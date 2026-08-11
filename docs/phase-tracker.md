# Phase Tracker — Coming Phases

Living checklist for what's left to build, in order. Companion to `travel-platform-plan.md` (the "what and why" doc) and `6-week-roadmap-elaborated.md` (the learning-hints doc) — this one is just for tracking progress phase by phase. Check items off as they land; add a short note under a phase if something changes mid-build.

---

## Where we are now

- **Done:** Auth Service, User Service, Hotel Service, Flight Service, Booking Service (app layer + docs complete for all five, incl. Hotel/Flight's `reserve`/`release` endpoints), Eureka service discovery (all 5 registered, Booking's Hotel/Flight calls rewritten to Feign+Eureka), and API Gateway (routing, defense-in-depth JWT filter, in-memory rate limiting, backend ports closed — each piece verified individually via Docker Compose). Only unit/integration tests are outstanding across the board, plus one continuous end-to-end walkthrough through the Gateway alone — tracked in `CLAUDE.md`, not duplicated here.
- **In progress / up next:** the end-to-end Gateway walkthrough (register → login → browse/search → book, all through port 8080), then the Angular frontend

---

## Phase 3 — Booking (core transactional flow, no Payment yet)

**Business goal:** a traveler can search and reserve a hotel room or flight seat and see it in their booking history — the first "real" transaction in the system, even without money changing hands yet. This is also the phase that unlocks starting the Angular frontend, since it's the first point where there's a full demoable slice (register → browse → search → book).

### 3.1 Flight Service — ✅ done (built, running, documented)
- [x] Entities: `SeatClass` (catalog) → `Flight` (single dated departure, not recurring) → `FlightSeat` (`totalInventory` + booking-driven `availableCount`). No `FlightAvailability` table — dropped mid-build once `Flight` was settled as one specific dated departure, not a Hotel-style recurring listing. See `CLAUDE.md`'s Architecture (flight-service) section and `docs/architecture&logic.md` for the full reasoning.
- [x] Repositories
- [x] Flyway migrations (one table per file: `seat_class`, `flight`, `flight_seat`, `flight_seat_facility` — one fewer than Hotel, no availability table)
- [x] `application.properties` (port 8084) + `.env`/`.env.example` + `docker-compose.yml` block + `flight_service_db` provisioned and verified
- [x] DTOs
- [x] `JwtService`/`JwtAuthFilter` own copies, granting `PARTNER_FLIGHT` authority
- [x] `SecurityConfig` (public search/browse routes, same pattern as Hotel Service)
- [x] Services — `FlightService`/`SeatClassService`/`FlightSeatService`, incl. the `seatCount` ceiling check (sum of `FlightSeat.totalInventory` ≤ `Flight.seatCount`). No availability get/set endpoints, unlike Hotel — `availableCount` is booking-driven only, never partner-submitted.
- [x] Ownership enforcement (JWT `sub` vs owning `Account`, same per-resource pattern as Hotel Service)
- [x] Controllers
- [x] `GlobalExceptionHandler` + custom exceptions
- [x] `docs/api-reference.md` Flight Service section
- [x] Verified running both natively (`./mvnw spring-boot:run`) and via `docker compose up --build`
- [ ] Unit/integration tests — still deferred, same as Hotel Service

### 3.2 Booking Service — ✅ done (built, running, documented)
**Business goal:** the actual "reserve this room / this seat" action — the core value proposition of a booking platform.

**Sequencing note**: built *before* Eureka/Gateway existed (deliberately reordered — see `travel-platform-plan.md` §5/§6 for the full reasoning). Originally called Hotel/Flight Service via plain REST with hardcoded URLs; rewritten to Feign + Eureka resolution once 3.3 below landed (see there for details) — known, accepted rework, not a mistake. Still has its own `JwtService`/`JwtAuthFilter` copy, same as every other service so far (Gateway/3.4 is what eventually centralizes that).

**Part 1 — real overselling-protection on Hotel/Flight — ✅ done**, built ahead of Booking Service itself:
- [x] Flight Service: `POST /flights/{flightId}/seats/{seatId}/reserve` / `.../release` — single atomic guarded `UPDATE`, no explicit locking needed (no date dimension)
- [x] Hotel Service: `POST /hotels/{hotelId}/rooms/{roomId}/reserve` / `.../release` — pessimistic lock on the parent `Room` for the whole transaction, all-or-nothing check-then-write across every night in the stay. `reserve` returns `ReserveRoomResponse` (price + nights), not the bare list, so Booking Service can compute `totalPrice` without a second call.
- [x] Both TRAVELER-gated (`hasRole("TRAVELER")`, no ownership check) — Booking Service forwards the traveler's own JWT rather than using a special service-to-service credential
- [x] `docs/api-reference.md` updated for both services; `CLAUDE.md` + `docs/architecture&logic.md` design rationale added

**Part 2 — Booking Service itself — ✅ done:**
- [x] Entities — **revised mid-build**: not a single `Booking` table with nullable fields as originally planned, but **JOINED JPA inheritance** (Class Table Inheritance) instead: abstract `Booking` (shared table: `travelerId`, `status`, `totalPrice`, `createdAt`) → concrete `HotelBooking`/`FlightBooking` extension tables, each with only its own real fields, zero nulls either way. `totalPrice` added mid-build too (missing from the original plan) — snapshotted once at creation, never recomputed. See `CLAUDE.md`/`docs/architecture&logic.md` and `docs/springboot-java-notes.md` §18 (sealed interfaces) for the full reasoning.
- [x] Repositories — `BookingRepository` (abstract, unified queries), `HotelBookingRepository`/`FlightBookingRepository` (concrete, type-scoped)
- [x] Flyway migrations — `booking` (parent) → `hotel_booking` → `flight_booking`, each child's `id` a FK back to `booking(id)`, not its own sequence
- [x] `application.properties` (port 8085) + `.env`/`.env.example` + `docker-compose.yml` block + `booking_service_db` provisioned and verified. `HOTEL_SERVICE_URL`/`FLIGHT_SERVICE_URL` existed briefly, removed once 3.3's Feign rewrite landed.
- [x] DTOs — `HotelBookingRequest`/`FlightBookingRequest` (two creation endpoints, not one polymorphic request), `HotelBookingResponse`/`FlightBookingResponse`, plus **`BookingResponse`** (sealed interface, `permits` both) for `GET /bookings/mine`'s unified list
- [x] `JwtService`/`JwtAuthFilter` (no partner authority needed), `SecurityConfig` locked to `hasRole("TRAVELER")` on every route (stricter than User Service's plain `anyRequest().authenticated()`, since every route here is a traveler-only action) + `RestAccessDeniedHandler`
- [x] `HotelServiceClient`/`FlightServiceClient` (`client/` package, new) — first inter-service HTTP calls in this project, token-relay JWT forwarding. Originally built on `RestClient` with hardcoded URLs, rewritten to wrap `HotelServiceFeignClient`/`FlightServiceFeignClient` (Feign + Eureka name resolution) once 3.3 landed — same public methods/exception translation either way, only the internals changed. See `docs/inter-service-http-calls.md` (general reference doc for this pattern).
- [x] `BookingService` — create (reserve-then-save), cancel (release-then-update, with the distributed-consistency gap called out explicitly, not silently ignored), unified `getMyBookings` with optional `?type=` filter
- [x] `BookingController` — `POST /bookings/hotel-rooms`, `POST /bookings/flight-seats`, `PATCH /bookings/{id}/cancel`, `GET /bookings/mine`
- [x] `GlobalExceptionHandler` + custom exceptions, incl. `InventoryServiceUnavailableException` → `503` (new status for this project — a downstream service being unreachable, not this service's own fault)
- [x] `docs/api-reference.md` Booking Service section
- [x] Verified running both natively (`./mvnw spring-boot:run`) and via `docker compose up`
- [ ] Unit/integration tests — still deferred, same as every other service

### 3.3 Service Discovery (Eureka) — ✅ done
**Business goal:** none directly user-facing — this is infrastructure that makes the next step (Gateway) possible without hardcoding ports/hostnames.
- [x] `eureka-service` app (own project, port 8761) — pure infrastructure, no DB/JWT/business logic. Named to match this repo's `-service` suffix convention rather than the more common `eureka-server`. Spring Cloud `2025.1.2` confirmed compatible with Spring Boot `4.1.0` via Initializr.
- [x] Registered Auth, User, Hotel, Flight, Booking as Eureka clients — `spring-cloud-starter-netflix-eureka-client` + `eureka.client.service-url.defaultZone`, no code changes needed (auto-configuration handles registration from `spring.application.name`). Verified all 5 show `UP` on the dashboard, both natively and via `docker compose up`.
- [x] Rewrote Booking's direct REST calls to Hotel/Flight as Feign clients resolved via Eureka — `HotelServiceFeignClient`/`FlightServiceFeignClient` (`@FeignClient(name = "...")`, Eureka resolves the host:port). `HotelServiceClient`/`FlightServiceClient` kept as thin wrappers around them (same public methods, same exception translation) specifically so `BookingService` needed zero changes — deliberately not a global Feign `ErrorDecoder`, to keep the "unreachable" and "declined" cases handled in one place same as before. `HOTEL_SERVICE_URL`/`FLIGHT_SERVICE_URL` config removed as dead.

### 3.4 API Gateway — ✅ mostly done (routing/security/rate-limiting verified, full walkthrough pending)
**Business goal:** one URL for the whole system instead of six different ports — the shape a real frontend or external client would expect.
- [x] Spring Cloud Gateway app — reactive (WebFlux), not WebMVC; regenerated once mid-build after an explicit best-practice-over-speed decision (see `CLAUDE.md`/`docs/architecture&logic.md`)
- [x] Route `/auth/**`, `/users/**`, `/hotels/**` + `/room-types/**`, `/flights/**` + `/seat-classes/**`, `/bookings/**` via Eureka — YAML-based (`application.yml`), `lb://` scheme resolves each target through Eureka + Spring Cloud LoadBalancer
- [x] Defense-in-depth JWT `GlobalFilter` — validates ahead of each service's own independent validation, not a replacement of it; mirrors each service's public-route allowlist rather than duplicating fine-grained role/ownership checks
- [x] Decided: individual services still validate independently too (explicit decision, not a default) — the Gateway is one additional layer, not a centralization/replacement of each service's own `JwtAuthFilter`
- [x] In-memory rate limiting (Bucket4j), per client IP, ahead of the JWT filter in the filter chain
- [x] Backend services' direct port mappings (8081–8085) closed off in `docker-compose.yml` — Gateway (8080) is the only entry point reachable from outside Docker
- [x] `docs/api-reference.md` API Gateway section (routing table + Gateway-level rejection shapes)
- [ ] Full end-to-end business-flow verification through the Gateway alone (register → login → browse/search → book) — individual pieces verified, not yet walked through as one continuous flow
- [ ] Unit/integration tests — same as every other service

**Definition of done for Phase 3:** a traveler can register, browse/search hotels and flights, reserve a room or seat, see it in their booking history, and cancel it — all through one Gateway URL — even though nothing is actually paid for yet.

---

## Frontend — Angular (starts after Phase 3)

**Business goal:** the first real UI. Also personal: this is new territory for you, so budget more time here than the backend phases suggest.

**Locked-in decisions**: standalone components, Angular Material + Tailwind (Preflight disabled), signals for state (no NgRx), Reactive Forms, `localStorage` JWT with no refresh-token flow, Playwright e2e added at the end. Backend now lives under `orbitra-be/`; this becomes a sibling `orbitra-fe/`. Unit tests use **Vitest** (Angular 21's actual default, confirmed via the real scaffold — not Jasmine/Karma as first assumed); this project has no `zone.js` dependency, i.e. it's zoneless by default. **Brand palette locked 2026-08-11**: "Dusk Departure" — violet primary (`#6E3FA3`) + cobalt tertiary (`#2F5D8A`), Sora display font (headings only), Material's own system tokens as the single neutral/background source. See `docs/frontend-architecture&logic.md` for the full reasoning.

**Folder structure (locked in 2026-08-10)**: `core/` (app-wide singletons: services, guards, interceptors, models — already built) and `layout/` (used-once shell pieces: navbar, footer) stay as top-level folders. `shared/` splits by kind of thing (`components/`, `directives/`, `pipes/` — generic, reusable across features). Each folder under `features/<name>/` splits by role: `pages/` (routable screens), `components/` (reusable pieces used by those pages, not routed to directly), `data-access/` (the feature's own `*ApiService`), `state/` (only if actually needed - not created preemptively), `<name>.routes.ts` (lazy-loaded from the main router). `AuthService` stays in `core/auth/` regardless — it's app-wide session state, not a per-feature concern; only the login/register *screens* go under `features/auth/pages/`.

### Phase 0 — ✅ done — CORS prerequisite (`api-gateway`)
- [x] **CORS config** — allow the Angular dev origin (`http://localhost:4200`)

### Phase 1 — Project scaffold & tooling
- [x] **Project init** — `ng new orbitra-fe` (standalone, routing, Tailwind CSS selected at scaffold time instead of plain SCSS)
- [x] **UI libraries** — Angular Material (Azure/Blue theme, `material-theme.scss`) + Tailwind, Preflight disabled in `styles.css`
- [x] **Linting** — ESLint (`@angular-eslint`, flat config) + Prettier
- [x] **Environments** — Gateway base URL config (`apiBaseUrl: http://localhost:8080`, both dev and prod for now)
- [ ] **Folder skeleton** — `core/`, `shared/`, `layout/`, `features/`

### Phase 2 — Core infrastructure
- [x] **Models** — auth/role/error/paged-response DTOs mirrored in `core/models/`; hotel/flight/booking-specific ones added when those features are built
- [x] **AuthService** — JWT signal + `localStorage`, decodes claims for identity, `login()`/`register()`/`logout()`
- [x] **Auth interceptor** — attaches Bearer token
- [x] **Error interceptor** — normalizes `ErrorResponse` via `NotificationService`, handles 401
- [x] **Route guards** — `authGuard` (→ `/login`), `roleGuard`/`partnerTypeGuard` factories (→ `/error` page, not built yet)
- [x] **Layout shell** — role-aware navbar (`layout/navbar/`), footer (`layout/footer/`), wired into root `App` component

### Phase 3 — Auth feature
- [ ] **Register screen** — Reactive Form, conditional `partnerType`
- [ ] **Login screen**

### Phase 4 — Profile feature
- [ ] **Profile screen** — view/edit, respect omitted-vs-null partial-update semantics

### Phase 5 — Hotel browsing (public)
- [ ] **Hotel search screen** — city/dates/guests/price
- [ ] **Hotel detail screen**

### Phase 6 — Flight browsing (public)
- [ ] **Flight search screen** — single travel date
- [ ] **Flight detail screen**

### Phase 7 — Booking flow (priority milestone — makes the app demoable)
- [ ] **Hotel room booking** — reserve/book
- [ ] **Flight seat booking** — reserve/book
- [ ] **My Bookings screen** — mixed hotel/flight, sealed response type
- [ ] **Cancel booking**

### Phase 8 — Partner features
- [ ] **Hotel partner screens** — hotel/room CRUD + availability calendar
- [ ] **Flight partner screens** — flight/seat CRUD

### Phase 9 — Admin features
- [ ] **Account management screen** — view/deactivate
- [ ] **Catalog management screen** — room-type/seat-class CRUD

### Phase 10 — E2E testing
- [ ] **E2E suite** — Playwright golden-path coverage (traveler/partner/admin)

### Phase 11 — Dockerize + polish
- [ ] **Dockerfile** — multi-stage build for `orbitra-fe/`
- [ ] **Compose entry** — frontend service in `docker-compose.yml`
- [ ] **Docs update** — `CLAUDE.md` frontend Architecture + build-progress sections

Note going in: booking status will only ever show `PENDING`/`CANCELLED` until Phase 4 ships Payment — don't treat that as a bug.

---

## Phase 4 — Engagement + Payment

**Business goal:** this is where a booking becomes a real transaction (money + confirmation) and the platform gets the feedback loops (reviews, notifications) that make it feel like a finished product.
- [ ] Payment Service (mock gateway — simulate success/failure)
- [ ] Wire Booking's `PENDING → CONFIRMED` transition to a successful payment
- [ ] Refund rules on cancellation (full/partial/none based on a cancellation window)
- [ ] **First Saga / compensating transaction**: payment fails → release the inventory hold on Hotel/Flight side (this was originally scoped into Phase 3 — moved here since it needs Payment to exist first)
- [ ] Review Service — reviews/ratings, post-`COMPLETED`-status only, tied to a specific `bookingId`
- [ ] Favorites (hotel/flight)
- [ ] Notification Service — confirmation, payment status, cancellation, reminders
- [ ] Admin: moderate reviews

---

## Phase 5 — Packages (stretch / deferred)

**Business goal:** bundled hotel+flight deals with combined pricing — a genuine feature, but the least essential one to prove the platform works, and the most expensive to build (cross-service orchestration + a 2-leg Saga). Kept in scope, just pushed out — revisit only once Phases 1–4 are solid and there's time left.
- [ ] Package Service — combine a hotel offer + flight offer, combined pricing/discount decision (flat vs. hotel+flight−discount%)
- [ ] Parallel availability checks (Hotel Service + Flight Service simultaneously)
- [ ] Package booking → linked hotel + flight bookings, atomic (2-leg Saga extension)
- [ ] Admin: curate featured packages

---

## Cross-cutting, introduced progressively (not their own phase)

- [ ] Circuit Breaker (Resilience4j) — once services call each other synchronously (Phase 4+)
- [ ] Message broker (Kafka/RabbitMQ) — Phase 4 for booking/payment events, reused in Phase 5 if Packages happens
- [ ] Centralized config/logging — whenever manual config management starts getting annoying
