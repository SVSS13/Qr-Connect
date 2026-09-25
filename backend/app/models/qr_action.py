"""SQLAlchemy model for the `qr_actions` table."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, Integer, String, DateTime, ForeignKey, JSON, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class QrAction(Base):
    """A dynamic action configured on a QR card.

    Actions are stored as database rows so the scanner UI is
    configuration-driven and changes never require redeployment.
    """

    __tablename__ = "qr_actions"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid, primary_key=True, default=uuid.uuid4
    )
    qr_card_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey("qr_cards.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    label: Mapped[str] = mapped_column(String(255), nullable=False)
    action_type: Mapped[str] = mapped_column(
        String(50), nullable=False
    )  # alert, message, voice, location
    icon: Mapped[str | None] = mapped_column(String(100), nullable=True)
    sort_order: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    config: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    # Relationships
    card: Mapped["QrCard"] = relationship(  # noqa: F821
        "QrCard", back_populates="actions"
    )
