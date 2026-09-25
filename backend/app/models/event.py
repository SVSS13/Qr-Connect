"""SQLAlchemy model for the `events` table."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Numeric, String, Text, DateTime, ForeignKey, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class Event(Base):
    """An event triggered by a visitor during a scanner session.

    Events include alerts, messages, voice recordings, and location shares.
    """

    __tablename__ = "events"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid, primary_key=True, default=uuid.uuid4
    )
    session_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey("sessions.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    action_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid, ForeignKey("qr_actions.id", ondelete="SET NULL"), nullable=True
    )
    type: Mapped[str] = mapped_column(
        String(50), nullable=False
    )  # alert, message, voice, location
    content: Mapped[str | None] = mapped_column(Text, nullable=True)
    latitude: Mapped[float | None] = mapped_column(
        Numeric(precision=10, scale=7), nullable=True
    )
    longitude: Mapped[float | None] = mapped_column(
        Numeric(precision=10, scale=7), nullable=True
    )
    address: Mapped[str | None] = mapped_column(String(500), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    # Relationships
    session: Mapped["ScanSession"] = relationship(  # noqa: F821
        "ScanSession", back_populates="events"
    )
    voice: Mapped["MessagesVoice | None"] = relationship(  # noqa: F821
        "MessagesVoice",
        back_populates="event",
        uselist=False,
        cascade="all, delete-orphan",
    )
