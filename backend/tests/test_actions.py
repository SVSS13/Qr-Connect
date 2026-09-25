"""Tests for QR action CRUD endpoints."""

import pytest
import pytest_asyncio
from httpx import AsyncClient


@pytest_asyncio.fixture
async def card_id(client: AsyncClient, auth_headers: dict) -> str:
    """Create a card and return its ID for action tests."""
    response = await client.post(
        "/api/owner/cards",
        json={"name": "Action Test Card"},
        headers=auth_headers,
    )
    return response.json()["id"]


@pytest.mark.asyncio
class TestListActions:
    """Tests for GET /api/owner/cards/{card_id}/actions."""

    async def test_list_default_actions(
        self, client: AsyncClient, auth_headers: dict, card_id: str
    ):
        response = await client.get(
            f"/api/owner/cards/{card_id}/actions",
            headers=auth_headers,
        )
        assert response.status_code == 200
        actions = response.json()
        assert len(actions) == 4  # Default: alert, message, voice, location

    async def test_list_actions_ordered_by_sort(
        self, client: AsyncClient, auth_headers: dict, card_id: str
    ):
        response = await client.get(
            f"/api/owner/cards/{card_id}/actions",
            headers=auth_headers,
        )
        actions = response.json()
        sort_orders = [a["sort_order"] for a in actions]
        assert sort_orders == sorted(sort_orders)


@pytest.mark.asyncio
class TestCreateAction:
    """Tests for POST /api/owner/cards/{card_id}/actions."""

    async def test_create_action(
        self, client: AsyncClient, auth_headers: dict, card_id: str
    ):
        response = await client.post(
            f"/api/owner/cards/{card_id}/actions",
            json={
                "label": "Custom Alert",
                "action_type": "alert",
                "icon": "bell",
                "sort_order": 10,
                "config": {"custom_text": "Custom notification!"},
            },
            headers=auth_headers,
        )
        assert response.status_code == 201
        data = response.json()
        assert data["label"] == "Custom Alert"
        assert data["action_type"] == "alert"
        assert data["icon"] == "bell"
        assert data["sort_order"] == 10
        assert data["enabled"] is True

    async def test_create_action_invalid_type(
        self, client: AsyncClient, auth_headers: dict, card_id: str
    ):
        response = await client.post(
            f"/api/owner/cards/{card_id}/actions",
            json={"label": "Bad", "action_type": "invalid_type"},
            headers=auth_headers,
        )
        assert response.status_code == 422

    async def test_create_action_nonexistent_card(
        self, client: AsyncClient, auth_headers: dict
    ):
        response = await client.post(
            "/api/owner/cards/00000000-0000-0000-0000-000000000000/actions",
            json={"label": "Test", "action_type": "alert"},
            headers=auth_headers,
        )
        assert response.status_code == 404


@pytest.mark.asyncio
class TestUpdateAction:
    """Tests for PATCH /api/owner/actions/{action_id}."""

    async def test_update_action(
        self, client: AsyncClient, auth_headers: dict, card_id: str
    ):
        # Get default actions
        list_resp = await client.get(
            f"/api/owner/cards/{card_id}/actions",
            headers=auth_headers,
        )
        action_id = list_resp.json()[0]["id"]

        # Update
        response = await client.patch(
            f"/api/owner/actions/{action_id}",
            json={"label": "Updated Label", "enabled": False},
            headers=auth_headers,
        )
        assert response.status_code == 200
        assert response.json()["label"] == "Updated Label"
        assert response.json()["enabled"] is False

    async def test_update_action_invalid_type(
        self, client: AsyncClient, auth_headers: dict, card_id: str
    ):
        list_resp = await client.get(
            f"/api/owner/cards/{card_id}/actions",
            headers=auth_headers,
        )
        action_id = list_resp.json()[0]["id"]

        response = await client.patch(
            f"/api/owner/actions/{action_id}",
            json={"action_type": "not_valid"},
            headers=auth_headers,
        )
        assert response.status_code == 422


@pytest.mark.asyncio
class TestDeleteAction:
    """Tests for DELETE /api/owner/actions/{action_id}."""

    async def test_delete_action(
        self, client: AsyncClient, auth_headers: dict, card_id: str
    ):
        # Get default actions
        list_resp = await client.get(
            f"/api/owner/cards/{card_id}/actions",
            headers=auth_headers,
        )
        action_id = list_resp.json()[0]["id"]
        original_count = len(list_resp.json())

        # Delete
        response = await client.delete(
            f"/api/owner/actions/{action_id}",
            headers=auth_headers,
        )
        assert response.status_code == 204

        # Verify deleted
        list_resp2 = await client.get(
            f"/api/owner/cards/{card_id}/actions",
            headers=auth_headers,
        )
        assert len(list_resp2.json()) == original_count - 1
