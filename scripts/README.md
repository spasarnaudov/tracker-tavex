# Scripts

Every script derives its own project root from its location on disk (nothing
is hardcoded to a specific path or directory name), so cloning this repo
into any directory works without editing any script, and running several
independent checkouts side by side on the same machine (e.g.
`tracker-tavex`, `tracker-tavex-alpha`, `tracker-tavex-beta`) is
fully supported — each checkout only ever touches its own `.env`,
`runtime/`, `logs/`, `backups/`, and its own crontab lines.

One-time environment provisioning (`setup_server.sh`, `setup_environment.sh`,
`init_database.sh`, `install_cron.sh`) lives in [`setup/`](setup/), kept
separate from the scripts you run repeatedly afterward (start/stop the app,
backups, restore, imports).

Database setup, backups, restore, and maintenance are covered separately in
[Database Operations](../docs/database-operations.md).

## New Server Setup

Before the first checkout on a brand-new machine, run this once
(machine-wide, not per-checkout — written for macOS/Apple Silicon with
Homebrew and Docker Desktop):

```bash
./scripts/setup/setup_server.sh
```

It checks for and installs Homebrew, git, python3, and Docker Desktop;
verifies Tailscale is installed and connected; and creates the shared
`postgresql` container (`postgres:17.4`, matching every other checkout of
this project), prompting interactively for a superuser password it never
stores or generates itself. Safe to re-run — every step checks whether it's
already done before acting.

This machine can then host multiple environments (e.g. `alpha`, `beta`,
`production`) side by side, each in its own checkout, sharing this one
PostgreSQL container — continue with Setting Up a New Environment below for
each one.

## Setting Up a New Environment

1. Clone the repo into its own directory.
2. Generate the config and let it carry the rest of the setup through —
   create the virtualenv and install dependencies (requires **Python
   3.10 or newer**; [`setup_server.sh`](#new-server-setup) installs a
   suitable interpreter via Homebrew if the machine doesn't already have
   one), create the database, apply the schema (also creating the default
   `admin`/`admin` account, see
   [Database Setup](../docs/database-operations.md#database-setup)), install
   this environment's cron jobs (see [Install Cron Jobs](#install-cron-jobs)),
   optionally enable automatic Tavex price import (see
   [Import Hourly Prices](#import-hourly-prices)), and start the app:

   ```bash
   ./scripts/setup/setup_environment.sh
   ```

   Prompts for the port, database name, the PostgreSQL password set in
   [New Server Setup](#new-server-setup) above, and whether to enable
   automatic Tavex import, then writes `.env` with a freshly generated
   `SECRET_KEY` and runs the venv setup, `init_database.sh`,
   `install_cron.sh`, and `start_app.sh` in sequence — one command, run by a
   person in one terminal (it needs a real tty for the password prompt),
   takes you from a fresh checkout to a running app.

   Prefer doing it by hand instead? Create the venv yourself
   (`python3.12 -m venv apps/flask/.venv && apps/flask/.venv/bin/pip install
   -r apps/flask/requirements.txt` — use that specific interpreter, not a
   bare `python3`, which on macOS is often too old), copy `.env.example` to
   `.env` and edit it — at minimum set a `DATABASE_URL`/`DB_NAME` unique to
   this environment (a distinct database inside the shared PostgreSQL
   container — see
   [PostgreSQL Runs in Docker](../docs/database-operations.md#postgresql-runs-in-docker))
   and a `PORT` not already used by another environment on this machine —
   then run `docker exec postgresql createdb -U casaos your_db_name`,
   `./scripts/setup/init_database.sh`, `./scripts/setup/install_cron.sh`, and
   `./scripts/start_app.sh` yourself, in that order.

## Run the App

Start, stop, or restart the Flask app as a background process:

```bash
./scripts/start_app.sh
./scripts/stop_app.sh
./scripts/restart_app.sh
```

`start_app.sh` refuses to start a second copy if one is already running (it
tracks the process in `runtime/app.pid`), and reports a clear error instead
of silently doing nothing if the app crashes immediately (for example, the
port from `.env` is already in use). App output goes to `logs/app.log`.
The database backup script also checks whether the app server is running
before proceeding and will prompt to start it if needed, falling back to an
automatic start in non-interactive environments such as cron.

This runs `python apps/flask/app.py` in the background — it's a starting
point, not a substitute for a real process supervisor (systemd, etc.): if
the app crashes after startup, nothing currently restarts it automatically.
To start it automatically after a reboot, see
[Install Cron Jobs](#install-cron-jobs).

## Install Cron Jobs

Install every standard cron job for this checkout in one step:

```bash
./scripts/setup/install_cron.sh
```

Safe to run more than once — it checks each line before adding, so nothing
is ever duplicated, and it never touches another checkout's crontab lines
(every line is built from this script's own project directory). It installs
exactly these four lines (paths shown here for a checkout at
`/home/spas/Projects/tracker-tavex`; adjust automatically to wherever
you actually cloned it):

```cron
@reboot cd /home/spas/Projects/tracker-tavex && ./scripts/start_app.sh >> /home/spas/Projects/tracker-tavex/logs/app.log 2>&1
0 * * * * cd /home/spas/Projects/tracker-tavex && apps/flask/.venv/bin/python scripts/import_tavex_prices.py >> /home/spas/Projects/tracker-tavex/logs/tavex_import.log 2>&1
0 3 * * * cd /home/spas/Projects/tracker-tavex && ./scripts/database/backup_database.sh >> /home/spas/Projects/tracker-tavex/logs/database_backup.log 2>&1
0 3 * * * cd /home/spas/Projects/tracker-tavex && ./scripts/backup_env.sh >> /home/spas/Projects/tracker-tavex/logs/env_backup.log 2>&1
```

Prefer editing the crontab by hand? Run `crontab -e` and add the lines
above yourself (with the path adjusted) — `install_cron.sh` is just a
reliable way to do the same thing without copy-paste mistakes.

It also removes any crontab line left over from an earlier script layout
(for example the pre-reorg `./scripts/backup_database.sh` path) before
installing the current ones, so a rename never leaves a stale line silently
failing every day until someone checks the logs.

After installing, it runs each job once immediately and reports pass/fail
right there in the terminal — so a bad path, a missing dependency, or a
macOS Full Disk Access issue shows up on the spot instead of a day later in
the logs.

## Import Hourly Prices

Run the hourly import job once:

```bash
apps/flask/.venv/bin/python scripts/import_tavex_prices.py
```

The script stores Tavex prices and the current prices of all jewelry/manual items
with the current round hour, for example `14:00:00`.

Manual-item prices are stored every time the cron script runs. Tavex product
prices and the gold buyback price per gram for every available karat are stored
together at the same round-hour timestamp when automatic Tavex import is enabled.
Enable the Tavex part of the cron import with:

```bash
mkdir -p runtime
touch runtime/auto_tavex_import.enabled
```

Disable it with:

```bash
rm runtime/auto_tavex_import.enabled
```

Manual-item snapshots continue even when the Tavex part is disabled.

Both manual script runs and cron use the current round hour. The cron job
that runs this hourly is installed by
[`install_cron.sh`](#install-cron-jobs). Logs are written to:

```text
logs/tavex_import.log
```

## Env Backups

Create a backup of `.env` manually:

```bash
./scripts/backup_env.sh
```

The env backup script:

- loads settings from `.env`
- copies the env file to `backups/env/`
- stores the backup with file permissions `600`
- removes env backups older than `ENV_BACKUP_RETENTION_DAYS`

The relevant `.env` values are:

```bash
ENV_BACKUP_DIR=/absolute/path/to/env/backups
ENV_BACKUP_RETENTION_DAYS=30
```

Env backup files contain secrets and must not be committed to git. The
nightly cron job for this is installed by
[`install_cron.sh`](#install-cron-jobs). Logs are written to:

```text
logs/env_backup.log
```
