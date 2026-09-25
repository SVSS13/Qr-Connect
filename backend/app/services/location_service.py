"""Location service handling reverse geocoding and coordinates lookup."""

import logging
from typing import Optional, Tuple
import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)

# Simple in-memory cache: (rounded_lat, rounded_lng) -> address
_geocode_cache: dict[Tuple[float, float], str] = {}


class LocationService:
    """Service to resolve coordinates into human-readable street addresses."""

    def __init__(self):
        self.google_api_key = settings.GOOGLE_MAPS_API_KEY.strip()

    async def reverse_geocode(self, latitude: float, longitude: float) -> str:
        """Resolve latitude and longitude into a street address.

        Uses Google Maps Geocoding API if key is present;
        falls back to OpenStreetMap Nominatim;
        falls back to formatted coordinate string if external lookups fail.
        """
        # Round to 4 decimal places (~11 meters precision) for cache key
        cache_key = (round(latitude, 4), round(longitude, 4))
        if cache_key in _geocode_cache:
            return _geocode_cache[cache_key]

        # 1. Try Google Maps API if configured
        if self.google_api_key:
            address = await self._reverse_geocode_google(latitude, longitude)
            if address:
                _geocode_cache[cache_key] = address
                return address

        # 2. Fallback to OpenStreetMap Nominatim
        address = await self._reverse_geocode_osm(latitude, longitude)
        if address:
            _geocode_cache[cache_key] = address
            return address

        # 3. Final fallback: formatted coordinates
        fallback = f"Coordinates: {latitude:.5f}, {longitude:.5f}"
        _geocode_cache[cache_key] = fallback
        return fallback

    async def _reverse_geocode_google(self, latitude: float, longitude: float) -> Optional[str]:
        """Query Google Maps Geocoding API."""
        try:
            url = "https://maps.googleapis.com/maps/api/geocode/json"
            params = {
                "latlng": f"{latitude},{longitude}",
                "key": self.google_api_key,
            }
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(url, params=params)
                if resp.status_code == 200:
                    data = resp.json()
                    results = data.get("results", [])
                    if results:
                        return results[0].get("formatted_address")
        except Exception as e:
            logger.warning("Google Maps geocoding failed: %s", e)
        return None

    async def _reverse_geocode_osm(self, latitude: float, longitude: float) -> Optional[str]:
        """Query OpenStreetMap Nominatim reverse geocoding API."""
        try:
            url = "https://nominatim.openstreetmap.org/reverse"
            params = {
                "lat": latitude,
                "lon": longitude,
                "format": "json",
                "zoom": 18,
                "addressdetails": 1,
            }
            headers = {
                "User-Agent": "QRConnect-App/1.0",
            }
            async with httpx.AsyncClient(timeout=4.0) as client:
                resp = await client.get(url, params=params, headers=headers)
                if resp.status_code == 200:
                    data = resp.json()
                    display_name = data.get("display_name")
                    if display_name:
                        return display_name
        except Exception as e:
            logger.warning("OSM geocoding failed or timed out: %s", e)
        return None


location_service = LocationService()
