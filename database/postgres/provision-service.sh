#!/usr/bin/env bash
# provision-service.sh
# Manually provisions a new service's database + dedicated login against the
# ALREADY-RUNNING shared Postgres container. Needed because init.sh only ever
# runs once, automatically, the very first time the Postgres volume is empty -
# every service added after that first boot needs this instead, run once by
# hand. Same CREATE DATABASE/USER + REVOKE/GRANT shape as init.sh, just aimed
# at a live container instead of a fresh one - reused as-is for every future
# service, just passing different arguments each time.
#
# Prerequisite: `docker compose up -d postgres` (or the full stack) already
# running.
#
# Usage (run from anywhere):
#   ./database/postgres/provision-service.sh <db_name> <db_user> <db_password>
#
# Example:
#   ./database/postgres/provision-service.sh user_service_db user_service 'Ur7fQ2kxwPz9!Ln4'

set -e

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <db_name> <db_user> <db_password>"
    exit 1
fi

DB_NAME="$1"
DB_USER="$2"
DB_PASSWORD="$3"

# Resolve paths relative to this script's own location, so it works no matter
# which directory it's actually run from.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."

# Bootstrap superuser credentials (POSTGRES_USER/POSTGRES_DB) live in the same
# root .env docker-compose.yml reads - loaded here so this script never
# hardcodes them itself. Extracted as plain text (grep+cut) rather than
# `source`-ing the file directly - sourcing would make bash execute every
# line as a real command, and any unrelated value elsewhere in the file
# containing a shell-special character (e.g. auth-service's password has an
# `&`) would break the whole file, not just that one line.
POSTGRES_USER="$(grep -E '^POSTGRES_USER=' "$REPO_ROOT/.env" | cut -d '=' -f2-)"
POSTGRES_DB="$(grep -E '^POSTGRES_DB=' "$REPO_ROOT/.env" | cut -d '=' -f2-)"

cd "$REPO_ROOT"

echo "Provisioning database '$DB_NAME' and user '$DB_USER'..."

# Passed to psql as its own variables (-v), not interpolated into the heredoc
# by bash - same reasoning as init.sh: keeps shell-special characters in the
# password (this script's args, not an interactive prompt, so no history-
# expansion risk either way) from being misinterpreted before psql sees them.
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v db_name="$DB_NAME" \
     -v db_user="$DB_USER" \
     -v db_password="$DB_PASSWORD" <<-'EOSQL'
    CREATE DATABASE :"db_name";
    CREATE USER :"db_user" WITH PASSWORD :'db_password';

    REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
    GRANT ALL PRIVILEGES ON DATABASE :"db_name" TO :"db_user";
EOSQL

# Schema-level grant needs a separate connection, TO the new database itself -
# same reason as init.sh (Postgres 15+ doesn't grant CREATE on public by
# default to anyone but the schema owner).
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$DB_NAME" \
     -v db_user="$DB_USER" <<-'EOSQL'
    GRANT ALL ON SCHEMA public TO :"db_user";
EOSQL

echo "Done. '$DB_NAME' is ready for '$DB_USER'."
