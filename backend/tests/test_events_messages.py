"""Tests for Events, Messages, and FCM token registration endpoints."""

import uuid
import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.owner import Owner
from app.models.qr_card import QrCard
from app.models.qr_action import QrAction
from app.models.session import ScanSession
from app.models.event import Event


@pytest.mark.asyncio
class TestFcmTokenRegistration:
    """Tests for POST /api/owner/fcm-token."""

    async def test_register_fcm_token_success(
        self, client: AsyncClient, auth_headers: dict
    ):
        response = await client.post(
            "/api/owner/fcm-token",
            json={"fcm_token": "sample-firebase-device-token-12345"},
            headers=auth_headers,
        )
        assert response.status_code == 200
        assert response.json()["success"] is True

    async def test_register_fcm_token_unauthorized(self, client: AsyncClient):
        response = await client.post(
            "/api/owner/fcm-token",
            json={"fcm_token": "token-without-auth"},
        )
        assert response.status_code == 401


@pytest.mark.asyncio
class TestEventsAndMessages:
    """Tests for GET /api/owner/events and GET /api/owner/messages."""

    async def test_events_and_messages_flow(
        self, client: AsyncClient, auth_headers: dict, db_session: AsyncSession, test_owner: Owner
    ):
        # 1. Create a card
        card_resp = await client.post(
            "/api/owner/cards",
            json={"name": "Event Test Car", "type": "car"},
            headers=auth_headers,
        )
        card_id = card_resp.json()["id"]
        card_token = card_resp.json()["card_token"]

        # 2. Register FCM token
        await client.post(
            "/api/owner/fcm-token",
            json={"fcm_token": "test-fcm-device-token"},
            headers=auth_headers,
        )

        # 3. Scan QR code (creates 5-min session)
        scan_resp = await client.get(f"/api/public/card/{card_token}")
        assert scan_resp.status_code == 200
        session_token = scan_resp.json()["session"]["session_token"]

        # 4. Trigger alert action
        alert_resp = await client.post(
            "/api/public/action",
            json={"session_token": session_token},
        )
        assert alert_resp.status_code == 200

        # 5. Send visitor message
        msg_resp = await client.post(
            "/api/public/message",
            json={
                "session_token": session_token,
                "content": "Please move your car, thanks!",
            },
        )
        assert msg_resp.status_code == 200

        # 6. Verify owner events list
        events_resp = await client.get("/api/owner/events", headers=auth_headers)
        assert events_resp.status_code == 200
        events = events_resp.json()
        assert len(events) >= 2
        event_types = [e["type"] for e in events]
        assert "alert" in event_types
        assert "message" in event_types

        # 7. Verify filtering events by type
        alert_events_resp = await client.get(
            "/api/owner/events?type=alert", headers=auth_headers
        )
        assert alert_events_resp.status_code == 200
        alert_events = alert_events_resp.json()
        assert all(e["type"] == "alert" for e in alert_events)

        # 8. Verify messages inbox endpoint
        messages_resp = await client.get("/api/owner/messages", headers=auth_headers)
        assert messages_resp.status_code == 200
        messages = messages_resp.json()
        assert len(messages) >= 1
        assert any("Please move your car" in m["content"] for m in messages)
        assert messages[0]["card_name"] == "Event Test Car"
