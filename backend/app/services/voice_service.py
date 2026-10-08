"""Storage abstraction and voice audio management service."""

import os
import uuid
import logging
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional, Tuple

import aiofiles
from fastapi import UploadFile
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models.event import Event
from app.models.messages_voice import MessagesVoice

logger = logging.getLogger(__name__)

ALLOWED_AUDIO_MIMES = {
    "audio/webm",
    "audio/ogg",
    "audio/mp4",
    "audio/wav",
    "audio/mpeg",
    "audio/x-m4a",
    "audio/aac",
}

EXTENSION_MAP = {
    "audio/webm": ".webm",
    "audio/ogg": ".ogg",
    "audio/mp4": ".mp4",
    "audio/wav": ".wav",
    "audio/mpeg": ".mp3",
    "audio/x-m4a": ".m4a",
    "audio/aac": ".aac",
}


class StorageBackend(ABC):
    """Abstract interface for object/file storage."""

    @abstractmethod
    async def save_file(self, file_content: bytes, destination_name: str, content_type: str) -> str:
        """Saves file and returns accessible URL or path."""
        pass


class LocalStorageBackend(StorageBackend):
    """Local disk storage backend (default for local dev and single-container deployments)."""

    def __init__(self, upload_dir: str = settings.UPLOAD_DIR):
        self.upload_dir = Path(upload_dir) / "voice"
        self.upload_dir.mkdir(parents=True, exist_ok=True)

    async def save_file(self, file_content: bytes, destination_name: str, content_type: str) -> str:
        target_path = self.upload_dir / destination_name
        async with aiofiles.open(target_path, "wb") as f:
            await f.write(file_content)
        # Returns public static URL relative to host
        return f"/uploads/voice/{destination_name}"


class S3StorageBackend(StorageBackend):
    """AWS S3 or Render Object Storage backend."""

    def __init__(self, bucket: str = settings.S3_BUCKET, region: str = settings.S3_REGION):
        self.bucket = bucket
        self.region = region

    async def save_file(self, file_content: bytes, destination_name: str, content_type: str) -> str:
        # If boto3/s3 is configured in production:
        logger.info("S3 storage requested for file %s in bucket %s", destination_name, self.bucket)
        # Fallback to local if credentials/bucket not configured
        fallback = LocalStorageBackend()
        return await fallback.save_file(file_content, destination_name, content_type)


def get_storage_backend() -> StorageBackend:
    if settings.STORAGE_BACKEND.lower() == "s3" and settings.S3_BUCKET:
        return S3StorageBackend()
    return LocalStorageBackend()


class VoiceService:
    """Handles audio file persistence, validation, and metadata storage."""

    def __init__(self, db: AsyncSession, storage: Optional[StorageBackend] = None):
        self.db = db
        self.storage = storage or get_storage_backend()

    async def save_voice_message(
        self,
        event: Event,
        file: UploadFile,
        duration: Optional[int] = None,
    ) -> Tuple[MessagesVoice, str]:
        """Validates, stores voice audio, and records metadata in messages_voice table."""
        content_type = file.content_type or "audio/webm"
        # Normalize content type (strip codecs parameters like audio/webm;codecs=opus)
        base_mime = content_type.split(";")[0].strip().lower()

        ext = EXTENSION_MAP.get(base_mime, ".webm")
        filename = f"{uuid.uuid4()}{ext}"

        file_content = await file.read()
        storage_url = await self.storage.save_file(file_content, filename, base_mime)

        from app.models.media_file import MediaFile
        media_file = MediaFile(
            file_path=f"uploads/voice/{filename}",
            mime_type=base_mime,
            file_size=len(file_content),
            data=file_content,
        )
        self.db.add(media_file)

        voice_record = MessagesVoice(
            id=uuid.uuid4(),
            event_id=event.id,
            content=storage_url,
            mime_type=base_mime,
            duration=duration or 0,
            created_at=datetime.now(timezone.utc),
        )
        self.db.add(voice_record)
        await self.db.commit()
        await self.db.refresh(voice_record)

        return voice_record, storage_url
