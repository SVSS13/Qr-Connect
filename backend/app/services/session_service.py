"""Session management service for 5-minute ephemeral scanner sessions."""

from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import generate_session_token
from app.models.qr_card import QrCard
from app.models.session import ScanSession


class SessionService:
    """Manages ephemeral 5-minute scanner sessions.

    Each QR scan creates a new session. Sessions expire after
    SESSION_EXPIRY_MINUTES (default 5). All visitor actions
    within the session window are recorded as events.
    """

    def __init__(self, db: AsyncSession):
        self.db = db

    async def create_session(
        self,
        card: QrCard,
        ip_address: str | None = None,
        user_agent: str | None = None,
    ) -> ScanSession:
        """Create a new 5-minute session for a scanned QR card."""
        now = datetime.now(timezone.utc)
        expires_at = now + timedelta(minutes=settings.SESSION_EXPIRY_MINUTES)

        session = ScanSession(
            qr_card_id=card.id,
            session_token=generate_session_token(),
            ip_address=ip_address,
            user_agent=user_agent,
            created_at=now,
            expires_at=expires_at,
        )
        self.db.add(session)
        await self.db.commit()
        await self.db.refresh(session)
        return session

    async def validate_session(self, session_token: str) -> ScanSession | None:
        """Validate a session token and check it hasn't expired.

        Returns the session if valid, None if invalid or expired.
        """
        result = await self.db.execute(
            select(ScanSession).where(ScanSession.session_token == session_token)
        )
        session = result.scalar_one_or_none()

        if session is None:
            return None

        if self.is_expired(session):
            return None

        return session

    @staticmethod
    def is_expired(session: ScanSession) -> bool:
        """Check if a session has expired."""
        return datetime.now(timezone.utc) > session.expires_at.replace(
            tzinfo=timezone.utc
        ) if session.expires_at.tzinfo is None else datetime.now(timezone.utc) > session.expires_at

    @staticmethod
    def remaining_seconds(session: ScanSession) -> int:
        """Calculate remaining seconds in a session."""
        now = datetime.now(timezone.utc)
        expires = session.expires_at.replace(tzinfo=timezone.utc) if session.expires_at.tzinfo is None else session.expires_at
        remaining = (expires - now).total_seconds()
        return max(0, int(remaining))
