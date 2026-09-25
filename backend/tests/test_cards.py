"""Tests for QR card CRUD endpoints."""

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
class TestListCards:
    """Tests for GET /api/owner/cards."""

    async def test_list_cards_empty(self, client: AsyncClient, auth_headers: dict):
        response = await client.get("/api/owner/cards", headers=auth_headers)
        assert response.status_code == 200
        assert response.json() == []

    async def test_list_cards_after_create(
        self, client: AsyncClient, auth_headers: dict
    ):
        # Create a card
        await client.post(
            "/api/owner/cards",
            json={"name": "My Car", "type": "car"},
            headers=auth_headers,
        )
        response = await client.get("/api/owner/cards", headers=auth_headers)
        assert response.status_code == 200
        cards = response.json()
        assert len(cards) == 1
        assert cards[0]["name"] == "My Car"
        assert cards[0]["type"] == "car"

    async def test_list_cards_requires_auth(self, client: AsyncClient):
        response = await client.get("/api/owner/cards")
        assert response.status_code == 401


@pytest.mark.asyncio
class TestCreateCard:
    """Tests for POST /api/owner/cards."""

    async def test_create_card_success(
        self, client: AsyncClient, auth_headers: dict
    ):
        response = await client.post(
            "/api/owner/cards",
            json={"name": "My Home", "type": "home"},
            headers=auth_headers,
        )
        assert response.status_code == 201
        data = response.json()
        assert data["name"] == "My Home"
        assert data["type"] == "home"
        assert data["status"] == "active"
        assert "card_token" in data
        assert len(data["card_token"]) > 10  # URL-safe token

    async def test_create_card_default_type(
        self, client: AsyncClient, auth_headers: dict
    ):
        response = await client.post(
            "/api/owner/cards",
            json={"name": "My Thing"},
            headers=auth_headers,
        )
        assert response.status_code == 201
        assert response.json()["type"] == "other"

    async def test_create_card_creates_default_actions(
        self, client: AsyncClient, auth_headers: dict
    ):
        """A new card should have default actions (alert, message, voice, location)."""
        create_resp = await client.post(
            "/api/owner/cards",
            json={"name": "Test Card"},
            headers=auth_headers,
        )
        card_id = create_resp.json()["id"]

        actions_resp = await client.get(
            f"/api/owner/cards/{card_id}/actions",
            headers=auth_headers,
        )
        assert actions_resp.status_code == 200
        actions = actions_resp.json()
        assert len(actions) == 4
        action_types = {a["action_type"] for a in actions}
        assert action_types == {"alert", "message", "voice", "location"}


@pytest.mark.asyncio
class TestUpdateCard:
    """Tests for PATCH /api/owner/cards/{id}."""

    async def test_update_card_name(self, client: AsyncClient, auth_headers: dict):
        # Create
        create_resp = await client.post(
            "/api/owner/cards",
            json={"name": "Old Name"},
            headers=auth_headers,
        )
        card_id = create_resp.json()["id"]

        # Update
        response = await client.patch(
            f"/api/owner/cards/{card_id}",
            json={"name": "New Name"},
            headers=auth_headers,
        )
        assert response.status_code == 200
        assert response.json()["name"] == "New Name"

    async def test_update_card_status(self, client: AsyncClient, auth_headers: dict):
        create_resp = await client.post(
            "/api/owner/cards",
            json={"name": "Card"},
            headers=auth_headers,
        )
        card_id = create_resp.json()["id"]

        response = await client.patch(
            f"/api/owner/cards/{card_id}",
            json={"status": "inactive"},
            headers=auth_headers,
        )
        assert response.status_code == 200
        assert response.json()["status"] == "inactive"

    async def test_update_nonexistent_card(
        self, client: AsyncClient, auth_headers: dict
    ):
        response = await client.patch(
            "/api/owner/cards/00000000-0000-0000-0000-000000000000",
            json={"name": "Nope"},
            headers=auth_headers,
        )
        assert response.status_code == 404


@pytest.mark.asyncio
class TestDeleteCard:
    """Tests for DELETE /api/owner/cards/{id}."""

    async def test_delete_card(self, client: AsyncClient, auth_headers: dict):
        create_resp = await client.post(
            "/api/owner/cards",
            json={"name": "To Delete"},
            headers=auth_headers,
        )
        card_id = create_resp.json()["id"]

        response = await client.delete(
            f"/api/owner/cards/{card_id}",
            headers=auth_headers,
        )
        assert response.status_code == 204

        # Verify deleted
        list_resp = await client.get("/api/owner/cards", headers=auth_headers)
        assert len(list_resp.json()) == 0

    async def test_delete_nonexistent_card(
        self, client: AsyncClient, auth_headers: dict
    ):
        response = await client.delete(
            "/api/owner/cards/00000000-0000-0000-0000-000000000000",
            headers=auth_headers,
        )
        assert response.status_code == 404
