"""Owner Messages API endpoints."""

import uuid
from typing import Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict
from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import get_current_user
from app.core.database import get_db
from app.models.event import Event
from app.models.owner import Owner
from app.models.qr_card import QrCard
from app.models.session import ScanSession

router = APIRouter()


class MessageItemResponse(BaseModel):
    """Message item formatted for the owner inbox."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    card_id: uuid.UUID
    card_name: str
    type: str  # 'message' or 'voice'
    content: Optional[str] = None
    voice_url: Optional[str] = None
    voice_duration: Optional[int] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    address: Optional[str] = None
    created_at: str


@router.get("/messages", response_model=list[MessageItemResponse])
async def list_messages(
    card_id: Optional[uuid.UUID] = None,
    limit: int = 50,
    offset: int = 0,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[MessageItemResponse]:
    """List text and voice messages received across owner's QR cards."""
    query = (
        select(Event, QrCard.id.label("c_id"), QrCard.name.label("c_name"))
        .join(ScanSession, Event.session_id == ScanSession.id)
        .join(QrCard, ScanSession.qr_card_id == QrCard.id)
        .options(selectinload(Event.voice))
        .where(
            QrCard.owner_id == current_user.id,
            or_(Event.type == "message", Event.type == "voice"),
        )
        .order_by(Event.created_at.desc())
        .limit(limit)
        .offset(offset)
    )

    if card_id:
        query = query.where(QrCard.id == card_id)

    result = await db.execute(query)
    rows = result.all()

    messages = []
    for event, c_id, c_name in rows:
        voice_url = event.voice.content if event.voice else None
        voice_duration = event.voice.duration if event.voice else None
        messages.append(
            MessageItemResponse(
                id=event.id,
                card_id=c_id,
                card_name=c_name,
                type=event.type,
                content=event.content,
                voice_url=voice_url,
                voice_duration=voice_duration,
                latitude=float(event.latitude) if event.latitude is not None else None,
                longitude=float(event.longitude) if event.longitude is not None else None,
                address=event.address,
                created_at=event.created_at.isoformat(),
            )
        )
    return messages
