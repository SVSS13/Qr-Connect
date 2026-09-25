"""Public scanner API endpoints — no authentication required.

These endpoints serve the scanner UI and handle visitor actions
within 5-minute ephemeral sessions.
"""

import uuid
from pathlib import Path
from typing import Optional

import aiofiles
from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.database import get_db
from app.models.event import Event
from app.models.qr_action import QrAction
from app.models.qr_card import QrCard
from app.schemas.public import (
    ActionResultResponse,
    PublicActionInfo,
    PublicCardResponse,
    ScanResponse,
    SendMessageRequest,
    SendLocationRequest,
    SessionInfo,
    TriggerActionRequest,
)
from app.services.session_service import SessionService


router = APIRouter()

templates = Jinja2Templates(directory="app/templates")


# ---------------------------------------------------------------------------
# Helper: validate session token from request body
# ---------------------------------------------------------------------------

async def _validate_session_token(
    session_token: str,
    db: AsyncSession,
) -> "ScanSession":  # noqa: F821
    """Validate session token and return session, or raise 403."""
    svc = SessionService(db)
    session = await svc.validate_session(session_token)
    if session is None:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Session expired or invalid. Please scan the QR code again.",
        )
    return session


# ---------------------------------------------------------------------------
# GET /c/{token} and /q/{token} — Scanner page (HTML)
# ---------------------------------------------------------------------------

@router.get("/c/{token}", response_class=HTMLResponse, include_in_schema=False)
@router.get("/q/{token}", response_class=HTMLResponse, include_in_schema=False)
async def scanner_page(
    token: str,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """Serve the scanner HTML page for a QR card.

    1. Validates the card token
    2. Creates a 5-minute session
    3. Renders the scanner UI with card info and actions
    """
    # Find the card with its enabled actions
    result = await db.execute(
        select(QrCard)
        .where(QrCard.card_token == token, QrCard.status == "active")
        .options(selectinload(QrCard.actions))
    )
    card = result.scalar_one_or_none()

    if card is None:
        return templates.TemplateResponse(
            request=request,
            name="scanner_error.html",
            context={"error": "QR code not found or inactive."},
            status_code=404,
        )

    # Create 5-minute session
    svc = SessionService(db)
    session = await svc.create_session(
        card=card,
        ip_address=request.client.host if request.client else None,
        user_agent=request.headers.get("user-agent"),
    )

    # Get enabled actions sorted by sort_order
    enabled_actions = sorted(
        [a for a in card.actions if a.enabled],
        key=lambda a: a.sort_order,
    )

    return templates.TemplateResponse(
        request=request,
        name="scanner.html",
        context={
            "card": card,
            "actions": enabled_actions,
            "session_token": session.session_token,
            "expires_in": svc.remaining_seconds(session),
        },
    )


# ---------------------------------------------------------------------------
# GET /api/public/card/{token} — Card info (JSON)
# ---------------------------------------------------------------------------

@router.get("/api/public/card/{token}", response_model=ScanResponse)
@router.get("/api/public/q/{token}", response_model=ScanResponse)
async def get_card_info(
    token: str,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> ScanResponse:
    """Get card info and create a session (JSON API for programmatic access)."""
    result = await db.execute(
        select(QrCard)
        .where(QrCard.card_token == token, QrCard.status == "active")
        .options(selectinload(QrCard.actions))
    )
    card = result.scalar_one_or_none()

    if card is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Card not found or inactive",
        )

    svc = SessionService(db)
    session = await svc.create_session(
        card=card,
        ip_address=request.client.host if request.client else None,
        user_agent=request.headers.get("user-agent"),
    )

    enabled_actions = sorted(
        [a for a in card.actions if a.enabled],
        key=lambda a: a.sort_order,
    )

    return ScanResponse(
        card=PublicCardResponse(
            name=card.name,
            type=card.type,
            actions=[PublicActionInfo.model_validate(a) for a in enabled_actions],
        ),
        session=SessionInfo(
            session_token=session.session_token,
            expires_in_seconds=svc.remaining_seconds(session),
        ),
    )


from app.core.limiter import limiter
from app.models.owner import Owner
from app.services.notification_service import notification_service

# ---------------------------------------------------------------------------
# POST /api/public/action — Trigger an alert
# ---------------------------------------------------------------------------

@router.post("/api/public/action", response_model=ActionResultResponse)
@limiter.limit("30/minute")
async def trigger_action(
    request: Request,
    payload: TriggerActionRequest,
    db: AsyncSession = Depends(get_db),
) -> ActionResultResponse:

    """Trigger an alert action within an active session."""
    session = await _validate_session_token(payload.session_token, db)

    # Determine alert text
    alert_text = "Someone scanned your QR code!"
    if payload.action_id:
        result = await db.execute(
            select(QrAction).where(QrAction.id == payload.action_id)
        )
        action = result.scalar_one_or_none()
        if action and action.config and "default_text" in action.config:
            alert_text = action.config["default_text"]

    # Store event
    event = Event(
        session_id=session.id,
        action_id=payload.action_id,
        type="alert",
        content=alert_text,
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)

    # Fetch card and owner to send FCM push notification
    card_result = await db.execute(
        select(QrCard).options(selectinload(QrCard.owner)).where(QrCard.id == session.qr_card_id)
    )
    card = card_result.scalar_one_or_none()
    if card and card.owner and card.owner.fcm_token:
        await notification_service.notify_owner_event(
            fcm_token=card.owner.fcm_token,
            card_name=card.name,
            event_type="alert",
            content=alert_text,
            event_id=str(event.id),
            card_id=str(card.id),
        )

    return ActionResultResponse(
        success=True,
        message="Alert sent! The owner has been notified.",
    )


# ---------------------------------------------------------------------------
# POST /api/public/message — Send a text message
# ---------------------------------------------------------------------------

@router.post("/api/public/message", response_model=ActionResultResponse)
@limiter.limit("20/minute")
async def send_message(
    request: Request,
    payload: SendMessageRequest,
    db: AsyncSession = Depends(get_db),
) -> ActionResultResponse:

    """Send a text message within an active session."""
    session = await _validate_session_token(payload.session_token, db)

    # Validate content
    content = payload.content.strip()
    if not content:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Message content cannot be empty",
        )

    # Check max length from action config if available
    max_length = 500  # default
    if payload.action_id:
        result = await db.execute(
            select(QrAction).where(QrAction.id == payload.action_id)
        )
        action = result.scalar_one_or_none()
        if action and action.config and "max_length" in action.config:
            max_length = action.config["max_length"]

    if len(content) > max_length:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Message too long. Maximum {max_length} characters.",
        )

    # Store event
    event = Event(
        session_id=session.id,
        action_id=payload.action_id,
        type="message",
        content=content,
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)

    # Fetch card and owner to send FCM push notification
    card_result = await db.execute(
        select(QrCard).options(selectinload(QrCard.owner)).where(QrCard.id == session.qr_card_id)
    )
    card = card_result.scalar_one_or_none()
    if card and card.owner and card.owner.fcm_token:
        await notification_service.notify_owner_event(
            fcm_token=card.owner.fcm_token,
            card_name=card.name,
            event_type="message",
            content=content,
            event_id=str(event.id),
            card_id=str(card.id),
        )

    return ActionResultResponse(
        success=True,
        message="Message sent! The owner has been notified.",
    )


# ---------------------------------------------------------------------------
# POST /api/public/voice — Upload a voice recording
# ---------------------------------------------------------------------------

from app.services.voice_service import VoiceService

@router.post("/api/public/voice", response_model=ActionResultResponse)
@limiter.limit("10/minute")
async def upload_voice(
    request: Request,
    session_token: str = Form(...),
    action_id: Optional[uuid.UUID] = Form(None),
    duration: Optional[int] = Form(None),
    audio_file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
) -> ActionResultResponse:
    """Upload a voice recording within an active session."""
    session = await _validate_session_token(session_token, db)


    # Store event
    event = Event(
        session_id=session.id,
        action_id=action_id,
        type="voice",
        content="Voice message recorded",
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)

    # Persist audio file and create MessagesVoice record
    voice_service = VoiceService(db)
    voice_record, storage_url = await voice_service.save_voice_message(
        event=event,
        file=audio_file,
        duration=duration,
    )
    event.content = storage_url
    await db.commit()

    # Notify owner via FCM
    card_result = await db.execute(
        select(QrCard).options(selectinload(QrCard.owner)).where(QrCard.id == session.qr_card_id)
    )
    card = card_result.scalar_one_or_none()
    if card and card.owner and card.owner.fcm_token:
        await notification_service.notify_owner_event(
            fcm_token=card.owner.fcm_token,
            card_name=card.name,
            event_type="voice",
            content=f"Voice message received ({duration or 0}s)",
            event_id=str(event.id),
            card_id=str(card.id),
        )

    return ActionResultResponse(
        success=True,
        message="Voice message sent! The owner has been notified.",
    )


# ---------------------------------------------------------------------------
# POST /api/public/location — Share visitor geolocation
# ---------------------------------------------------------------------------

from app.services.location_service import location_service

@router.post("/api/public/location", response_model=ActionResultResponse)
@limiter.limit("15/minute")
async def share_location(
    request: Request,
    payload: SendLocationRequest,
    db: AsyncSession = Depends(get_db),
) -> ActionResultResponse:
    """Share visitor coordinates and reverse-geocode to street address."""
    session = await _validate_session_token(payload.session_token, db)


    # Validate coordinate bounds
    if not (-90.0 <= payload.latitude <= 90.0) or not (-180.0 <= payload.longitude <= 180.0):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Invalid coordinates: latitude must be in [-90, 90] and longitude in [-180, 180].",
        )

    # Reverse geocode coordinates to street address
    address = await location_service.reverse_geocode(payload.latitude, payload.longitude)

    # Store event
    event = Event(
        session_id=session.id,
        action_id=payload.action_id,
        type="location",
        content=f"Location shared: {address}",
        latitude=payload.latitude,
        longitude=payload.longitude,
        address=address,
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)

    # Notify owner via FCM
    card_result = await db.execute(
        select(QrCard).options(selectinload(QrCard.owner)).where(QrCard.id == session.qr_card_id)
    )
    card = card_result.scalar_one_or_none()
    if card and card.owner and card.owner.fcm_token:
        await notification_service.notify_owner_event(
            fcm_token=card.owner.fcm_token,
            card_name=card.name,
            event_type="location",
            content=f"📍 Location: {address}",
            event_id=str(event.id),
            card_id=str(card.id),
        )

    return ActionResultResponse(
        success=True,
        message=f"Location shared successfully! ({address})",
    )


# ---------------------------------------------------------------------------
# POST /api/public/photo — Upload a photo attachment
# ---------------------------------------------------------------------------

@router.post("/api/public/photo", response_model=ActionResultResponse)
@limiter.limit("15/minute")
async def upload_photo(
    request: Request,
    session_token: str = Form(...),
    action_id: Optional[uuid.UUID] = Form(None),
    caption: Optional[str] = Form(None),
    photo_file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
) -> ActionResultResponse:
    """Upload a photo note within an active session."""
    session = await _validate_session_token(session_token, db)

    content_type = photo_file.content_type or "image/jpeg"
    ext_map = {
        "image/jpeg": ".jpg",
        "image/jpg": ".jpg",
        "image/png": ".png",
        "image/webp": ".webp",
        "image/gif": ".gif",
        "image/heic": ".heic",
    }
    ext = ext_map.get(content_type.lower(), ".jpg")
    filename = f"{uuid.uuid4()}{ext}"
    dest_path = Path("uploads/photos") / filename

    content_bytes = await photo_file.read()
    if len(content_bytes) > 20 * 1024 * 1024:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Photo file too large (maximum 20MB allowed)",
        )

    async with aiofiles.open(dest_path, "wb") as f:
        await f.write(content_bytes)

    storage_url = f"/uploads/photos/{filename}"
    cleaned_caption = caption.strip() if caption else None
    event_content = f"{storage_url}:::{cleaned_caption}" if cleaned_caption else storage_url

    event = Event(
        session_id=session.id,
        action_id=action_id,
        type="photo",
        content=event_content,
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)

    # Notify owner via FCM
    card_result = await db.execute(
        select(QrCard).options(selectinload(QrCard.owner)).where(QrCard.id == session.qr_card_id)
    )
    card = card_result.scalar_one_or_none()
    if card and card.owner and card.owner.fcm_token:
        preview_text = f"📸 Photo received: {cleaned_caption}" if cleaned_caption else "📸 Photo received"
        await notification_service.notify_owner_event(
            fcm_token=card.owner.fcm_token,
            card_name=card.name,
            event_type="photo",
            content=preview_text,
            event_id=str(event.id),
            card_id=str(card.id),
        )

    return ActionResultResponse(
        success=True,
        message="Photo sent! The owner has been notified.",
    )


# ---------------------------------------------------------------------------
# POST /api/public/video — Upload a video recording
# ---------------------------------------------------------------------------

@router.post("/api/public/video", response_model=ActionResultResponse)
@limiter.limit("10/minute")
async def upload_video(
    request: Request,
    session_token: str = Form(...),
    action_id: Optional[uuid.UUID] = Form(None),
    caption: Optional[str] = Form(None),
    video_file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
) -> ActionResultResponse:
    """Upload a video recording within an active session."""
    session = await _validate_session_token(session_token, db)

    content_type = video_file.content_type or "video/mp4"
    ext_map = {
        "video/mp4": ".mp4",
        "video/webm": ".webm",
        "video/quicktime": ".mov",
        "video/3gpp": ".3gp",
        "video/x-msvideo": ".avi",
    }
    ext = ext_map.get(content_type.lower(), ".mp4")
    filename = f"{uuid.uuid4()}{ext}"
    dest_path = Path("uploads/videos") / filename

    content_bytes = await video_file.read()
    if len(content_bytes) > 50 * 1024 * 1024:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Video file too large (maximum 50MB allowed)",
        )

    async with aiofiles.open(dest_path, "wb") as f:
        await f.write(content_bytes)

    storage_url = f"/uploads/videos/{filename}"
    cleaned_caption = caption.strip() if caption else None
    event_content = f"{storage_url}:::{cleaned_caption}" if cleaned_caption else storage_url

    event = Event(
        session_id=session.id,
        action_id=action_id,
        type="video",
        content=event_content,
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)

    # Notify owner via FCM
    card_result = await db.execute(
        select(QrCard).options(selectinload(QrCard.owner)).where(QrCard.id == session.qr_card_id)
    )
    card = card_result.scalar_one_or_none()
    if card and card.owner and card.owner.fcm_token:
        preview_text = f"🎥 Video received: {cleaned_caption}" if cleaned_caption else "🎥 Video received"
        await notification_service.notify_owner_event(
            fcm_token=card.owner.fcm_token,
            card_name=card.name,
            event_type="video",
            content=preview_text,
            event_id=str(event.id),
            card_id=str(card.id),
        )

    return ActionResultResponse(
        success=True,
        message="Video sent! The owner has been notified.",
    )



