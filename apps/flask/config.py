import hashlib
import os
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
VALID_APP_ENVIRONMENTS = {"development", "test", "staging", "production"}


def load_env_file(env_path, override=False):
    if not env_path.exists():
        return

    for line in env_path.read_text().splitlines():
        line = line.strip()

        if not line or line.startswith("#") or "=" not in line:
            continue

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()

        if override:
            os.environ[key] = value
        else:
            os.environ.setdefault(key, value)


def get_app_environment():
    app_env = os.getenv("APP_ENV", "development").strip().lower()

    if app_env not in VALID_APP_ENVIRONMENTS:
        valid_names = ", ".join(sorted(VALID_APP_ENVIRONMENTS))
        raise RuntimeError(f"APP_ENV must be one of: {valid_names}")

    return app_env


load_env_file(PROJECT_ROOT / ".env")

APP_ENV = get_app_environment()


def get_bool_env(name, default=False):
    raw_value = os.getenv(name)

    if raw_value is None:
        return default

    return raw_value.strip().lower() in {"1", "true", "yes", "on"}


def get_int_env(name, default):
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def get_default_session_cookie_name():
    project_hash = hashlib.sha256(str(PROJECT_ROOT).encode()).hexdigest()[:12]
    return f"tracker_tavex_{project_hash}_session"


def get_database_config():
    database_url = os.getenv("DATABASE_URL")

    if database_url:
        return database_url

    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": get_int_env("DB_PORT", 5432),
        "dbname": os.getenv("DB_NAME"),
        "user": os.getenv("DB_USER"),
        "password": os.getenv("DB_PASSWORD"),
    }


SECRET_KEY = os.getenv("SECRET_KEY") or os.getenv("APP_SECRET_KEY")
if not SECRET_KEY:
    raise RuntimeError("SECRET_KEY must be set in .env")

DATABASE_CONFIG = get_database_config()
DEBUG = get_bool_env("DEBUG", APP_ENV == "development")
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
HOST = os.getenv("HOST", "127.0.0.1")
PORT = get_int_env("PORT", 5000)
SESSION_TIMEOUT_MINUTES = max(1, get_int_env("SESSION_TIMEOUT_MINUTES", 5))
SESSION_COOKIE_NAME = os.getenv("SESSION_COOKIE_NAME") or get_default_session_cookie_name()
ROLE_MANAGER_USERNAME = os.getenv("ROLE_MANAGER_USERNAME", "admin").lower()

UPLOADS_DIR = PROJECT_ROOT / "uploads" / "receipts"
UPLOADS_DIR.mkdir(parents=True, exist_ok=True)
