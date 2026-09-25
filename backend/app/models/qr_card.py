"""SQLAlchemy model for the `qr_cards` table."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, ForeignKey, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class QrCard(Base):
    """A permanent QR card owned by an owner.

    The card_token is immutable and printed on the physical QR code.
    """

    __tablename__ = "qr_cards"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid, primary_key=True, default=uuid.uuid4
    )
    owner_id: Mapped[uuid.UUID] = mapped_column(
        Uuid, ForeignKey("owners.id", ondelete="CASCADE"), nullable=False, index=True
    )
    card_token: Mapped[str] = mapped_column(
        String(64), unique=True, nullable=False, index=True
    )
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    type: Mapped[str] = mapped_column(String(50), nullable=False, default="other")
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="active")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    rotated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    # Relationships
    owner: Mapped["Owner"] = relationship(  # noqa: F821
        "Owner", back_populates="cards"
    )
    actions: Mapped[list["QrAction"]] = relationship(  # noqa: F821
        "QrAction", back_populates="card", cascade="all, delete-orphan"
    )
    sessions: Mapped[list["ScanSession"]] = relationship(  # noqa: F821
        "ScanSession", back_populates="card", cascade="all, delete-orphan"
    )
