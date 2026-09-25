"""Pydantic schemas for QR card endpoints."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class CardCreate(BaseModel):
    """Payload for creating a new QR card."""

    name: str
    type: str = "other"


class CardUpdate(BaseModel):
    """Payload for updating a QR card. All fields optional."""

    name: str | None = None
    type: str | None = None
    status: str | None = None


class CardResponse(BaseModel):
    """QR card data returned in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    owner_id: uuid.UUID
    card_token: str
    name: str
    type: str
    status: str
    created_at: datetime
    rotated_at: datetime | None = None


class CardListResponse(BaseModel):
    """Paginated list of QR cards."""

    cards: list[CardResponse]
    total: int
