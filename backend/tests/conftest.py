"""Pytest configuration and shared fixtures for QR Connect tests.

Uses SQLite in-memory database for fast, isolated test execution.
"""

import uuid
from collections.abc import AsyncGenerator

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.database import Base, get_db
from app.core.security import hash_password
from app.core.config import settings
from app.core.limiter import limiter
from app.main import app
from app.models.owner import Owner
from app.models.settings import UserSettings

settings.TESTING = True
limiter.enabled = False

# Use SQLite for tests — async via aiosqlite


TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

test_engine = create_async_engine(TEST_DATABASE_URL, echo=False)
test_session_factory = async_sessionmaker(
    test_engine, class_=AsyncSession, expire_on_commit=False
)


@pytest_asyncio.fixture(autouse=True)
async def setup_database():
    """Create all tables before each test and drop them after."""
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture
async def db_session() -> AsyncGenerator[AsyncSession, None]:
    """Provide a clean database session for each test."""
    async with test_session_factory() as session:
        yield session


@pytest_asyncio.fixture
async def client(db_session: AsyncSession) -> AsyncGenerator[AsyncClient, None]:
    """Provide an async HTTP test client with overridden DB dependency."""

    async def _override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = _override_get_db

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac

    app.dependency_overrides.clear()


@pytest_asyncio.fixture
async def test_owner(db_session: AsyncSession) -> Owner:
    """Create and return a test owner in the database."""
    owner = Owner(
        id=uuid.uuid4(),
        name="Test Owner",
        email="test@example.com",
        password_hash=hash_password("testpass123"),
    )
    db_session.add(owner)

    settings = UserSettings(user_id=owner.id)
    db_session.add(settings)

    await db_session.commit()
    await db_session.refresh(owner)
    return owner


@pytest_asyncio.fixture
async def auth_headers(client: AsyncClient, test_owner: Owner) -> dict:
    """Login the test owner and return authorization headers."""
    response = await client.post(
        "/api/auth/login",
        json={"email": "test@example.com", "password": "testpass123"},
    )
    assert response.status_code == 200
    token = response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}
