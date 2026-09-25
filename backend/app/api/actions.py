"""QR Action CRUD API endpoints."""

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core.database import get_db
from app.models.owner import Owner
from app.models.qr_action import QrAction
from app.models.qr_card import QrCard
from app.schemas.action import ActionCreate, ActionResponse, ActionUpdate

router = APIRouter()

VALID_ACTION_TYPES = {"alert", "message", "voice", "location", "photo", "video", "custom"}


async def _verify_card_ownership(
    card_id: uuid.UUID,
    current_user: Owner,
    db: AsyncSession,
) -> QrCard:
    """Verify that a card belongs to the current user. Raises 404 if not found."""
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
    return card


async def _get_action_with_ownership(
    action_id: uuid.UUID,
    current_user: Owner,
    db: AsyncSession,
) -> QrAction:
    """Fetch an action and verify its card belongs to the current user."""
    result = await db.execute(
        select(QrAction)
        .join(QrCard, QrAction.qr_card_id == QrCard.id)
        .where(
            QrAction.id == action_id,
            QrCard.owner_id == current_user.id,
        )
    )
    action = result.scalar_one_or_none()
    if action is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Action not found",
        )
    return action


@router.get("/cards/{card_id}/actions", response_model=list[ActionResponse])
async def list_actions(
    card_id: uuid.UUID,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[ActionResponse]:
    """List all actions for a QR card, ordered by sort_order."""
    await _verify_card_ownership(card_id, current_user, db)

    result = await db.execute(
        select(QrAction)
        .where(QrAction.qr_card_id == card_id)
        .order_by(QrAction.sort_order)
    )
    actions = result.scalars().all()
    return [ActionResponse.model_validate(a) for a in actions]


@router.post(
    "/cards/{card_id}/actions",
    response_model=ActionResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_action(
    card_id: uuid.UUID,
    payload: ActionCreate,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> ActionResponse:
    """Add a new action to a QR card."""
    await _verify_card_ownership(card_id, current_user, db)

    if payload.action_type not in VALID_ACTION_TYPES:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Invalid action_type. Must be one of: {', '.join(sorted(VALID_ACTION_TYPES))}",
        )

    action = QrAction(
        qr_card_id=card_id,
        label=payload.label,
        action_type=payload.action_type,
        icon=payload.icon,
        sort_order=payload.sort_order,
        config=payload.config,
    )
    db.add(action)
    await db.commit()
    await db.refresh(action)

    return ActionResponse.model_validate(action)


@router.patch("/actions/{action_id}", response_model=ActionResponse)
async def update_action(
    action_id: uuid.UUID,
    payload: ActionUpdate,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> ActionResponse:
    """Update a QR action's properties."""
    action = await _get_action_with_ownership(action_id, current_user, db)

    update_data = payload.model_dump(exclude_unset=True)

    # Validate action_type if being updated
    if "action_type" in update_data and update_data["action_type"] not in VALID_ACTION_TYPES:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Invalid action_type. Must be one of: {', '.join(sorted(VALID_ACTION_TYPES))}",
        )

    for field, value in update_data.items():
        setattr(action, field, value)

    await db.commit()
    await db.refresh(action)

    return ActionResponse.model_validate(action)


@router.delete("/actions/{action_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_action(
    action_id: uuid.UUID,
    current_user: Owner = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> None:
    """Delete a QR action."""
    action = await _get_action_with_ownership(action_id, current_user, db)
    await db.delete(action)
    await db.commit()
