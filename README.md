# Tracker Tavex

Tracker Tavex is a personal project for tracking investment assets and their historical prices.

## Goals

- Learn PostgreSQL database design
- Build a web application
- Visualize portfolio data with charts
- Host the application on a personal HomeLab server

## Technologies

- PostgreSQL 17
- pgAdmin 4
- Ubuntu 24.04
- Python
- Flask

## Project Structure

```text
tracker-tavex/
├── apps/
│   └── flask/
│       ├── app.py
│       ├── repository.py
│       ├── static/
│       └── templates/
├── database/
│   └── postgresql/
│       ├── maintenance/
│       ├── schema/
│       └── seed/
├── docs/
├── scripts/
├── README.md
└── .gitignore
```

## Documentation

- [docs/database-design.md](docs/database-design.md) — the data model: tables, columns, constraints.
- [docs/database-operations.md](docs/database-operations.md) — database setup, backups, restore, maintenance.
- [docs/git-branch-strategy.md](docs/git-branch-strategy.md) — branch naming, workflow, and push policy.
- [scripts/README.md](scripts/README.md) — environment setup and day-to-day operation (app process, cron, price imports).

## Current Version

The current version includes:

- Asset categories
- Assets
- Historical asset prices with timestamp support
- Flask web interface
- Interactive portfolio value chart
- Multiple configurable asset price charts
- All products stored in the database are selectable in Charts
- Per-chart asset, period, and price interval settings
- Custom chart ranges with start and end dates
- Asynchronous chart filtering without full-page reloads
- Historical Tavex gold buyback prices per gram for each available karat
- Saved chart filter settings in local runtime state
- Tavex product and price import
- Hourly Tavex price import and manual-item price snapshots through cron
- Portfolio page with owned Tavex quantities
- Jewelry and manual items with Tavex gold buyback price helpers
- Portfolio value chart with selectable Tavex and manual gold items
- User login and registration with password hashing
- Successful login history with last-login and usage counts
- Self-service account deactivation with preserved user and portfolio data
- User roles: admin and user
- Database maintenance scripts

## Local Runtime Files

Some application state is intentionally stored locally and is not committed:

- `.env` stores application settings and secrets.
- `runtime/chart_filters.json` stores the selected chart layout and filters.
- `runtime/auto_tavex_import.enabled` controls the Tavex part of the cron import.
  Manual-item price snapshots continue to run every hour.
- `logs/tavex_import.log` stores automatic import output.
- `logs/database_backup.log` stores automatic database backup output.
- `logs/env_backup.log` stores automatic env backup output.
- `backups/database/*.dump` stores PostgreSQL backup files.
- `backups/env/*.backup` stores env backup files with secrets.

## Useful Commands

Create the local configuration from the safe template, then replace its placeholder
secrets and database credentials:

```bash
cp .env.example .env
```

Run the Flask development server from the project root (foreground, stops
with Ctrl+C):

```bash
apps/flask/.venv/bin/python apps/flask/app.py
```

To run it in the background instead — including automatically after a
reboot — see `scripts/start_app.sh`/`stop_app.sh`/`restart_app.sh` in
[scripts/README.md](scripts/README.md#run-the-app).

Run the hourly import job manually:

```bash
apps/flask/.venv/bin/python scripts/import_tavex_prices.py
```

Manual-item snapshots are always stored. Tavex product and gold-buyback prices
are imported only when `runtime/auto_tavex_import.enabled` exists.

Initialize an empty database schema (requires a running PostgreSQL Docker
container — see [Database Operations](docs/database-operations.md#postgresql-runs-in-docker)).
This also creates the default admin account (username `admin`, password
`admin` — change it after first login if the environment is reachable by
anyone other than you):

```bash
./scripts/setup/init_database.sh
```

For the full new-environment walkthrough (venv, `.env`, database, cron,
starting the app), see
[Setting Up a New Environment](scripts/README.md#setting-up-a-new-environment)
in `scripts/README.md`.

Create or update an application user from the terminal:

```bash
apps/flask/.venv/bin/python scripts/create_user.py username
```

Reset the special admin account's password (`init_database.sh` already
creates it on a fresh environment — this is for changing it afterward):

```bash
apps/flask/.venv/bin/python scripts/create_user.py admin --password=admin
```

## Admin dashboards

Users with the `admin` role can access the `Users` and `Logs` dashboards from
the main navigation. Both pages require an authenticated admin account. The
Users dashboard displays fixed roles, creation dates, and login statistics.

The Logs dashboard reads regular `.log` files directly from the project-level
`logs/` directory and displays at most the last 500 lines of each file. Log
files are runtime data and are excluded from Git by `.gitignore`.

## Configuration

All application, database, backup, and runtime settings are loaded from the
single `.env` file in the project root. The file is ignored by git because it
contains secrets. `APP_ENV` inside it controls runtime behavior such as the
default debug mode; it does not select another configuration file.

Role behavior:

- `admin`: reserved for the single configured user-management account.
- `user`: assigned to every other account and sees its own portfolio data.

The special `admin` account is intended only for user and login management. It can open
Users and Logs and change its password, but it cannot browse portfolio or market-data tabs.
Roles cannot be changed from the application. The database permits only one
account with the `admin` role.
Regular users can deactivate their own account from the user menu. The special
`admin` account cannot be deactivated. Admins cannot reactivate a self-deleted
account.

Sessions use `SESSION_TIMEOUT_MINUTES` from `.env`. When there is no
user activity for that many minutes, the user is logged out. A user can have
only one active session; a new login asks whether to log
out the existing session first.

The app uses a project-path-based default `SESSION_COOKIE_NAME`. Set it in
`.env` only when you want an explicit stable cookie name for a deployment.

## Next Steps

- Improve deployment setup for the HomeLab server
