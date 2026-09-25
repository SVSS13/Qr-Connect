"""Pydantic schemas for event endpoints."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class EventResponse(BaseModel):
    """Event data returned in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    session_id: uuid.UUID
    action_id: uuid.UUID | None = None
    type: str
    content: str | None = None
    latitude: float | None = None
    longitude: float | None = None
    address: str | None = None
    created_at: datetime


class EventListResponse(BaseModel):
    """Paginated list of events."""

    events: list[EventResponse]
    total: int
