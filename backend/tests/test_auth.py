"""Tests for authentication endpoints."""

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
class TestRegister:
    """Tests for POST /api/auth/register."""

    async def test_register_success(self, client: AsyncClient):
        response = await client.post(
            "/api/auth/register",
            json={
                "name": "John Doe",
                "email": "john@example.com",
                "password": "securepass123",
            },
        )
        assert response.status_code == 201
        data = response.json()
        assert "access_token" in data
        assert "refresh_token" in data
        assert data["token_type"] == "bearer"
        assert data["user"]["name"] == "John Doe"
        assert data["user"]["email"] == "john@example.com"
        assert "id" in data["user"]

    async def test_register_duplicate_email(self, client: AsyncClient):
        # Register first user
        await client.post(
            "/api/auth/register",
            json={
                "name": "User 1",
                "email": "dup@example.com",
                "password": "securepass123",
            },
        )
        # Attempt duplicate
        response = await client.post(
            "/api/auth/register",
            json={
                "name": "User 2",
                "email": "dup@example.com",
                "password": "anotherpass123",
            },
        )
        assert response.status_code == 409
        assert "already exists" in response.json()["detail"]

    async def test_register_short_password(self, client: AsyncClient):
        response = await client.post(
            "/api/auth/register",
            json={
                "name": "Short Pass",
                "email": "short@example.com",
                "password": "abc",
            },
        )
        assert response.status_code == 422

    async def test_register_invalid_email(self, client: AsyncClient):
        response = await client.post(
            "/api/auth/register",
            json={
                "name": "Bad Email",
                "email": "not-an-email",
                "password": "securepass123",
            },
        )
        assert response.status_code == 422


@pytest.mark.asyncio
class TestLogin:
    """Tests for POST /api/auth/login."""

    async def test_login_success(self, client: AsyncClient, test_owner):
        response = await client.post(
            "/api/auth/login",
            json={"email": "test@example.com", "password": "testpass123"},
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert "refresh_token" in data
        assert data["user"]["email"] == "test@example.com"

    async def test_login_wrong_password(self, client: AsyncClient, test_owner):
        response = await client.post(
            "/api/auth/login",
            json={"email": "test@example.com", "password": "wrongpassword"},
        )
        assert response.status_code == 401

    async def test_login_nonexistent_email(self, client: AsyncClient):
        response = await client.post(
            "/api/auth/login",
            json={"email": "nobody@example.com", "password": "testpass123"},
        )
        assert response.status_code == 401


@pytest.mark.asyncio
class TestRefresh:
    """Tests for POST /api/auth/refresh."""

    async def test_refresh_success(self, client: AsyncClient, test_owner):
        # Login first
        login_resp = await client.post(
            "/api/auth/login",
            json={"email": "test@example.com", "password": "testpass123"},
        )
        refresh_token = login_resp.json()["refresh_token"]

        # Refresh
        response = await client.post(
            "/api/auth/refresh",
            json={"refresh_token": refresh_token},
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert "refresh_token" in data

    async def test_refresh_with_access_token_fails(
        self, client: AsyncClient, test_owner
    ):
        """Using an access token for refresh should fail."""
        login_resp = await client.post(
            "/api/auth/login",
            json={"email": "test@example.com", "password": "testpass123"},
        )
        access_token = login_resp.json()["access_token"]

        response = await client.post(
            "/api/auth/refresh",
            json={"refresh_token": access_token},
        )
        assert response.status_code == 401

    async def test_refresh_invalid_token(self, client: AsyncClient):
        response = await client.post(
            "/api/auth/refresh",
            json={"refresh_token": "not-a-valid-token"},
        )
        assert response.status_code == 401
