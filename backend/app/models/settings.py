"""SQLAlchemy model for the `settings` table."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, ForeignKey, JSON, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class UserSettings(Base):
    """Per-owner preferences and notification settings.

    Named UserSettings (not Settings) to avoid conflict with pydantic Settings.
    """

    __tablename__ = "settings"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid, primary_key=True, default=uuid.uuid4
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey("owners.id", ondelete="CASCADE"),
        unique=True,
        nullable=False,
    )
    preferences: Mapped[dict | None] = mapped_column(JSON, nullable=True, default=dict)
    location_enabled: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=True
    )
    notifications_enabled: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=True
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    # Relationships
    owner: Mapped["Owner"] = relationship(  # noqa: F821
        "Owner", back_populates="settings"
    )
