"""SQLAlchemy model for the `messages_voice` table."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Integer, String, DateTime, ForeignKey, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class MessagesVoice(Base):
    """Metadata for voice message recordings attached to events."""

    __tablename__ = "messages_voice"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid, primary_key=True, default=uuid.uuid4
    )
    event_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey("events.id", ondelete="CASCADE"),
        unique=True,
        nullable=False,
    )
    content: Mapped[str | None] = mapped_column(
        String(1024), nullable=True
    )  # Storage path or URL
    language: Mapped[str | None] = mapped_column(String(10), nullable=True)
    mime_type: Mapped[str | None] = mapped_column(String(50), nullable=True)
    duration: Mapped[int | None] = mapped_column(
        Integer, nullable=True
    )  # Duration in seconds
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    # Relationships
    event: Mapped["Event"] = relationship(  # noqa: F821
        "Event", back_populates="voice"
    )
