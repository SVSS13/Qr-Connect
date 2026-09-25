"""Tests for Geolocation sharing and reverse geocoding."""

import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.models.event import Event


@pytest.mark.asyncio
class TestLocationSharing:
    """Tests for POST /api/public/location."""

    async def test_share_location_success(
        self, client: AsyncClient, auth_headers: dict, db_session: AsyncSession
    ):
        # 1. Create a card
        card_resp = await client.post(
            "/api/owner/cards",
            json={"name": "Location Test Car", "type": "car"},
            headers=auth_headers,
        )
        card_token = card_resp.json()["card_token"]

        # 2. Start a 5-min session
        scan_resp = await client.get(f"/api/public/card/{card_token}")
        session_token = scan_resp.json()["session"]["session_token"]

        # 3. Share coordinates (San Francisco)
        payload = {
            "session_token": session_token,
            "latitude": 37.7749,
            "longitude": -122.4194,
            "accuracy": 15.0,
        }
        response = await client.post("/api/public/location", json=payload)
        assert response.status_code == 200
        assert response.json()["success"] is True
        assert "Location shared" in response.json()["message"]

        # 4. Verify Event record in database
        event_result = await db_session.execute(
            select(Event).where(Event.type == "location")
        )
        event = event_result.scalar_one_or_none()
        assert event is not None
        assert event.type == "location"
        assert abs(float(event.latitude) - 37.7749) < 0.001
        assert abs(float(event.longitude) - (-122.4194)) < 0.001
        assert event.address is not None

        # 5. Verify owner events endpoint retrieves the coordinates and address
        events_resp = await client.get("/api/owner/events?type=location", headers=auth_headers)
        assert events_resp.status_code == 200
        events = events_resp.json()
        assert len(events) >= 1
        loc_event = events[0]
        assert loc_event["type"] == "location"
        assert loc_event["latitude"] is not None
        assert loc_event["longitude"] is not None
        assert loc_event["address"] is not None

    async def test_share_location_invalid_session(self, client: AsyncClient):
        payload = {
            "session_token": "nonexistent-token",
            "latitude": 40.7128,
            "longitude": -74.0060,
        }
        response = await client.post("/api/public/location", json=payload)
        assert response.status_code == 403

    async def test_share_location_invalid_coordinates(
        self, client: AsyncClient, auth_headers: dict
    ):
        # 1. Create a card
        card_resp = await client.post(
            "/api/owner/cards",
            json={"name": "Coord Test", "type": "car"},
            headers=auth_headers,
        )
        card_token = card_resp.json()["card_token"]

        # 2. Start session
        scan_resp = await client.get(f"/api/public/card/{card_token}")
        session_token = scan_resp.json()["session"]["session_token"]

        # 3. Invalid latitude (> 90)
        payload = {
            "session_token": session_token,
            "latitude": 95.0,
            "longitude": -122.4194,
        }
        response = await client.post("/api/public/location", json=payload)
        assert response.status_code == 422
