"""Owner Events API endpoints and FCM token registration."""

import uuid
from typing import Optional

from fastapi import APIRouter, Depends, Query, status
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import get_current_user
from app.core.database import get_db
from app.models.event import Event
from app.models.owner import Owner
from app.models.qr_card import QrCard
from app.models.session import ScanSession
from app.schemas.event import EventResponse

router = APIRouter()


class FcmTokenRequest(BaseModel):
    """Payload to register an FCM device token."""

    fcm_token: str


@router.get("/events", response_model=list[EventResponse])
async def list_events(
    card_id: Optional[uuid.UUID] = None,
    event_type: Optional[str] = Query(None, alias="type"),
    limit: int = 50,
    offset: int = 0,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[EventResponse]:
    """List activity events across all QR cards belonging to the owner."""
    query = (
        select(Event, QrCard.id.label("qr_card_id"), QrCard.name.label("qr_card_name"))
        .options(selectinload(Event.voice))
        .join(ScanSession, Event.session_id == ScanSession.id)
        .join(QrCard, ScanSession.qr_card_id == QrCard.id)
        .where(QrCard.owner_id == current_user.id)
        .order_by(Event.created_at.desc())
        .limit(limit)
        .offset(offset)
    )

    if card_id:
        query = query.where(QrCard.id == card_id)
    if event_type:
        query = query.where(Event.type == event_type)

    result = await db.execute(query)
    rows = result.all()
    response = []
    for event_obj, qcard_id, qcard_name in rows:
        item = EventResponse.model_validate(event_obj)
        item.card_id = qcard_id
        item.card_name = qcard_name
        if event_obj.type == "voice" and event_obj.voice and event_obj.voice.content:
            item.content = event_obj.voice.content
        response.append(item)
    return response


@router.post("/fcm-token", status_code=status.HTTP_200_OK)
async def register_fcm_token(
    payload: FcmTokenRequest,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    """Register or update the FCM push notification token for the owner's Android device."""
    current_user.fcm_token = payload.fcm_token
    await db.commit()
    await db.refresh(current_user)
    return {"success": True, "message": "FCM token updated successfully"}
