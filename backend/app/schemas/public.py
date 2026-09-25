"""Pydantic schemas for public scanner API endpoints."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


# ---------------------------------------------------------------------------
# Responses
# ---------------------------------------------------------------------------

class PublicActionInfo(BaseModel):
    """Action info returned to the scanner UI."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    label: str
    action_type: str
    icon: str | None = None
    sort_order: int
    config: dict | None = None


class PublicCardResponse(BaseModel):
    """Card info returned to the scanner UI (no sensitive data)."""

    name: str
    type: str
    actions: list[PublicActionInfo]


class SessionInfo(BaseModel):
    """Session info returned after scanning."""

    session_token: str
    expires_in_seconds: int


class ScanResponse(BaseModel):
    """Full response for a QR scan (used by JSON endpoint)."""

    card: PublicCardResponse
    session: SessionInfo


# ---------------------------------------------------------------------------
# Requests
# ---------------------------------------------------------------------------

class TriggerActionRequest(BaseModel):
    """Request to trigger an alert action."""

    session_token: str
    action_id: uuid.UUID | None = None


class SendMessageRequest(BaseModel):
    """Request to send a text message."""

    session_token: str
    action_id: uuid.UUID | None = None
    content: str


class SendLocationRequest(BaseModel):
    """Request to share visitor geolocation."""

    session_token: str
    action_id: uuid.UUID | None = None
    latitude: float
    longitude: float
    accuracy: float | None = None



# ---------------------------------------------------------------------------
# Generic response
# ---------------------------------------------------------------------------

class ActionResultResponse(BaseModel):
    """Generic response for public action endpoints."""

    success: bool
    message: str
