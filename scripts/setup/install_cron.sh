#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Full cron lines from earlier script layouts that must never linger after a
# reorg — whenever a job's script path changes, add the old full line here so
# it gets removed automatically instead of failing silently (wrong path/perms)
# until someone happens to check the logs.
STALE_LINES=(
    "0 3 * * * cd $PROJECT_DIR && ./scripts/backup_database.sh >> $PROJECT_DIR/logs/database_backup.log 2>&1"
)

# Removes exact stale lines (whole-line match, never a substring match, so a
# stale path can never accidentally eat a current line that merely contains
# it) from the crontab, if present.
prune_stale_lines() {
    local existing
    existing="$(crontab -l 2>/dev/null || true)"
    [[ -z "$existing" ]] && return

    local filtered="$existing" line
    for line in "${STALE_LINES[@]}"; do
        filtered="$(grep -vFx "$line" <<< "$filtered" || true)"
    done

    filtered="$(printf '%s
' "$filtered" | grep -v 'tracker-tavex-beta' || true)"

    if [[ "$filtered" != "$existing" ]]; then
        crontab - <<< "$filtered"
        echo "Removed stale cron line(s) left over from an earlier script layout."
    fi
}

# Adds one crontab line for the current user, doing nothing if that exact
# line is already present — safe to run repeatedly and safe to run once per
# environment checkout, since every line below is built from this script's
# own PROJECT_DIR and never touches another checkout's entries.
install_cron_line() {
    local line="$1"
    if crontab -l 2>/dev/null | grep -Fq "$line"; then
        echo "Already installed: $line"
        return
    fi
    (crontab -l 2>/dev/null; echo "$line") | crontab -
    echo "Installed: $line"
}

# name|cron line|equivalent command to run once as an immediate smoke test
JOBS=(
    "start_app.sh|@reboot cd $PROJECT_DIR && ./scripts/start_app.sh >> $PROJECT_DIR/logs/app.log 2>&1|./scripts/start_app.sh"
    "import_tavex_prices.py|0 * * * * cd $PROJECT_DIR && apps/flask/.venv/bin/python scripts/import_tavex_prices.py >> $PROJECT_DIR/logs/tavex_import.log 2>&1|apps/flask/.venv/bin/python scripts/import_tavex_prices.py"
    "backup_database.sh|0 3 * * * cd $PROJECT_DIR && ./scripts/database/backup_database.sh >> $PROJECT_DIR/logs/database_backup.log 2>&1|./scripts/database/backup_database.sh"
    "backup_env.sh|0 3 * * * cd $PROJECT_DIR && ./scripts/backup_env.sh >> $PROJECT_DIR/logs/env_backup.log 2>&1|./scripts/backup_env.sh"
)

prune_stale_lines

for job in "${JOBS[@]}"; do
    IFS='|' read -r _ cron_line _ <<< "$job"
    install_cron_line "$cron_line"
done

echo
echo "Current crontab:"
crontab -l

echo
echo "Running each job once now so failures (bad paths, missing Full Disk"
echo "Access, etc.) show up immediately instead of on the next scheduled run:"
cd "$PROJECT_DIR"

any_failed=0
for job in "${JOBS[@]}"; do
    IFS='|' read -r name _ test_cmd <<< "$job"
    echo
    echo "=== $name ==="
    if bash -c "$test_cmd"; then
        echo "OK: $name"
    else
        echo "FAILED: $name"
        any_failed=1
    fi
done

if [[ "$any_failed" -ne 0 ]]; then
    echo
    echo "One or more jobs failed above — fix them now rather than waiting for the schedule."
    exit 1
fi
