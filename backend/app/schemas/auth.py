"""Pydantic schemas for authentication endpoints."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr


class RegisterRequest(BaseModel):
    """Owner registration payload."""

    name: str
    email: EmailStr
    password: str  # min 8 chars validated at API layer


class LoginRequest(BaseModel):
    """Owner login payload."""

    email: EmailStr
    password: str


class RefreshRequest(BaseModel):
    """Token refresh payload."""

    refresh_token: str


class OwnerResponse(BaseModel):
    """Owner data returned in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    email: str
    created_at: datetime


class TokenResponse(BaseModel):
    """JWT token pair response."""

    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class AuthResponse(BaseModel):
    """Full authentication response with tokens and user data."""

    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: OwnerResponse
