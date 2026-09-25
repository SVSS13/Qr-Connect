"""Tests for Voice recording upload and storage."""

import io
import os
import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.models.event import Event
from app.models.messages_voice import MessagesVoice
from app.models.qr_card import QrCard


@pytest.mark.asyncio
class TestVoiceUpload:
    """Tests for POST /api/public/voice."""

    async def test_upload_voice_success(
        self, client: AsyncClient, auth_headers: dict, db_session: AsyncSession
    ):
        # 1. Create a card
        card_resp = await client.post(
            "/api/owner/cards",
            json={"name": "Voice Car", "type": "car"},
            headers=auth_headers,
        )
        card_token = card_resp.json()["card_token"]

        # 2. Start a 5-min session
        scan_resp = await client.get(f"/api/public/card/{card_token}")
        session_token = scan_resp.json()["session"]["session_token"]

        # 3. Simulate audio file upload (fake webm bytes)
        fake_audio_bytes = b"RIFF....WAVEfmt ....data...."
        files = {
            "audio_file": ("test_recording.webm", io.BytesIO(fake_audio_bytes), "audio/webm")
        }
        data = {
            "session_token": session_token,
            "duration": "15",
        }

        response = await client.post("/api/public/voice", data=data, files=files)
        assert response.status_code == 200
        assert response.json()["success"] is True

        # 4. Verify Event record in database
        event_result = await db_session.execute(
            select(Event).where(Event.type == "voice")
        )
        event = event_result.scalar_one_or_none()
        assert event is not None
        assert event.type == "voice"

        # 5. Verify MessagesVoice record
        voice_result = await db_session.execute(
            select(MessagesVoice).where(MessagesVoice.event_id == event.id)
        )
        voice = voice_result.scalar_one_or_none()
        assert voice is not None
        assert voice.duration == 15
        assert voice.content.startswith("/uploads/voice/")

        # 6. Verify owner messages endpoint returns voice details
        messages_resp = await client.get("/api/owner/messages", headers=auth_headers)
        assert messages_resp.status_code == 200
        messages = messages_resp.json()
        voice_msgs = [m for m in messages if m["type"] == "voice"]
        assert len(voice_msgs) >= 1
        assert voice_msgs[0]["voice_url"] == voice.content
        assert voice_msgs[0]["voice_duration"] == 15

    async def test_upload_voice_invalid_session(self, client: AsyncClient):
        fake_audio = b"fake audio content"
        files = {"audio_file": ("voice.webm", io.BytesIO(fake_audio), "audio/webm")}
        data = {"session_token": "expired-or-invalid-token", "duration": "5"}

        response = await client.post("/api/public/voice", data=data, files=files)
        assert response.status_code == 403
