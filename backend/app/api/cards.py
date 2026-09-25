"""QR Card CRUD API endpoints."""

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import get_current_user
from app.core.database import get_db
from app.core.security import generate_card_token
from app.models.owner import Owner
from app.models.qr_card import QrCard
from app.models.qr_action import QrAction
from app.schemas.card import CardCreate, CardResponse, CardUpdate

router = APIRouter()

# Default actions created for every new card
DEFAULT_ACTIONS = [
    {
        "label": "Send Alert",
        "action_type": "alert",
        "icon": "alert-circle",
        "sort_order": 0,
        "config": {"default_text": "Someone is trying to reach you!"},
    },
    {
        "label": "Send Message",
        "action_type": "message",
        "icon": "message-square",
        "sort_order": 1,
        "config": {"max_length": 500, "placeholder": "Type your message..."},
    },
    {
        "label": "Record Voice",
        "action_type": "voice",
        "icon": "mic",
        "sort_order": 2,
        "config": {"max_duration_seconds": 60, "allowed_formats": ["webm", "ogg"]},
    },
    {
        "label": "Share Location",
        "action_type": "location",
        "icon": "map-pin",
        "sort_order": 3,
        "config": {"high_accuracy": True, "timeout_ms": 10000},
    },
]


def get_default_actions_for_type(card_type: str) -> list[dict]:
    t = (card_type or "").lower().strip()
    if t in ["car", "vehicle"]:
        return [
            {"label": "Move Vehicle", "action_type": "alert", "icon": "alert-circle", "sort_order": 0, "config": {"default_text": "Please move your vehicle, it is blocking the way."}},
            {"label": "Lights Left On", "action_type": "alert", "icon": "alert-circle", "sort_order": 1, "config": {"default_text": "Your vehicle lights were left on!"}},
            {"label": "Send Message", "action_type": "message", "icon": "message-square", "sort_order": 2, "config": {"max_length": 500, "placeholder": "Message the vehicle owner..."}},
            {"label": "Record Voice", "action_type": "voice", "icon": "mic", "sort_order": 3, "config": {"max_duration_seconds": 60}},
            {"label": "Attach Photo", "action_type": "photo", "icon": "camera", "sort_order": 4, "config": {"allowed_formats": ["jpg", "png", "webp"]}},
            {"label": "Share Location", "action_type": "location", "icon": "map-pin", "sort_order": 5, "config": {"high_accuracy": True}},
        ]
    elif t in ["home", "door", "front door"]:
        return [
            {"label": "Doorbell Ring", "action_type": "alert", "icon": "alert-circle", "sort_order": 0, "config": {"default_text": "Someone is at your front door!"}},
            {"label": "Package Delivered", "action_type": "alert", "icon": "alert-circle", "sort_order": 1, "config": {"default_text": "Package delivered at your door."}},
            {"label": "Send Message", "action_type": "message", "icon": "message-square", "sort_order": 2, "config": {"max_length": 500, "placeholder": "Leave a message for resident..."}},
            {"label": "Record Voice", "action_type": "voice", "icon": "mic", "sort_order": 3, "config": {"max_duration_seconds": 60}},
            {"label": "Photo Proof", "action_type": "photo", "icon": "camera", "sort_order": 4, "config": {"allowed_formats": ["jpg", "png", "webp"]}},
        ]
    elif t in ["shoerack", "shoe rack", "shoe"]:
        return [
            {"label": "Shoes Misplaced", "action_type": "alert", "icon": "alert-circle", "sort_order": 0, "config": {"default_text": "Shoes placed incorrectly or obstructing pathway."}},
            {"label": "Send Note", "action_type": "message", "icon": "message-square", "sort_order": 1, "config": {"max_length": 500, "placeholder": "Note about shoe rack..."}},
            {"label": "Record Voice", "action_type": "voice", "icon": "mic", "sort_order": 2, "config": {"max_duration_seconds": 60}},
            {"label": "Take Photo", "action_type": "photo", "icon": "camera", "sort_order": 3, "config": {"allowed_formats": ["jpg", "png"]}},
        ]
    elif t in ["luggage", "bag"]:
        return [
            {"label": "Found Bag Alert", "action_type": "alert", "icon": "alert-circle", "sort_order": 0, "config": {"default_text": "I found your luggage / bag!"}},
            {"label": "Share Location", "action_type": "location", "icon": "map-pin", "sort_order": 1, "config": {"high_accuracy": True}},
            {"label": "Message Owner", "action_type": "message", "icon": "message-square", "sort_order": 2, "config": {"max_length": 500, "placeholder": "Where I am keeping the bag..."}},
            {"label": "Photo of Bag", "action_type": "photo", "icon": "camera", "sort_order": 3, "config": {"allowed_formats": ["jpg", "png"]}},
        ]
    elif t in ["pet", "dog", "cat"]:
        return [
            {"label": "Pet Found Alert", "action_type": "alert", "icon": "alert-circle", "sort_order": 0, "config": {"default_text": "Your pet has been found safe!"}},
            {"label": "Share Location", "action_type": "location", "icon": "map-pin", "sort_order": 1, "config": {"high_accuracy": True}},
            {"label": "Contact Owner", "action_type": "message", "icon": "message-square", "sort_order": 2, "config": {"max_length": 500, "placeholder": "Your pet is with me at..."}},
            {"label": "Photo of Pet", "action_type": "photo", "icon": "camera", "sort_order": 3, "config": {"allowed_formats": ["jpg", "png"]}},
        ]
    else:
        return DEFAULT_ACTIONS


@router.get("/cards", response_model=list[CardResponse])
async def list_cards(
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[CardResponse]:
    """List all QR cards belonging to the current owner."""
    result = await db.execute(
        select(QrCard)
        .where(QrCard.owner_id == current_user.id)
        .options(selectinload(QrCard.actions))
        .order_by(QrCard.created_at.desc())
    )
    cards = result.scalars().all()
    return [CardResponse.model_validate(card) for card in cards]


@router.post(
    "/cards",
    response_model=CardResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_card(
    payload: CardCreate,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> CardResponse:
    """Create a new QR card with default actions.

    A unique card_token is auto-generated for the physical QR code.
    """
    # Generate a unique card_token (retry on collision, though extremely unlikely)
    card_token = generate_card_token()

    card = QrCard(
        owner_id=current_user.id,
        card_token=card_token,
        name=payload.name,
        type=payload.type,
    )
    db.add(card)
    await db.flush()  # Get card.id for actions

    # Create default actions customized for scenario type
    for action_def in get_default_actions_for_type(payload.type):
        action = QrAction(
            qr_card_id=card.id,
            **action_def,
        )
        db.add(action)

    await db.commit()
    await db.refresh(card)

    return CardResponse.model_validate(card)


@router.patch("/cards/{card_id}", response_model=CardResponse)
async def update_card(
    card_id: uuid.UUID,
    payload: CardUpdate,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> CardResponse:
    """Update a QR card's name, type, or status."""
    result = await db.execute(
        select(QrCard).where(
            QrCard.id == card_id,
            QrCard.owner_id == current_user.id,
        )
    )
    card = result.scalar_one_or_none()

    if card is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Card not found",
        )

    # Update only provided fields
    update_data = payload.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(card, field, value)

    await db.commit()
    await db.refresh(card)

    return CardResponse.model_validate(card)


@router.delete("/cards/{card_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_card(
    card_id: uuid.UUID,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> None:
    """Delete a QR card and all its associated actions, sessions, and events."""
    result = await db.execute(
        select(QrCard).where(
            QrCard.id == card_id,
            QrCard.owner_id == current_user.id,
        )
    )
    card = result.scalar_one_or_none()

    if card is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Card not found",
        )

    await db.delete(card)
    await db.commit()
