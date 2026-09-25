"""Pydantic schemas for user settings endpoints."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class SettingsUpdate(BaseModel):
    """Payload for updating user settings. All fields optional."""

    preferences: dict | None = None
    location_enabled: bool | None = None
    notifications_enabled: bool | None = None


class SettingsResponse(BaseModel):
    """User settings data returned in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    user_id: uuid.UUID
    preferences: dict | None = None
    location_enabled: bool
    notifications_enabled: bool
    created_at: datetime
