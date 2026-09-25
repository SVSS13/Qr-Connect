"""Tests for Rate Limiting security enforcement."""

import pytest
from httpx import AsyncClient

from app.core.limiter import limiter


@pytest.mark.asyncio
class TestRateLimiter:
    """Verifies that brute-force and DDoS rate limits return HTTP 429."""

    async def test_rate_limiting_enforcement(self, client: AsyncClient):
        # Enable limiter temporarily for this specific security test
        limiter.enabled = True
        try:
            # POST /api/auth/login is limited to 10/minute
            exceeded = False
            for i in range(15):
                resp = await client.post(
                    "/api/auth/login",
                    json={"email": f"fake_{i}@test.com", "password": "wrongpassword"},
                )
                if resp.status_code == 429:
                    exceeded = True
                    break

            assert exceeded is True, "Rate limiter did not return 429 after exceeding limit"
        finally:
            # Restore disabled state for other tests
            limiter.enabled = False
