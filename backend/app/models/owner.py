"""SQLAlchemy model for the `owners` table."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class Owner(Base):
    """QR card owner / user account."""

    __tablename__ = "owners"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid, primary_key=True, default=uuid.uuid4
    )
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    email: Mapped[str] = mapped_column(
        String(255), unique=True, nullable=False, index=True
    )
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    fcm_token: Mapped[str | None] = mapped_column(String(512), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    # Relationships
    cards: Mapped[list["QrCard"]] = relationship(  # noqa: F821
        "QrCard", back_populates="owner", cascade="all, delete-orphan"
    )
    settings: Mapped["UserSettings | None"] = relationship(  # noqa: F821
        "UserSettings",
        back_populates="owner",
        uselist=False,
        cascade="all, delete-orphan",
    )
