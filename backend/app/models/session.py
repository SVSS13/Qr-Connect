"""SQLAlchemy model for the `sessions` table."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Numeric, String, DateTime, ForeignKey, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class ScanSession(Base):
    """A 5-minute ephemeral scanner session created when a visitor scans a QR code.

    Named ScanSession (not Session) to avoid conflict with SQLAlchemy's Session.
    """

    __tablename__ = "sessions"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid, primary_key=True, default=uuid.uuid4
    )
    qr_card_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey("qr_cards.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    session_token: Mapped[str] = mapped_column(
        String(128), unique=True, nullable=False, index=True
    )
    ip_address: Mapped[str | None] = mapped_column(String(45), nullable=True)
    user_agent: Mapped[str | None] = mapped_column(String(512), nullable=True)
    latitude: Mapped[float | None] = mapped_column(
        Numeric(precision=10, scale=7), nullable=True
    )
    longitude: Mapped[float | None] = mapped_column(
        Numeric(precision=10, scale=7), nullable=True
    )
    location_status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="pending"
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    expires_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False
    )

    # Relationships
    card: Mapped["QrCard"] = relationship(  # noqa: F821
        "QrCard", back_populates="sessions"
    )
    events: Mapped[list["Event"]] = relationship(  # noqa: F821
        "Event", back_populates="session", cascade="all, delete-orphan"
    )
