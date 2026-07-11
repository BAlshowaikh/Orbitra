#!/usr/bin/env bash
# init.sh
# Runs automatically, exactly once, the first time the shared Postgres
# container starts with an empty data volume (Postgres's own
# /docker-entrypoint-initdb.d/ convention - .sh scripts placed there get
# executed with access to the container's environment variables; .sql files
# placed there would NOT get variable substitution, which is why this is a
# shell script instead of plain SQL).
#
# Creates one database + one dedicated, least-privilege user per service that
# exists at first boot. A service added LATER (after the volume already has
# data) needs its own separate, manually-run provisioning script instead -
# this file only ever runs once and is not re-triggered by editing it.
#
# Expects AUTH_DB_NAME / AUTH_DB_USER / AUTH_DB_PASSWORD to already be set in
# this container's own environment (passed in via docker-compose.yml, sourced
# from real secrets in each environment - local .env for dev, whatever
# secrets mechanism the eventual production host uses - never hardcoded here,
# since this same script has to work unchanged in both places).

set -e

# Secret values are passed to psql as its own variables (-v), then referenced
# in the SQL below via :'name' / :"name" - NOT interpolated directly into the
# heredoc by bash. This matters: an unquoted heredoc lets bash expand
# $variables itself, which means any shell-special character that happened to
# be in a password (a literal $, a backtick, a quote, ...) could be
# misinterpreted by bash before psql ever sees it. Quoting the heredoc
# delimiter ('EOSQL') disables all bash expansion inside it, and lets psql do
# the substitution and escaping itself instead - the mechanism it's actually
# designed for.

# --- auth-service's database + dedicated user ---
# Connects as the bootstrap superuser (POSTGRES_USER/POSTGRES_DB, set by the
# postgres image's own required env vars) to create the new database and role.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v auth_db_name="$AUTH_DB_NAME" \
     -v auth_db_user="$AUTH_DB_USER" \
     -v auth_db_password="$AUTH_DB_PASSWORD" <<-'EOSQL'
    CREATE DATABASE :"auth_db_name";
    CREATE USER :"auth_db_user" WITH PASSWORD :'auth_db_password';

    -- Isolation: by default, every role can connect to every database in the
    -- same Postgres cluster. Revoking from PUBLIC and granting only to this
    -- service's own user is what actually enforces "auth-service's database
    -- is only reachable with auth-service's own credentials" at the engine
    -- level, not just by convention.
    REVOKE ALL ON DATABASE :"auth_db_name" FROM PUBLIC;
    GRANT ALL PRIVILEGES ON DATABASE :"auth_db_name" TO :"auth_db_user";
EOSQL

# Schema-level grant has to happen as a separate connection, TO the new
# database itself - GRANT ALL PRIVILEGES ON DATABASE (above) covers
# database-level things like CONNECT, but not CREATE inside its "public"
# schema (Postgres 15+ no longer grants that to anyone but the schema owner
# by default). Without this, Hibernate (connecting as auth_service later)
# would fail to create its own tables via ddl-auto=update.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$AUTH_DB_NAME" \
     -v auth_db_user="$AUTH_DB_USER" <<-'EOSQL'
    GRANT ALL ON SCHEMA public TO :"auth_db_user";
EOSQL
