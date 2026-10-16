#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

should_continue_with_environment_setup() {
    local project_dir="${1:-.}"
    local env_file="$project_dir/.env"
    local setup_script="$project_dir/scripts/setup/setup_environment.sh"

    if [[ -f "$env_file" ]]; then
        return 1
    fi

    if [[ ! -f "$setup_script" ]]; then
        return 1
    fi

    return 0
}

# One-time machine setup for a new Tracker Tavex server host. Written for
# macOS on Apple Silicon (Homebrew + Docker Desktop) — the alpha/beta/production
# checkouts all share this machine's single PostgreSQL container, per
# scripts/README.md and docs/database-operations.md.
#
# Run this ONCE per machine, before following "Setting Up a New Environment"
# in scripts/README.md for any individual checkout. It is safe to re-run: every
# step first checks whether it's already done.
#
# Prompts interactively for the PostgreSQL superuser password the first time
# the container is created — this script never generates, hardcodes, or
# stores that password itself.

if [[ ! -t 0 || ! -t 1 ]]; then
    echo "ERROR: run this yourself in a terminal — it prompts for passwords and" >&2
    echo "sudo/Touch ID, which need a person typing directly into a real tty." >&2
    exit 1
fi

echo "=== Tracker Tavex server setup ==="
echo

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "This script is written for macOS. Detected: $(uname -s)."
    echo "It may still mostly work (the Docker/Postgres steps are OS-agnostic),"
    echo "but the Homebrew/Docker Desktop steps below assume macOS."
    read -r -p "Continue anyway? [y/N] " CONTINUE
    [[ "$CONTINUE" =~ ^[Yy]$ ]] || exit 1
fi

# --- Homebrew ---
if ! command -v brew &>/dev/null; then
    echo "Homebrew not found — installing (this will ask for your Mac login password)..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    if [[ -x /opt/homebrew/bin/brew ]]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
    echo "Homebrew installed. Add this to your shell profile (~/.zprofile) if the"
    echo "installer didn't already:"
    echo '  eval "$(/opt/homebrew/bin/brew shellenv)"'
else
    echo "Homebrew already installed: $(brew --version | head -1)"
fi
echo

# --- git ---
if ! command -v git &>/dev/null; then
    echo "git not found — installing via Homebrew..."
    brew install git
else
    echo "git already installed: $(git --version)"
fi
echo

# --- python3 ---
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/find_python.sh"
PYTHON_BIN="$(find_suitable_python || true)"

if [[ -z "$PYTHON_BIN" ]]; then
    echo "No Python >=3.10 found (the system python3 is too old) — installing"
    echo "python@3.12 via Homebrew..."
    brew install python@3.12
    PYTHON_BIN="$(brew --prefix python@3.12)/bin/python3.12"
else
    echo "Using $PYTHON_BIN ($("$PYTHON_BIN" --version))"
fi
echo

# --- Docker Desktop ---
if ! command -v docker &>/dev/null; then
    echo "Docker not found — installing Docker Desktop via Homebrew..."
    brew install --cask docker
    echo
    echo "Launching Docker Desktop for first-time setup..."
    # `open -a Docker` (name lookup via Launch Services) can fail right after
    # a fresh `brew install --cask` because LS hasn't indexed the app yet.
    # We know exactly where Homebrew puts it, so open that path directly.
    open -a /Applications/Docker.app
else
    echo "Docker already installed: $(docker --version)"
    open -a /Applications/Docker.app 2>/dev/null || true
fi

# Docker Desktop's own first-launch flow needs someone at the screen for its
# EULA and its privileged-helper install — that install step shows macOS's
# own native authorization dialog (password or Touch ID) and there is no
# legitimate way to script past that boundary, nor should there be. What we
# CAN do is not make the person babysit Terminal too: just poll until the
# daemon responds, so the only thing they actually have to do is answer
# whatever dialog macOS/Docker puts on screen.
echo "Waiting for the Docker daemon to respond (up to 5 minutes — respond to"
echo "any Docker/macOS dialogs that appear, this will continue on its own"
echo "once you do)..."
DOCKER_READY=0
for _ in $(seq 1 150); do
    if docker info &>/dev/null; then
        DOCKER_READY=1
        break
    fi
    sleep 2
done
if [[ "$DOCKER_READY" -ne 1 ]]; then
    echo "ERROR: Docker daemon still not responding after 5 minutes."
    echo "Open Docker Desktop manually, finish any dialogs, wait for the whale"
    echo "icon to stop animating, then re-run this script."
    exit 1
fi
echo "Docker daemon is ready."
echo

echo "Registering Docker Desktop as a login item, so the postgresql container"
echo "(restart policy 'unless-stopped') comes back automatically after a reboot..."
if osascript -e 'tell application "System Events" to make login item at end with properties {path:"/Applications/Docker.app", hidden:false}' &>/dev/null; then
    echo "Done. (If macOS just asked to let Terminal control 'System Events',"
    echo "that's this step — allow it.)"
else
    echo "Could not set this automatically (macOS may have denied Terminal"
    echo "automation permission). Set it by hand instead:"
    echo "  Docker Desktop -> Settings -> General ->"
    echo "  enable 'Start Docker Desktop when you log in'."
fi
echo

# --- Tailscale (expected to already be installed) ---
# The Mac App Store / GUI build of Tailscale.app does not put a `tailscale`
# CLI on PATH by default. Fall back to the app bundle's own binary instead of
# failing outright on a machine where Tailscale is actually installed and
# connected — but invoke it by its real path, not through a symlink: this
# build resolves its own bundle identifier from the path it's invoked as,
# and a symlink (e.g. one at /usr/local/bin/tailscale) breaks that lookup
# and crashes with "Fatal error: The current bundleIdentifier is unknown to
# the registry" (a Swift trap, surfaced as "Trace/BPT trap: 5").
TAILSCALE_BIN=""
if command -v tailscale &>/dev/null; then
    TAILSCALE_BIN="tailscale"
elif [[ -x "/Applications/Tailscale.app/Contents/MacOS/Tailscale" ]]; then
    TAILSCALE_BIN="/Applications/Tailscale.app/Contents/MacOS/Tailscale"
fi

if [[ -z "$TAILSCALE_BIN" ]]; then
    echo "ERROR: Tailscale not found on PATH or at /Applications/Tailscale.app,"
    echo "but it's expected to already be installed on this machine. Install it"
    echo "from https://tailscale.com/download, sign in, then re-run this script."
    exit 1
fi

if ! "$TAILSCALE_BIN" status &>/dev/null; then
    echo "ERROR: Tailscale is installed but not connected. Run 'tailscale up',"
    echo "sign in, then re-run this script."
    exit 1
fi
echo "Tailscale is connected."
echo "MagicDNS name for this machine:"
"$TAILSCALE_BIN" status --self 2>/dev/null | head -1
echo

# --- PostgreSQL container ---
POSTGRES_CONTAINER_NAME="postgresql"
POSTGRES_IMAGE="postgres:17.4"

if docker inspect "$POSTGRES_CONTAINER_NAME" &>/dev/null; then
    echo "Container '$POSTGRES_CONTAINER_NAME' already exists."
    if [[ "$(docker inspect --format='{{.State.Running}}' "$POSTGRES_CONTAINER_NAME")" != "true" ]]; then
        echo "Starting it..."
        docker start "$POSTGRES_CONTAINER_NAME"
    fi
    echo "Nothing more to do here — this script does not touch an existing"
    echo "container's data or password."
else
    echo "Creating the '$POSTGRES_CONTAINER_NAME' container ($POSTGRES_IMAGE)."
    echo "This is a NEW machine, so choose a fresh PostgreSQL superuser"
    echo "password now — pick something you'll store in a password manager,"
    echo "not a placeholder you'll forget:"
    read -r -s -p "PostgreSQL superuser (casaos) password: " PG_PASSWORD
    echo
    read -r -s -p "Confirm password: " PG_PASSWORD_CONFIRM
    echo

    if [[ -z "$PG_PASSWORD" ]]; then
        echo "ERROR: password cannot be empty."
        exit 1
    fi
    if [[ "$PG_PASSWORD" != "$PG_PASSWORD_CONFIRM" ]]; then
        echo "ERROR: passwords did not match. Re-run the script."
        exit 1
    fi

    docker volume create postgresql_data >/dev/null

    docker run -d \
        --name "$POSTGRES_CONTAINER_NAME" \
        --restart unless-stopped \
        -e POSTGRES_USER=casaos \
        -e POSTGRES_PASSWORD="$PG_PASSWORD" \
        -p 5432:5432 \
        -v postgresql_data:/var/lib/postgresql/data \
        "$POSTGRES_IMAGE" >/dev/null

    unset PG_PASSWORD PG_PASSWORD_CONFIRM

    echo "Waiting for PostgreSQL to accept connections..."
    READY=0
    for _ in $(seq 1 30); do
        if docker exec "$POSTGRES_CONTAINER_NAME" pg_isready --quiet 2>/dev/null; then
            READY=1
            break
        fi
        sleep 1
    done
    if [[ "$READY" -ne 1 ]]; then
        echo "ERROR: PostgreSQL did not become ready in time."
        echo "Check: docker logs $POSTGRES_CONTAINER_NAME"
        exit 1
    fi

    echo "PostgreSQL is up, container set to restart automatically (unless-stopped)."
    echo
    echo "Save the username/password you just entered now (e.g. in your"
    echo "password manager) — every environment's .env will need it in its"
    echo "DATABASE_URL: postgresql://casaos:<password>@localhost:5432/<db_name>"
fi
echo

echo "=== Server setup complete ==="
echo
if should_continue_with_environment_setup "$PROJECT_DIR"; then
    echo "Automatically continuing with environment setup..."
    bash "$SCRIPT_DIR/setup_environment.sh"
else
    echo "Next, for each environment (alpha, beta, production):"
    echo "  1. git clone this repo into its own directory"
    echo "  2. ./scripts/setup/setup_environment.sh   (see scripts/README.md — takes you"
    echo "     the rest of the way to a running app: venv, database, schema, cron, start)"
fi
