# Orbitra — Service Architecture Reference

One place to read *why* each service's entity model and design decisions look the way they do — consolidated from `CLAUDE.md`, cross-checked against the actual code (not just copied) as of this writing. `CLAUDE.md` keeps its own copy of this same content for Claude Code's guidance; this file is the human-facing reference. If the two drift, treat the code itself as the tiebreaker, not either doc.

---

## Services at a glance

| Service | Port | Purpose | Status |
|---|---|---|---|
| `auth-service` | 8081 | Registration, login, JWT issuance, role management | ✅ Done |
| `user-service` | 8082 | Profile data (name, contact, preferences) | ✅ Done |
| `hotel-service` | 8083 | Hotel/room listings, availability, search | ✅ Done |
| `flight-service` | 8084 | Flight/seat listings, search | ✅ Done |
| `booking-service` | 8085 | Reserve a room/seat, booking history, cancellation | ✅ Done |
| `eureka-service` | 8761 | Service registry (infra, no business logic) | ✅ Done |
| `api-gateway` | 8080 | Single entry point — routing, JWT check, rate limiting | ✅ Done |
| Payment Service | — | Mock payment gateway, `PENDING` → `CONFIRMED` | Not yet built (Phase 4) |

`api-gateway` is now the only port meant to be reached from outside Docker — the 5 business services' own ports are closed off in `docker-compose.yml` (see the API Gateway section below).

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

**JWT authentication flow, project-wide**: `auth-service` is the only service that ever issues a token (`JwtService.generateToken()`); every other service — `user`, `hotel`, `flight`, `booking`, and now `api-gateway` — only ever validates one, each with its own independent `JwtService`/`JwtAuthFilter` copy rather than a shared library (no shared code module between services, by design). All of them verify against the exact same `JWT_SECRET` value, which must match byte-for-byte across every service's `.env` — a mismatch anywhere silently rejects every otherwise-valid token with no other symptom. The token's `sub` claim is `Account.id` (never email), and it also carries `role`/`partnerType` claims so a downstream service can authorize a request without ever calling Auth Service to look anything up. Now that `api-gateway` exists, it performs the same validation *again*, ahead of each service's own filter — a deliberate defense-in-depth choice, not a replacement of per-service validation (see the API Gateway section below for why centralizing it fully was considered and rejected for now).

**Consistent `ErrorResponse` shape, every service, every non-2xx response**: `{ "timestamp": ..., "status": ..., "message": ... }` — including rejections that originate from `api-gateway` itself (missing/invalid token, rate limit exceeded), so a client can't tell from the response shape alone whether a request was rejected at the Gateway or by whichever backend service it would have reached.

**Soft-delete, never hard-delete**: the same `enabled`/`active`-flag pattern is reused everywhere an entity might still be referenced elsewhere after being "removed" — `Account.enabled`, `Hotel`/`Room`/`RoomType.active`, `Flight`/`FlightSeat`/`SeatClass.active`. A row is flipped inactive, never actually deleted, since a downstream service (Booking, historically) may still hold a reference to it.

**`PagedResponse<T>`, reused across every service with a list endpoint** (`auth`, `hotel`, `flight`, `booking` — not `user`, which only ever returns the caller's own single profile): one shared pagination shape (`content`/`page`/`size`/`totalElements`/`totalPages`) rather than each service inventing its own.

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

The hard part is concurrency-safety across a whole date range, not just one row. A plain guarded `UPDATE` (the approach Flight Service uses, see below) can't protect the "no `Availability` row yet, falls back to `Room.totalInventory`" case — there's no row to lock when one doesn't exist yet, so two concurrent *first-ever* reservations for the same room/date could both read "fully available" before either writes. Solved by row-locking the **parent `Room`** for the whole transaction (`RoomRepository.findByIdForUpdate`, `PESSIMISTIC_WRITE`) instead of locking per-date — coarser (it serializes *all* reserve/release calls against that room, even ones for non-overlapping dates), but simple and correct, and this project's scale doesn't need finer-grained throughput. `reserve()` is all-or-nothing: it checks every night in the requested stay first, and only writes the decrements if every night has availability — all inside the room-level lock, so nothing can interleave between the check and the write. `release()` mirrors it, capped so it can never push a night's count above `totalInventory`. `reserve()` returns `ReserveRoomResponse` (not the bare `List<AvailabilityResponse>`) — bundles `Room.basePricePerNight` alongside the per-night list, so Booking Service can compute a total price without a second call back to this service; `release()` still returns the bare list since nothing needs pricing there. Flight Service's `reserve` didn't need this change — `FlightSeatResponse` already carries `basePricePerSeat`.

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

**Reserve/release** (`POST .../seats/{seatId}/reserve` / `.../release`) — built the same session as Hotel Service's equivalent, TRAVELER-gated the same way (`hasRole("TRAVELER")`, no ownership check, JWT forwarded by Booking Service). Much simpler than Hotel's version, though: since a `FlightSeat` has no date dimension, concurrency-safety is just **one atomic guarded `UPDATE`** (`FlightSeatRepository.decrementAvailableCount`/`incrementAvailableCount`: `availableCount - 1 WHERE availableCount > 0`, `+ 1 WHERE availableCount < totalInventory`). Postgres's own row locking during that single `UPDATE` is enough — no explicit `@Lock` or transaction-spanning check-then-write needed the way Hotel's date-range version requires. No response-shape change needed for pricing either (unlike Hotel's `reserve`) — `FlightSeatResponse`, already returned here, already carries `basePricePerSeat`.

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

---

## Booking Service (`com.orbitra.booking_service`)

**This is the first service in the project that calls other services synchronously** — creating or cancelling a booking has to call Hotel Service's or Flight Service's `reserve`/`release` endpoints directly, since inventory decrementing has to happen inside whichever service actually owns that data. Direct cross-database access was ruled out (schema coupling, bypassing each service's own business rules, no access control) — see `docs/inter-service-http-calls.md` for the general HTTP-call pattern this project settled on.

**Entities — JOINED JPA inheritance (Class Table Inheritance), not a single table with nullable fields.** The original plan was one `Booking` table with every possible field (hotel fields, flight fields) and most of them null depending on type — reconsidered mid-build as unprofessional once actually compared against alternatives. Instead: abstract `Booking` (`@Inheritance(strategy = InheritanceType.JOINED)`) maps to a shared `booking` table holding only common fields (`travelerId`, `status`, `totalPrice`, `createdAt`); `HotelBooking`/`FlightBooking` each map to their own extension table (`hotel_booking`/`flight_booking`) holding only their own type-specific fields, joined back to the parent by a shared id (a foreign key, not its own sequence). Zero nulls either way. `Booking` itself is deliberately **not** sealed, even though its DTO counterpart is — sealing a JPA entity risks conflicting with Hibernate's runtime proxy generation, so the exhaustiveness guarantee is only enforced at the DTO layer. `@SuperBuilder` (not plain `@Builder`) on `Booking` so `HotelBooking`/`FlightBooking`'s own builders can set inherited fields too.

**`totalPrice` — snapshotted once, never recomputed.** Computed at creation time from whatever Hotel/Flight Service's `reserve` response returns (`basePricePerNight × nights`, or `basePricePerSeat` directly for a single flight seat) and stored on the `Booking` row itself. A partner changing their price later doesn't retroactively change what a past booking shows or owes — this is also what Payment Service (Phase 4) will eventually charge against.

**`BookingResponse` — a sealed interface, not `Booking`'s own DTO shape.** `GET /bookings/mine` needs one unified list mixing both booking types, but a hotel booking and a flight booking have genuinely different fields — a sealed interface (`permits HotelBookingResponse, FlightBookingResponse`) plus Jackson's `@JsonTypeInfo`/`@JsonSubTypes` lets each JSON item carry only its own real fields, with a `"type"` discriminator Jackson adds automatically rather than either DTO declaring it itself. `HotelBookingRequest`/`FlightBookingRequest` stay as two separate creation endpoints (not one polymorphic request) — a client always knows which kind of booking it's making, so there's no ambiguity to resolve on the request side the way there is on the response side.

**Inter-service calls — Feign, not `RestClient`.** Originally built directly on `RestClient` with hardcoded URLs (`HOTEL_SERVICE_URL`/`FLIGHT_SERVICE_URL`), before Eureka existed. Rewritten to `HotelServiceFeignClient`/`FlightServiceFeignClient` (`@FeignClient(name = "hotel-service")`, resolved by Eureka at call time, no hardcoded host) once service discovery landed — `HotelServiceClient`/`FlightServiceClient` stayed in place as thin wrappers around the generated Feign interfaces, keeping the same public method signatures and exception-translation logic as before, so `BookingService` itself needed zero changes across the rewrite. Each Feign call forwards the *traveler's own JWT* (`Authorization` header, unchanged) rather than any special service-to-service credential — the same token-relay pattern Hotel/Flight Service's `reserve`/`release` endpoints were built to expect.

**Create/cancel ordering — external call first, local write second, both ways.** `createHotelBooking`/`createFlightBooking` call `reserve` before ever writing a local `Booking` row — a `PENDING` booking only ever exists here if the other service actually confirmed the reservation, and a `409` there (no availability) surfaces here as `InsufficientAvailabilityException`, with nothing saved locally. `cancel()` mirrors this: `release()` is called first, and the row is only marked `CANCELLED` once that succeeds. **Known, accepted gap**: if `release()` succeeds but the local status update then fails, the booking is stuck `PENDING` while its inventory has already been given back elsewhere — a real distributed-consistency issue, intentionally not solved here. This is exactly what this project's Saga/compensating-transaction work (Phase 4) eventually closes.

**Security**: own `JwtService`/`JwtAuthFilter` copy (no partner authority needed — no PARTNER routes exist here at all). `SecurityConfig` is `.anyRequest().hasRole("TRAVELER")` — stricter than User Service's plain `anyRequest().authenticated()`, since every single route here is a traveler-only action, unlike every other service so far which mixes public/partner/admin/traveler routes. Ownership is baked into the lookup query itself (`findByIdAndTravelerId`) rather than a separate check-then-403 step — a booking that exists but belongs to someone else returns the exact same `404` as one that doesn't exist at all, so probing other travelers' booking ids can't distinguish the two cases.

---

## Eureka Service Discovery (`com.orbitra.eureka_service`)

**Business goal**: no direct user-facing value — this is infrastructure that makes the API Gateway possible without hardcoding every backend service's port/hostname. Pure registry: no database, no JWT, no business logic of its own.

**Standalone, not a peer cluster** — `eureka.client.register-with-eureka=false` / `eureka.client.fetch-registry=false`, since this is a single instance, not a cluster of Eureka peers replicating a registry between themselves. Registering with itself, or trying to fetch a registry from a peer that doesn't exist, would just be dead configuration at this project's scale.

**Named `eureka-service`, not the more common `eureka-server`** — matches this repo's own `-service` suffix convention for every top-level directory rather than the ecosystem's usual naming.

**Client-side wiring needed no application code at all** — every business service just adds `spring-cloud-starter-netflix-eureka-client` as a dependency and sets `eureka.client.service-url.defaultZone`; Spring Boot's auto-configuration handles registration under `spring.application.name` automatically, no `@Enable...` annotation or manual registration code needed (unlike this server itself, which does need `@EnableEurekaServer`).

**Spring Cloud `2025.1.2`** — confirmed compatible with Spring Boot `4.1.0` via Spring Initializr's own dependency resolution when scaffolding this service; used as the pinned version for every other service that later needed Spring Cloud dependencies (Feign in Booking Service, the Gateway).

**Consequence for Booking Service**: this is what made the Feign rewrite possible — `HotelServiceFeignClient`/`FlightServiceFeignClient` resolve `hotel-service`/`flight-service` by name through this registry instead of a hardcoded `HOTEL_SERVICE_URL`/`FLIGHT_SERVICE_URL`. See the Booking Service section above.

---

## API Gateway (`com.orbitra.api_gateway`)

Not package-by-layer like the other services — only `dto/`, `security/`, `ratelimit/`, since this service owns no data and defines no business endpoints of its own; its only job is routing plus two cross-cutting checks. The only service on the reactive (WebFlux) stack and the only one configured via YAML instead of `.properties`.

**Reactive over WebMVC**: Spring Cloud Gateway ships two genuinely different implementations — a non-blocking WebFlux one (classic `RouteLocatorBuilder` API) and a blocking WebMVC one (functional `RouterFunction` API). Reactive was chosen deliberately, favoring best practice over the faster build path — a Gateway spends most of its time waiting on a backend response, the textbook case non-blocking I/O suits. The two dependencies expose genuinely incompatible APIs; mixing them up produces a real compile failure, not just a style mismatch.

**YAML routes, not Java**: `spring.cloud.gateway.server.webflux.routes` lists all 7 routes as plain data (`id` / `uri: lb://<service>` / `predicates: - Path=...`) — the right fit since none of the 7 need conditional/dynamic logic. `lb://` triggers Spring Cloud LoadBalancer + Eureka resolution automatically.

**`eureka.instance.prefer-ip-address=true`**: added to every business service, not just this one. Without it, a service registers under whatever hostname the JVM resolves for the local machine — on a machine with a Windows Mobile Hotspot/ICS virtual adapter active, that's an unresolvable `*.mshome.net` hostname, which breaks the Gateway's load balancer even though route matching itself succeeds.

**Defense-in-depth, not replacement**: `JwtAuthGlobalFilter` checks every request against an ordered allowlist mirrored from each service's own `permitAll` routes (protected overrides like `GET /hotels/mine` checked before the more general public `GET /hotels/*` pattern they'd otherwise also match). Anything not on the list needs a valid `Bearer` token or gets rejected here with `401` — but it deliberately does not replicate each service's fine-grained role/ownership checks; those stay fully owned downstream, on top of (not instead of) each service's own independent JWT validation.

**Rate limiting**: in-memory Bucket4j, one token bucket per client IP, not Redis-backed — correct only because there's a single Gateway instance; a horizontally-scaled Gateway would need a shared store instead. Keyed by IP rather than account id since public routes have no token to key off of, and runs ahead of the JWT filter since it needs to cover public routes too.

**Backend service ports closed**: once routing/JWT/rate-limiting were verified working end to end, the 5 business services' host port mappings were removed from `docker-compose.yml` — they're still reachable to each other and to the Gateway over the internal Compose network by hostname, just no longer reachable directly from outside Docker.

---

## Payment Service — not yet built

Phase 4 work, not started. Planned as a mock gateway (simulate success/failure, no real payment processor integration) — its job is to drive `Booking.status` from `PENDING` to `CONFIRMED` on success, and define refund rules on cancellation based on a cancellation window. No entity model or design decisions exist yet to document here.

---

## Deferred cross-cutting concerns

Explicitly not built yet, tracked in `docs/phase-tracker.md`, noted here so this doc doesn't read as though the system already has them:

- **Circuit Breaker (Resilience4j)** — Booking Service already calls Hotel/Flight Service synchronously (an `InventoryServiceUnavailableException` → `503` today is the entire fallback behavior), but a real circuit breaker is deferred until Phase 4, once more synchronous inter-service calls exist and cascading-failure risk is worth the added complexity.
- **Message broker (Kafka/RabbitMQ)** — planned for Phase 4 (booking/payment events), reused in Phase 5 if Packages ships. Nothing in the system publishes or consumes events yet; every cross-service interaction so far is a direct synchronous HTTP call.
- **Centralized config/logging** — each service still manages its own `.env`/`application.properties` independently. No target date — revisit once manual config management across 7 services starts getting genuinely annoying, not on a fixed schedule.
