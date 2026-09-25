"""Notification service handling Firebase Cloud Messaging (FCM) push notifications."""

import base64
import json
import logging
import os
from typing import Any

from app.core.config import settings

logger = logging.getLogger(__name__)

_firebase_initialized = False


def _init_firebase() -> bool:
    """Initialize Firebase Admin SDK if credentials are provided."""
    global _firebase_initialized
    if _firebase_initialized:
        return True

    creds_json = settings.FCM_CREDENTIALS_JSON.strip()
    if not creds_json:
        logger.info("FCM_CREDENTIALS_JSON not configured. Push notifications will run in simulation/log mode.")
        return False

    try:
        import firebase_admin
        from firebase_admin import credentials

        if firebase_admin._apps:
            _firebase_initialized = True
            return True

        # Check if it's base64 encoded
        if not creds_json.startswith("{") and os.path.exists(creds_json):
            cred = credentials.Certificate(creds_json)
        else:
            try:
                # Try raw JSON first
                cert_dict = json.loads(creds_json)
            except Exception:
                # Try base64 decoded JSON
                decoded = base64.b64decode(creds_json).decode("utf-8")
                cert_dict = json.loads(decoded)
            cred = credentials.Certificate(cert_dict)

        firebase_admin.initialize_app(cred)
        _firebase_initialized = True
        logger.info("Firebase Admin SDK successfully initialized.")
        return True
    except Exception as e:
        logger.warning("Failed to initialize Firebase Admin SDK: %s. Running in simulation mode.", e)
        return False


class NotificationService:
    """Service to deliver real-time push notifications to Android devices via FCM."""

    def __init__(self):
        self.is_ready = _init_firebase()

    async def send_to_token(
        self,
        token: str,
        title: str,
        body: str,
        data: dict[str, str] | None = None,
    ) -> bool:
        """Send a notification to a specific device registration token."""
        if not token:
            logger.debug("Cannot send push notification: FCM token is empty.")
            return False

        if not self.is_ready:
            logger.info(
                "[MOCK FCM] Push to '%s...': Title='%s' | Body='%s' | Data=%s",
                token[:12] if len(token) > 12 else token,
                title,
                body,
                data or {},
            )
            return True

        try:
            from firebase_admin import messaging

            string_data = {str(k): str(v) for k, v in (data or {}).items()}

            message = messaging.Message(
                notification=messaging.Notification(title=title, body=body),
                data=string_data,
                token=token,
            )
            response = messaging.send(message)
            logger.info("FCM notification sent successfully: %s", response)
            return True
        except Exception as e:
            logger.error("Error sending FCM notification: %s", e)
            return False

    async def notify_owner_event(
        self,
        fcm_token: str | None,
        card_name: str,
        event_type: str,
        content: str | None = None,
        event_id: str | None = None,
        card_id: str | None = None,
    ) -> bool:
        """Format and dispatch a notification for a newly created event."""
        if not fcm_token:
            logger.info("Owner has no registered FCM token for card '%s'. Skipping push.", card_name)
            return False

        title_map = {
            "alert": f"🚨 Alert: {card_name}",
            "message": f"💬 New Message on {card_name}",
            "voice": f"🎙️ Voice Message on {card_name}",
            "location": f"📍 Location Shared for {card_name}",
        }
        title = title_map.get(event_type, f"🔔 Activity on {card_name}")

        body = content if content else f"New {event_type} interaction received."

        data = {
            "event_type": event_type,
            "card_name": card_name,
        }
        if event_id:
            data["event_id"] = str(event_id)
        if card_id:
            data["card_id"] = str(card_id)

        return await self.send_to_token(
            token=fcm_token,
            title=title,
            body=body,
            data=data,
        )


notification_service = NotificationService()
