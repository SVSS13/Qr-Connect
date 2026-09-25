"""Seed script to initialize local database and create demo data."""

import asyncio
import uuid
from app.core.database import init_db, async_session_factory
from app.core.security import hash_password
from app.models.owner import Owner
from app.models.settings import UserSettings
from app.models.qr_card import QrCard
from app.models.qr_action import QrAction
from sqlalchemy import select


async def seed():
    print("Initializing database tables...")
    await init_db()

    async with async_session_factory() as session:
        # Check if demo owner already exists
        result = await session.execute(
            select(Owner).where(Owner.email == "demo@example.com")
        )
        owner = result.scalar_one_or_none()

        if not owner:
            print("Creating demo owner...")
            owner = Owner(
                id=uuid.uuid4(),
                name="Demo Owner",
                email="demo@example.com",
                password_hash=hash_password("password123"),
            )
            session.add(owner)
            await session.flush()

            settings = UserSettings(user_id=owner.id)
            session.add(settings)
        else:
            print("Demo owner already exists.")

        # Check if demo card already exists
        card_result = await session.execute(
            select(QrCard).where(QrCard.card_token == "demo-car-123")
        )
        card = card_result.scalar_one_or_none()

        if not card:
            print("Creating demo QR card...")
            card = QrCard(
                id=uuid.uuid4(),
                owner_id=owner.id,
                card_token="demo-car-123",
                name="My Car (Sedan)",
                type="car",
                status="active",
            )
            session.add(card)
            await session.flush()

            actions = [
                QrAction(
                    qr_card_id=card.id,
                    label="Vehicle is Blocking",
                    action_type="alert",
                    icon="alert-circle",
                    sort_order=0,
                    config={"default_text": "Your car is blocking someone or in the way!"},
                    enabled=True,
                ),
                QrAction(
                    qr_card_id=card.id,
                    label="Lights are ON",
                    action_type="alert",
                    icon="alert-triangle",
                    sort_order=1,
                    config={"default_text": "Your vehicle lights were left on!"},
                    enabled=True,
                ),
                QrAction(
                    qr_card_id=card.id,
                    label="Send Message",
                    action_type="message",
                    icon="message-square",
                    sort_order=2,
                    config={"max_length": 500, "placeholder": "Type message to vehicle owner..."},
                    enabled=True,
                ),
                QrAction(
                    qr_card_id=card.id,
                    label="Record Voice",
                    action_type="voice",
                    icon="mic",
                    sort_order=3,
                    config={"max_duration_seconds": 60},
                    enabled=True,
                ),
                QrAction(
                    qr_card_id=card.id,
                    label="Share Location",
                    action_type="location",
                    icon="map-pin",
                    sort_order=4,
                    config={"high_accuracy": True},
                    enabled=True,
                ),
            ]
            for a in actions:
                session.add(a)

            await session.commit()
            print("Demo QR card and dynamic actions created successfully!")
        else:
            print("Demo card already exists.")

        print("\n" + "="*50)
        print("🎉 Database seeded successfully!")
        print(f"👉 Demo Login: demo@example.com / password123")
        print(f"👉 Scanner URL: http://localhost:8000/c/demo-car-123")
        print(f"👉 API Docs (Swagger): http://localhost:8000/docs")
        print("="*50 + "\n")


if __name__ == "__main__":
    asyncio.run(seed())
