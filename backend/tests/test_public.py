"""Tests for public scanner API endpoints and session management."""

import pytest
import pytest_asyncio
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import generate_card_token, hash_password
from app.models.owner import Owner
from app.models.qr_card import QrCard
from app.models.qr_action import QrAction
from app.models.settings import UserSettings
import uuid


@pytest_asyncio.fixture
async def scanner_card(db_session: AsyncSession) -> QrCard:
    """Create an owner with a card and actions for scanner tests."""
    owner = Owner(
        id=uuid.uuid4(),
        name="Card Owner",
        email="owner@example.com",
        password_hash=hash_password("testpass123"),
    )
    db_session.add(owner)
    db_session.add(UserSettings(user_id=owner.id))
    await db_session.flush()

    card = QrCard(
        id=uuid.uuid4(),
        owner_id=owner.id,
        card_token="test-card-token-123",
        name="Test Car",
        type="car",
        status="active",
    )
    db_session.add(card)
    await db_session.flush()

    # Add actions
    actions = [
        QrAction(
            qr_card_id=card.id,
            label="Send Alert",
            action_type="alert",
            icon="alert-circle",
            sort_order=0,
            config={"default_text": "Someone is at your car!"},
            enabled=True,
        ),
        QrAction(
            qr_card_id=card.id,
            label="Send Message",
            action_type="message",
            icon="message-square",
            sort_order=1,
            config={"max_length": 500},
            enabled=True,
        ),
        QrAction(
            qr_card_id=card.id,
            label="Disabled Action",
            action_type="voice",
            icon="mic",
            sort_order=2,
            enabled=False,
        ),
    ]
    for a in actions:
        db_session.add(a)

    await db_session.commit()
    await db_session.refresh(card)
    return card


@pytest_asyncio.fixture
async def session_token(client: AsyncClient, scanner_card: QrCard) -> str:
    """Get a valid session token by hitting the card info endpoint."""
    resp = await client.get(f"/api/public/card/{scanner_card.card_token}")
    assert resp.status_code == 200
    return resp.json()["session"]["session_token"]


# ---------------------------------------------------------------------------
# Scanner Page
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestScannerPage:
    """Tests for GET /c/{token}."""

    async def test_scanner_page_valid_token(
        self, client: AsyncClient, scanner_card: QrCard
    ):
        resp = await client.get(f"/c/{scanner_card.card_token}")
        assert resp.status_code == 200
        assert "text/html" in resp.headers["content-type"]
        assert "Test Car" in resp.text
        assert "Send Alert" in resp.text
        assert "Send Message" in resp.text
        # Disabled action should not appear
        assert "Disabled Action" not in resp.text

    async def test_scanner_page_invalid_token(self, client: AsyncClient):
        resp = await client.get("/c/nonexistent-token")
        assert resp.status_code == 404
        assert "QR code not found" in resp.text


# ---------------------------------------------------------------------------
# Card Info (JSON)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestCardInfo:
    """Tests for GET /api/public/card/{token}."""

    async def test_card_info_returns_card_and_session(
        self, client: AsyncClient, scanner_card: QrCard
    ):
        resp = await client.get(f"/api/public/card/{scanner_card.card_token}")
        assert resp.status_code == 200
        data = resp.json()

        # Card info
        assert data["card"]["name"] == "Test Car"
        assert data["card"]["type"] == "car"

        # Only enabled actions
        action_types = [a["action_type"] for a in data["card"]["actions"]]
        assert "alert" in action_types
        assert "message" in action_types
        assert "voice" not in action_types  # disabled

        # Session
        assert "session_token" in data["session"]
        assert data["session"]["expires_in_seconds"] > 0
        assert data["session"]["expires_in_seconds"] <= 300

    async def test_card_info_not_found(self, client: AsyncClient):
        resp = await client.get("/api/public/card/nonexistent")
        assert resp.status_code == 404


# ---------------------------------------------------------------------------
# Trigger Alert
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestTriggerAlert:
    """Tests for POST /api/public/action."""

    async def test_trigger_alert_success(
        self, client: AsyncClient, session_token: str
    ):
        resp = await client.post(
            "/api/public/action",
            json={"session_token": session_token},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["success"] is True
        assert "notified" in data["message"].lower()

    async def test_trigger_alert_invalid_session(self, client: AsyncClient):
        resp = await client.post(
            "/api/public/action",
            json={"session_token": "invalid-session-token"},
        )
        assert resp.status_code == 403
        assert "expired" in resp.json()["detail"].lower() or "invalid" in resp.json()["detail"].lower()


# ---------------------------------------------------------------------------
# Send Message
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestSendMessage:
    """Tests for POST /api/public/message."""

    async def test_send_message_success(
        self, client: AsyncClient, session_token: str
    ):
        resp = await client.post(
            "/api/public/message",
            json={
                "session_token": session_token,
                "content": "Hey, your car is blocking me!",
            },
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["success"] is True
        assert "notified" in data["message"].lower()

    async def test_send_empty_message_fails(
        self, client: AsyncClient, session_token: str
    ):
        resp = await client.post(
            "/api/public/message",
            json={
                "session_token": session_token,
                "content": "   ",
            },
        )
        assert resp.status_code == 422

    async def test_send_message_invalid_session(self, client: AsyncClient):
        resp = await client.post(
            "/api/public/message",
            json={
                "session_token": "invalid-token",
                "content": "Hello",
            },
        )
        assert resp.status_code == 403

    async def test_multiple_messages_in_session(
        self, client: AsyncClient, session_token: str
    ):
        """Multiple messages should be allowed within the same session."""
        for i in range(3):
            resp = await client.post(
                "/api/public/message",
                json={
                    "session_token": session_token,
                    "content": f"Message {i + 1}",
                },
            )
            assert resp.status_code == 200


# ---------------------------------------------------------------------------
# Session Expiry
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestSessionExpiry:
    """Tests for session expiry behavior."""

    async def test_session_has_correct_expiry(
        self, client: AsyncClient, scanner_card: QrCard
    ):
        resp = await client.get(f"/api/public/card/{scanner_card.card_token}")
        session_info = resp.json()["session"]
        # Should be close to 300 seconds (5 minutes)
        assert 295 <= session_info["expires_in_seconds"] <= 300

    async def test_each_scan_creates_new_session(
        self, client: AsyncClient, scanner_card: QrCard
    ):
        """Each scan should create a distinct session token."""
        resp1 = await client.get(f"/api/public/card/{scanner_card.card_token}")
        resp2 = await client.get(f"/api/public/card/{scanner_card.card_token}")

        token1 = resp1.json()["session"]["session_token"]
        token2 = resp2.json()["session"]["session_token"]
        assert token1 != token2  # Each scan = new session


# ---------------------------------------------------------------------------
# Vanity Route & Media Uploads (Photo & Video)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestVanityAndMedia:
    """Tests for vanity URL masking and media endpoints."""

    async def test_vanity_scanner_page(
        self, client: AsyncClient, scanner_card: QrCard
    ):
        """GET /q/{token} should serve the scanner page identically to /c/{token}."""
        resp = await client.get(f"/q/{scanner_card.card_token}")
        assert resp.status_code == 200
        assert "QR Connect" in resp.text
        assert scanner_card.name in resp.text

    async def test_upload_photo_success(
        self, client: AsyncClient, session_token: str
    ):
        """POST /api/public/photo uploads a photo and creates an event."""
        files = {
            "photo_file": ("test_photo.jpg", b"fake-jpeg-image-bytes", "image/jpeg"),
        }
        data = {
            "session_token": session_token,
            "caption": "Package left at door",
        }
        resp = await client.post("/api/public/photo", data=data, files=files)
        assert resp.status_code == 200
        result = resp.json()
        assert result["success"] is True

    async def test_upload_video_success(
        self, client: AsyncClient, session_token: str
    ):
        """POST /api/public/video uploads a video and creates an event."""
        files = {
            "video_file": ("test_video.mp4", b"fake-mp4-video-bytes", "video/mp4"),
        }
        data = {
            "session_token": session_token,
            "caption": "Short video note",
        }
        resp = await client.post("/api/public/video", data=data, files=files)
        assert resp.status_code == 200
        result = resp.json()
        assert result["success"] is True

