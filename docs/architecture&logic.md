# Orbitra — Service Architecture Reference

One place to read *why* each service's entity model and design decisions look the way they do — consolidated from `CLAUDE.md`, cross-checked against the actual code (not just copied) as of this writing. `CLAUDE.md` keeps its own copy of this same content for Claude Code's guidance; this file is the human-facing reference. If the two drift, treat the code itself as the tiebreaker, not either doc.

---

## Shared conventions across all services

Package-by-layer under `com.orbitra.<service>_service`:
- `model/` — JPA entities and enums
- `repository/` — Spring Data JPA repositories
- `dto/` — request/response shapes that cross the HTTP boundary (entities never exposed directly from a controller)
- `service/` — business logic
- `controller/` — thin HTTP layer, no business logic
- `config/` — `@Configuration` beans (e.g. Spring Security filter chain)
- `security/` — JWT-specific pieces (token service, auth filter), kept separate from general `config/`
- `exception/` — custom exceptions + a global `@RestControllerAdvice`

Package-by-layer, not package-by-feature — the right choice while each service stays a single focused domain; package-by-feature would only make sense if one service grew multiple unrelated sub-domains.

Every service has its own database (database-per-service) — no shared database, no shared entity definitions. Schema is owned by **Flyway**, never Hibernate `ddl-auto=update` (`validate` only, as a safety check). Secrets are never hardcoded — always `${ENV_VAR:dev-fallback}`.

**Synchronous inter-service calls** were avoided entirely through Auth/User/Hotel/Flight, but Booking Service (Phase 3) breaks that: it has to call Hotel Service's and Flight Service's new `reserve`/`release` endpoints directly, since inventory decrementing has to happen inside whichever service actually owns that data — see the Hotel/Flight sections below for what those endpoints do, and why direct cross-database access was ruled out instead (schema coupling, bypassing each service's own business rules, no access control).

---

## Auth Service (`com.orbitra.auth_service`)

**The core entity is named `Account`, deliberately not `User`.** Holds only credentials/identity: `id`, `email` (unique), `password` (BCrypt hash), `role`, `partnerType`, `enabled`, `createdAt` — nothing about profile data (name, address, preferences), which belongs to the separate User Service. Auth Service owns "who is this and what can they do"; User Service owns "everything else about them."

- `Role` — `TRAVELER | PARTNER | ADMIN` (`GUEST` is the unauthenticated state, never persisted). Mapped `EnumType.STRING`, never `ORDINAL`, so column values stay readable and safe to reorder.
- `PartnerType` — `HOTEL | FLIGHT`, set only when `role == PARTNER`, null otherwise. Every PARTNER account has exactly one type.
- **JWT `sub` claim = `Account.id`, not email** — that's the identifier every downstream service uses to identify whose data a request is about. `JwtService.generateToken()` also embeds `role` and `partnerType` claims, since services never call each other synchronously and these claims are the only source of truth a downstream service gets (e.g. hotel-service telling a HOTEL partner apart from a FLIGHT partner without a database lookup).
- Auth Service and User Service are linked **only** by sharing the same `Account.id` value as a plain column — no cross-database foreign key.

---

## User Service (`com.orbitra.user_service`)

Deliberately duplicates a small amount of code rather than sharing a module with Auth Service (own `JwtService`/`JwtAuthFilter`, own `ErrorResponse`) — no shared library between services; centralizing JWT validation at the Gateway (Phase 2) is the intended real fix, not a shared jar.

- **`UserProfile.id` is never `@GeneratedValue`** — always explicitly set to the caller's own account id, taken from their JWT `sub` claim. A profile row is created lazily, the first time its owner calls `PUT /users/profile`, not proactively at registration (avoids hard runtime coupling before Gateway/Eureka exist).
- `JwtService` here is **validation-only** (no `generateToken`) — this service never issues tokens, only Auth Service does.
- `SecurityConfig` has **no public routes** — every `/users/**` endpoint requires `.anyRequest().authenticated()`, unlike every other service in this project.
- `JwtAuthFilter` here **cannot re-check `enabled` status** per request the way Auth Service's does (no access to the `accounts` table, separate database) — a deactivated account's token keeps working here until it naturally expires.

---

## Hotel Service (`com.orbitra.hotel_service`)

**Entities** (`model/`): `Hotel` → `Room` → `RoomType`/`Availability`.

- **`Hotel`** — id is its own auto-generated identity (a partner can own *many* hotels, unlike `UserProfile`). Linked to its owning `Account` only via a plain `ownerId` column (no FK — same loose cross-database link `UserProfile` uses). `amenities` is a separate `hotel_amenity` element-collection table (one row per amenity string), not a delimited column, so it stays individually queryable. Soft-delete via an `active` flag — never hard-deleted, since future services (Booking) may reference it.
- **`RoomType`** — the admin-managed, global room-type *vocabulary* (e.g. "Deluxe King"): `name` (unique) + `description` + `active`. Hotel partners pick from this list rather than typing free text.
- **`Room`** — a specific hotel's own instance of a `RoomType`: FKs to both `Hotel` and `RoomType` (real FKs — intra-service relations, not cross-service), plus `capacity`, `basePricePerNight`, `totalInventory`, `facilities` (room-level, its own `room_facility` table, distinct from `Hotel.amenities`), `active`. Unique constraint `(hotel_id, room_type_id)` — a hotel can add a given room type at most once. **Naming note**: a `Room` row is *not* one physical room — it represents a hotel's whole stock of a given room type (`totalInventory` physical units behind it).
- **`Availability`** — per-`(room, date)` `availableCount`, unique on `(room_id, date)`. A missing row means "fully available" — effective count is `COALESCE(explicit row, room.totalInventory)`. Partners only ever write rows to *override* specific dates (blackout, custom count). Implemented as one shared lookup reused by search, detail view, and (later) Booking Service's decrement logic.

**Cross-service JWT change**: Auth Service's JWT carries a `partnerType` claim, and both Auth's and User's `JwtAuthFilter` grant an extra `PARTNER_<type>` Spring Security authority (e.g. `PARTNER_HOTEL`) alongside `ROLE_<role>`. Hotel Service's own `JwtAuthFilter` was the first to actually consume this authority for route-level checks.

**Ownership enforcement**: hotel-service does *per-resource* authorization — the caller's JWT `sub` must match `Hotel.ownerId` — enforced in the service layer (`ForbiddenException` → 403), not just `SecurityConfig`. `HotelService.getOwnedHotel()` and `RoomService.getOwnedHotel()`/`getOwnedRoom()` are the shared checks (`getOwnedRoom` also confirms the room actually belongs to the given `hotelId`). `updateStatus()` on both hotels and rooms takes the ADMIN-bypass shape (`isAdmin || owner`) — one shared endpoint per action rather than a separate admin route. `RoomTypeService` has no ownership checks by design — it's the global admin-only catalog, gated at `SecurityConfig`'s `hasRole("ADMIN")`.

**Public routes**: `permitAll` GET routes for browsing/search (`GUEST` role) — the first public GET endpoints since Auth Service's register/login.

**Reserve/release** (`POST .../rooms/{roomId}/reserve` / `.../release`) — built ahead of Booking Service itself, since Booking needs these to exist first. This is the project's first synchronous inter-service call: Booking Service will call these, forwarding the *traveler's own JWT* rather than any special service-to-service credential, so authorization is just `hasRole("TRAVELER")` — no ownership check, since a traveler isn't the resource owner, they're reserving inventory for themselves.

The hard part is concurrency-safety across a whole date range, not just one row. A plain guarded `UPDATE` (the approach Flight Service uses, see below) can't protect the "no `Availability` row yet, falls back to `Room.totalInventory`" case — there's no row to lock when one doesn't exist yet, so two concurrent *first-ever* reservations for the same room/date could both read "fully available" before either writes. Solved by row-locking the **parent `Room`** for the whole transaction (`RoomRepository.findByIdForUpdate`, `PESSIMISTIC_WRITE`) instead of locking per-date — coarser (it serializes *all* reserve/release calls against that room, even ones for non-overlapping dates), but simple and correct, and this project's scale doesn't need finer-grained throughput. `reserve()` is all-or-nothing: it checks every night in the requested stay first, and only writes the decrements if every night has availability — all inside the room-level lock, so nothing can interleave between the check and the write. `release()` mirrors it, capped so it can never push a night's count above `totalInventory`.

---

## Flight Service (`com.orbitra.flight_service`)

Entity model deliberately deviates from a literal Hotel-service mirror in several places — see below for why each one differs.

**Entities** (`model/`): `SeatClass` → `Flight` → `FlightSeat` (no `Availability` equivalent — see decision below).

- **`SeatClass`** — the admin-managed, global seat-class *vocabulary* (e.g. "Economy"/"Business"/"First"): `name` (unique) + `description` + `active`. Mirrors `RoomType` exactly.
- **`Flight`** — **a single specific dated departure, not a recurring schedule.** `AA100` departing March 5th and `AA100` departing March 6th are two different `Flight` rows with two different flight numbers, not the same listing viewed on two dates — a deliberate correction mid-build after the first draft mirrored Hotel's recurring-venue shape too literally. `id` auto-generated, `ownerId` plain column (no FK), `flightNumber` **unique** (unlike `Hotel.name` — a flight number is a real-world identifier for one specific route+schedule, not just a display label), `originCode`/`destinationCode`, `departureTime`/`arrivalTime` as full `LocalDateTime` (not time-of-day — confirms this is one dated instance), `durationMinutes`, `seatCount` (aircraft capacity ceiling, see below), `active` soft-delete. Bulk/recurring creation (auto-generate a batch of flights from a template, one per day) is a real future convenience but explicitly deferred — needs no schema change, just a bulk-create helper layered on top of normal single-`Flight` creation later.
- **`FlightSeat`** — a flight's own instance of a `SeatClass`: FKs to both, `basePricePerSeat`, `totalInventory` (partner-set, fixed capacity for that class on that flight), `availableCount` (seats currently left — starts equal to `totalInventory`, **booking-driven only**: decremented exclusively via the `reserve` endpoint below, never partner-editable directly — confirmed explicitly during design, since there's no partner-facing "manually block seats" feature), `facilities` (own `flight_seat_facility` element-collection table), `active`. Unique constraint `(flight_id, seat_class_id)`. **No `capacity` field** (unlike `Room.capacity`) — a seat always holds exactly one passenger, so the field would just be dead, always-1 data. **No flight-level `amenities` table** (unlike `Hotel.amenities`) — in-flight perks map more naturally to the seat class than the route itself, so they only exist as `FlightSeat.facilities`.

**No `FlightAvailability` table** (unlike Hotel's `Availability`): Hotel needs a per-`(room, date)` calendar because one `Room` row is browsed across many dates. Flight doesn't have that problem — since a `Flight` is already pinned to one specific date, there's no calendar dimension left to track. This table was drafted, then deleted once the single-dated-departure decision above was settled. Consequence: Flight Service has no availability-*range* endpoint (Hotel has `PUT /rooms/{id}/availability`) — `FlightSeat.availableCount` only ever changes via `reserve`/`release`, not a partner-submitted date range.

**`Flight.seatCount` — the aircraft-capacity ceiling**: the sum of every linked `FlightSeat.totalInventory` must not exceed `Flight.seatCount` — enforced in `FlightSeatService` at create/update time (query the existing sum for that flight, reject if the new total would exceed it), not as a DB constraint. Deliberately **`≤`, not strict `=`** — seat classes are added one at a time (Business today, Deluxe added tomorrow) and there's no "finalize/publish this flight" step to know when a partner is "done" allocating seats; strict equality would make a flight look invalid every time it's mid-setup.

**Search implications**: unlike Hotel's search (`checkIn`/`checkOut` date *range* against a per-date availability calendar), Flight search filters by a **single** travel date (since a `Flight` is already one specific dated departure) plus origin/destination/passengers/seat-class/price — no calendar-spanning logic needed.

**Ownership/security**: same pattern as hotel-service — own `JwtService`/`JwtAuthFilter` granting `PARTNER_FLIGHT`, per-resource ownership checks (`getOwnedFlight`/`getOwnedFlightSeat`) with ADMIN-bypass on `updateStatus()`, `permitAll` public search/browse routes, `SeatClassService` has no ownership checks (global admin-only catalog, same as `RoomTypeService`).

**Reserve/release** (`POST .../seats/{seatId}/reserve` / `.../release`) — built the same session as Hotel Service's equivalent, TRAVELER-gated the same way (`hasRole("TRAVELER")`, no ownership check, JWT forwarded by Booking Service). Much simpler than Hotel's version, though: since a `FlightSeat` has no date dimension, concurrency-safety is just **one atomic guarded `UPDATE`** (`FlightSeatRepository.decrementAvailableCount`/`incrementAvailableCount`: `availableCount - 1 WHERE availableCount > 0`, `+ 1 WHERE availableCount < totalInventory`). Postgres's own row locking during that single `UPDATE` is enough — no explicit `@Lock` or transaction-spanning check-then-write needed the way Hotel's date-range version requires.

---

## Net effect: Hotel vs. Flight entity model, side by side

| | Hotel Service | Flight Service |
|---|---|---|
| Top-level entity | Recurring venue (`Hotel`) | Single dated departure (`Flight`) |
| Date-scoped availability table | `Availability` (per room, per date) | None — not needed |
| Capacity ceiling check | None | `Flight.seatCount` vs. sum of `FlightSeat.totalInventory` (`≤`) |
| Per-unit occupancy field | `Room.capacity` | Dropped (always 1 for a seat) |
| Top-level perks table | `Hotel.amenities` | Dropped (seat-level `facilities` only) |
| Remaining-vs-total inventory | `Availability.availableCount` (per date) vs. `Room.totalInventory` | `FlightSeat.availableCount` (booking-driven, no date key) vs. `FlightSeat.totalInventory` |
| Search date param | `checkIn`/`checkOut` range | single travel date |
| Reserve/release concurrency | Pessimistic lock on parent `Room` (per-date rows may not exist yet) | Single atomic guarded `UPDATE` (no date dimension to protect) |
