"""Alembic environment configuration.

Imports all models so that Base.metadata contains the full schema
for auto-generating migrations.
"""

import sys
from logging.config import fileConfig
from pathlib import Path

from alembic import context
from sqlalchemy import engine_from_config, pool

# Ensure the backend directory is on sys.path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.config import settings as app_settings
from app.core.database import Base

# Import all models to register them with Base.metadata
from app.models.owner import Owner  # noqa: F401
from app.models.qr_card import QrCard  # noqa: F401
from app.models.qr_action import QrAction  # noqa: F401
from app.models.session import ScanSession  # noqa: F401
from app.models.event import Event  # noqa: F401
from app.models.messages_voice import MessagesVoice  # noqa: F401
from app.models.settings import UserSettings  # noqa: F401

# Alembic Config object
config = context.config

# Interpret the config file for Python logging
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# Set the SQLAlchemy URL from app settings (sync driver for Alembic)
config.set_main_option("sqlalchemy.url", app_settings.DATABASE_URL_SYNC)

# Target metadata for 'autogenerate' support
target_metadata = Base.metadata


def run_migrations_offline() -> None:
    """Run migrations in 'offline' mode.

    Generates SQL scripts without connecting to the database.
    """
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Run migrations in 'online' mode.

    Creates an engine and associates a connection with the context.
    """
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
        )

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
