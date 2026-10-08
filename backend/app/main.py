"""QR Connect FastAPI application entry point."""

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api import auth, cards, actions, settings_router, public, events, messages
from app.core.config import settings
from app.core.database import get_db
from app.models.media_file import MediaFile

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan: startup and shutdown events."""
    logger.info("QR Connect API starting up — %s", settings.PROJECT_NAME)
    from app.core.database import init_db
    try:
        await init_db()
        logger.info("Database tables initialized")
    except Exception as e:
        logger.warning("Database init warning: %s", e)
    yield
    logger.info("QR Connect API shutting down")


from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded

from app.core.limiter import limiter

app = FastAPI(
    title="QR Connect API",
    description="Generic QR-based contact platform with dynamic actions and ephemeral sessions.",
    version="1.0.0",
    lifespan=lifespan,
)

# Rate limiting
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)


# CORS
origins = [o.strip() for o in settings.CORS_ORIGINS.split(",")]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

from pathlib import Path

# Ensure uploads directory exists
Path("uploads/voice").mkdir(parents=True, exist_ok=True)
Path("uploads/photos").mkdir(parents=True, exist_ok=True)
Path("uploads/videos").mkdir(parents=True, exist_ok=True)

# Static assets
app.mount("/static", StaticFiles(directory="app/static"), name="static")

# Self-healing uploads endpoint: serves from disk cache or restores from PostgreSQL
@app.get("/uploads/{folder}/{filename}", tags=["uploads"])
async def serve_uploaded_file(
    folder: str,
    filename: str,
    db: AsyncSession = Depends(get_db),
):
    safe_folder = Path(folder).name
    safe_filename = Path(filename).name
    file_path = Path("uploads") / safe_folder / safe_filename

    # 1. Serve immediately from disk if available
    if file_path.is_file():
        return FileResponse(file_path)

    # 2. Ephemeral disk restoration: Query PostgreSQL media_files table
    target_rel_path = f"uploads/{safe_folder}/{safe_filename}"
    result = await db.execute(
        select(MediaFile).where(MediaFile.file_path == target_rel_path)
    )
    media = result.scalar_one_or_none()

    if media and media.data:
        try:
            file_path.parent.mkdir(parents=True, exist_ok=True)
            file_path.write_bytes(media.data)
            return FileResponse(file_path, media_type=media.mime_type)
        except Exception as e:
            logger.warning("Could not cache restored media file to disk: %s", e)
            return Response(content=media.data, media_type=media.mime_type or "application/octet-stream")

    raise HTTPException(status_code=404, detail="Media file not found")

# API Routers — Owner (authenticated)
app.include_router(auth.router, prefix="/api/auth", tags=["auth"])
app.include_router(cards.router, prefix="/api/owner", tags=["cards"])
app.include_router(actions.router, prefix="/api/owner", tags=["actions"])
app.include_router(settings_router.router, prefix="/api/owner", tags=["settings"])
app.include_router(events.router, prefix="/api/owner", tags=["events"])
app.include_router(messages.router, prefix="/api/owner", tags=["messages"])

# Public scanner routes (no auth) — mounted at root for /c/{token}
app.include_router(public.router, tags=["scanner"])


@app.get("/", tags=["system"])
async def root():
    """Root endpoint for QR Connect API."""
    return {
        "name": "QR Connect API",
        "status": "online",
        "docs": "/docs",
        "health": "/health",
        "version": "1.0.0"
    }


@app.get("/health", tags=["system"])
@app.get("/healthz", tags=["system"])
async def health_check():
    """Health check endpoint for Render and monitoring."""
    return {"status": "healthy", "service": "qr-connect-api", "version": "1.0.0"}
