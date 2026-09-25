"""Pydantic schemas for QR action endpoints."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class ActionCreate(BaseModel):
    """Payload for adding an action to a QR card."""

    label: str
    action_type: str  # alert, message, voice, location
    icon: str | None = None
    sort_order: int = 0
    config: dict | None = None


class ActionUpdate(BaseModel):
    """Payload for updating a QR action. All fields optional."""

    label: str | None = None
    action_type: str | None = None
    icon: str | None = None
    sort_order: int | None = None
    config: dict | None = None
    enabled: bool | None = None


class ActionResponse(BaseModel):
    """QR action data returned in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    qr_card_id: uuid.UUID
    label: str
    action_type: str
    icon: str | None = None
    sort_order: int
    config: dict | None = None
    enabled: bool
    created_at: datetime
