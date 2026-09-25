"""User Settings API endpoints."""

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core.database import get_db
from app.models.owner import Owner
from app.models.settings import UserSettings
from app.schemas.settings import SettingsResponse, SettingsUpdate

router = APIRouter()


async def _get_or_create_settings(
    user_id,
    db: AsyncSession,
) -> UserSettings:
    """Fetch or create default settings for a user."""
    result = await db.execute(
        select(UserSettings).where(UserSettings.user_id == user_id)
    )
    user_settings = result.scalar_one_or_none()

    if user_settings is None:
        user_settings = UserSettings(user_id=user_id)
        db.add(user_settings)
        await db.commit()
        await db.refresh(user_settings)

    return user_settings


@router.get("/settings", response_model=SettingsResponse)
async def get_settings(
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> SettingsResponse:
    """Get the current user's settings."""
    user_settings = await _get_or_create_settings(current_user.id, db)
    return SettingsResponse.model_validate(user_settings)


@router.patch("/settings", response_model=SettingsResponse)
async def update_settings(
    payload: SettingsUpdate,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> SettingsResponse:
    """Update the current user's settings."""
    user_settings = await _get_or_create_settings(current_user.id, db)

    update_data = payload.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(user_settings, field, value)

    await db.commit()
    await db.refresh(user_settings)

    return SettingsResponse.model_validate(user_settings)
