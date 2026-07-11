# Docker Setup — Architecture, Flow & Playbook

How this project's Docker + Postgres + Flyway setup is structured, what
happens end to end when it runs, and the exact steps to follow when a new
service gets added later. This is the "what does the dockerization part
actually do" reference.

## The two containers

- **`postgres`** — one shared Postgres server (official `postgres:16-alpine`
  image). Not one container per service - every service gets its own
  *database* inside this *one* container, each isolated by its own dedicated
  login (see "Isolation model" below).
- **`auth-service`** — built from `auth-service/Dockerfile`, runs the actual
  Spring Boot app.

They sit on the same Compose-managed network and reach each other by service
name - `auth-service` is told `DB_HOST: postgres`, and Compose resolves
`postgres` to that container automatically, the same way a hostname resolves
to an IP address.

## The division of labor: init.sh vs. Flyway

These do two genuinely different jobs, easy to conflate:

- **`database/postgres/init.sh`** (runs inside the `postgres` container, once,
  only the first time its data volume is empty) creates the *database itself*
  - an empty, named space - plus a dedicated login allowed to use it, plus the
  permission for that login to create things inside it later. When it's done,
  `auth_service_db` exists but is completely empty - no tables, nothing in it.
- **Flyway** (runs inside the `auth-service` app, on every startup) is what
  actually creates the `accounts` table *inside* that already-existing,
  already-empty database, using the migration file in
  `auth-service/src/main/resources/db/migration/`.

Analogy: `init.sh` builds an empty, locked room and hands `auth-service` the
only key. Flyway is what walks into that room afterward and builds the actual
shelves (tables) inside it. Postgres itself never knows or cares what a
"table" is supposed to look like - that's entirely the app's responsibility,
enforced by Flyway instead of Hibernate's `ddl-auto` (which is now set to
`validate` - Hibernate only *checks* the schema matches `Account.java`, it
never creates or alters anything itself anymore).

## The credential chain - why they always match

The login `init.sh` creates and the login `auth-service` connects with are
guaranteed to match because both are fed from the exact same source, never
hardcoded twice by hand:

1. **Root `.env`** defines the real values once: `AUTH_DB_USER`,
   `AUTH_DB_PASSWORD`, etc.
2. **`docker-compose.yml`** hands those same values to *both* containers,
   under each container's own expected variable names:
   - to `postgres`, as `AUTH_DB_USER`/`AUTH_DB_PASSWORD` (which `init.sh`
     reads to run `CREATE USER ... WITH PASSWORD ...`)
   - to `auth-service`, as `DB_USERNAME`/`DB_PASSWORD` (a rename - the app's
     own property names, not Postgres's)
3. **`application.properties`** reads `DB_USERNAME`/`DB_PASSWORD` and tells
   Spring's datasource to connect with them.

One shared source, fed to two different containers under two different
variable names - so they can never silently drift apart.

## Isolation model (why one shared Postgres container is still safe)

By default, Postgres lets *any* login connect to *any* database in the same
server - creating a dedicated `auth_service` user by itself wouldn't actually
stop it from reaching some other service's database later. `init.sh` enforces
real isolation with two extra statements:

```sql
REVOKE ALL ON DATABASE auth_service_db FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE auth_service_db TO auth_service;
```

This makes `auth_service_db` reachable *only* with the `auth_service` login -
enforced by the database engine itself, not just by convention. A separate
`GRANT ALL ON SCHEMA public TO auth_service` (run as a second, separate
connection *to* `auth_service_db` itself) is also required - the
database-level grant above doesn't include permission to create tables inside
the `public` schema (Postgres 15+ no longer grants that by default to anyone
but the schema owner).

## The full end-to-end flow (first run, `docker compose up --build`)

1. Compose reads `docker-compose.yml`, resolving every `${VAR}` against the
   root `.env` sitting next to it.
2. Images get built/pulled: `auth-service` builds via its `Dockerfile`
   (Maven+JDK stage compiles the jar, then a JRE-only stage runs it);
   `postgres` is pulled from Docker Hub as-is.
3. Compose creates a shared network so containers can reach each other by
   service name.
4. Start order is enforced by `depends_on`. `auth-service` depends on
   `postgres` with `condition: service_healthy`, so Compose starts `postgres`
   first and won't start `auth-service` until Postgres's healthcheck
   (`pg_isready`) passes - not just "container started," but "actually ready
   for connections."
5. Postgres boots. Since the named volume is empty on this very first run,
   Postgres's own entrypoint initializes its data directory, creates the
   bootstrap superuser (`POSTGRES_USER`/`POSTGRES_PASSWORD` - used only for
   this initialization, no app ever connects with it), creates the default
   `POSTGRES_DB`. *Only because the volume was empty*, it then runs every
   script in `/docker-entrypoint-initdb.d/` - our mounted `init.sh` - which
   creates `auth_service_db`, the dedicated `auth_service` login, and the
   isolation grants described above.
6. Compose's healthcheck starts passing once Postgres is ready.
7. `auth-service` starts. Spring Boot resolves `DB_HOST` to `postgres` (the
   other container's service name), connects to `postgres:5432/auth_service_db`
   with the `auth_service` credentials `init.sh` just created. Flyway then
   runs, sees an empty schema, and applies `V1__create_accounts_table.sql` -
   this is the moment the `accounts` table actually comes into existence.
   Hibernate (`ddl-auto=validate`) checks the result matches `Account.java`
   and, since it does, the app finishes starting.
8. The `"8081:8081"` port mapping forwards `localhost:8081` on the host
   machine into the container, so Postman/curl/a browser can reach it.

**On every subsequent run:** the volume already has data, so Postgres skips
`init.sh` entirely. Flyway also skips `V1` (it keeps its own record, the
`flyway_schema_history` table, of what's already been applied) and only runs
migrations it hasn't seen yet.

## Adding a new service later (e.g. `hotel-service`)

The shared Postgres container barely changes - it just gains one more
database/login inside it. Same shape every time:

1. New service gets its own folder with its own `Dockerfile`,
   `.dockerignore`, `application.properties` (same `${DB_HOST}`/`${DB_NAME}`/
   etc. pattern as auth-service), its own fixed `server.port` (a new,
   distinct value, never reused), and its own Flyway migrations from day one
   (never start a new service on `ddl-auto=update` - that just defers the
   same migration work to later, for no benefit).
2. Add three lines to root `.env` (and `.env.example`): `HOTEL_DB_NAME`,
   `HOTEL_DB_USER`, `HOTEL_DB_PASSWORD` - same prefixed shape as `AUTH_*`.
3. Add a new service block to `docker-compose.yml` for `hotel-service` -
   pointing `DB_HOST` at the *same* `postgres` service (no second Postgres
   container), its own port mapping, `depends_on: postgres: condition:
   service_healthy`.
4. Append a matching block to `database/postgres/init.sh` for the new
   service's database/login/grants - keeps the file accurate as "what a
   genuinely fresh setup creates," even though it won't run against an
   already-initialized volume.
5. Separately, actually provision the new database against your *already
   running* Postgres container (since step 4 alone won't take effect there):
   `docker exec` into the running `postgres` container and run the same
   `CREATE DATABASE`/`CREATE USER`/`REVOKE`/`GRANT` SQL by hand, once.
6. `docker compose up` - the existing Postgres container doesn't restart, it
   just now also serves the new database.

## Gotchas worth remembering

- `init.sh` only ever runs once, against a genuinely empty volume - editing
  it does nothing to an already-initialized container. New services always
  need the separate manual provisioning step (step 5 above) too.
- `127.0.0.1` inside a container means "this container," never another one -
  cross-container communication always goes through the other service's name
  (`postgres`), never `127.0.0.1` or a hardcoded IP.
- Postgres doesn't isolate databases by default - the `REVOKE`/`GRANT` lines
  in `init.sh` are what make per-service isolation real, not just a naming
  convention.
- `server.port` is fixed per service (not left on Docker's old default of
  8080) specifically so running multiple services natively/in an IDE debugger
  - outside Docker's container isolation entirely - never collides either.
- Flyway migration files never get edited once written and applied - a schema
  change is always a *new* file (`V2__...sql`), never a modification to `V1`.
- Modern Docker Desktop uses `docker compose` (a space) as a built-in
  subcommand - the standalone `docker-compose` binary isn't installed by
  default anymore.
