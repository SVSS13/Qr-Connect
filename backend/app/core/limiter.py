"""Rate limiting configuration using Slowapi."""

from slowapi import Limiter
from slowapi.util import get_remote_address

from app.core.config import settings

# Key limiter by remote client IP address
limiter = Limiter(
    key_func=get_remote_address,
    default_limits=[f"{settings.RATE_LIMIT_PER_MINUTE}/minute"],
    headers_enabled=False,
    enabled=not settings.TESTING,
)
