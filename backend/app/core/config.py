"""Application settings loaded from environment variables."""

from pydantic import computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """QR Connect application settings.

    All values can be overridden via environment variables or a .env file.
    """

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    # General
    PROJECT_NAME: str = "QR Connect"
    TESTING: bool = False
    VANITY_BASE_URL: str = "https://qr.svss.io"

    # Database

    DATABASE_URL: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/qrconnect"

    @computed_field  # type: ignore[prop-decorator]
    @property
    def ASYNC_DATABASE_URL(self) -> str:
        """Asynchronous database URL for SQLAlchemy async engine, converting Render postgres:// prefixes."""
        url = self.DATABASE_URL
        if url.startswith("postgres://"):
            url = url.replace("postgres://", "postgresql+asyncpg://", 1)
        elif url.startswith("postgresql://") and not url.startswith("postgresql+asyncpg://"):
            url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
        return url

    @computed_field  # type: ignore[prop-decorator]
    @property
    def DATABASE_URL_SYNC(self) -> str:
        """Synchronous database URL for Alembic migrations."""
        url = self.DATABASE_URL
        if url.startswith("postgres://"):
            url = url.replace("postgres://", "postgresql://", 1)
        return url.replace("+asyncpg", "").replace("+aiosqlite", "")

    # JWT
    SECRET_KEY: str = "change-me-in-production-use-a-real-secret-key"
    JWT_ALGORITHM: str = "HS256"
    JWT_EXPIRY_MINUTES: int = 60
    JWT_REFRESH_EXPIRY_DAYS: int = 30

    # Sessions
    SESSION_EXPIRY_MINUTES: int = 5

    # Rate Limiting
    RATE_LIMIT_PER_MINUTE: int = 30

    # CORS
    CORS_ORIGINS: str = "*"

    # Firebase Cloud Messaging
    FCM_CREDENTIALS_JSON: str = ""

    # Google Maps Geocoding
    GOOGLE_MAPS_API_KEY: str = ""

    # IP Geolocation
    IPGEO_API_KEY: str = ""

    # File Storage
    STORAGE_BACKEND: str = "local"  # 'local' or 's3'
    UPLOAD_DIR: str = "uploads"
    S3_BUCKET: str = ""
    S3_REGION: str = ""


settings = Settings()
