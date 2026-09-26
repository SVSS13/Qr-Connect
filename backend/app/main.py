"""QR Connect FastAPI application entry point."""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.api import auth, cards, actions, settings_router, public, events, messages
from app.core.config import settings

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

# Static and Uploaded files
app.mount("/static", StaticFiles(directory="app/static"), name="static")
app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")

# API Routers — Owner (authenticated)
app.include_router(auth.router, prefix="/api/auth", tags=["auth"])
app.include_router(cards.router, prefix="/api/owner", tags=["cards"])
app.include_router(actions.router, prefix="/api/owner", tags=["actions"])
app.include_router(settings_router.router, prefix="/api/owner", tags=["settings"])
app.include_router(events.router, prefix="/api/owner", tags=["events"])
app.include_router(messages.router, prefix="/api/owner", tags=["messages"])

# Public scanner routes (no auth) — mounted at root for /c/{token}
app.include_router(public.router, tags=["scanner"])


@app.get("/health", tags=["system"])
@app.get("/healthz", tags=["system"])
async def health_check():
    """Health check endpoint for Render and monitoring."""
    return {"status": "healthy", "service": "qr-connect-api", "version": "1.0.0"}
