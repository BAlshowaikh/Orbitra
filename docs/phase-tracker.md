# Phase Tracker — Coming Phases

Living checklist for what's left to build, in order. Companion to `travel-platform-plan.md` (the "what and why" doc) and `6-week-roadmap-elaborated.md` (the learning-hints doc) — this one is just for tracking progress phase by phase. Check items off as they land; add a short note under a phase if something changes mid-build.

---

## Where we are now

- **Done:** Auth Service, User Service, Hotel Service, Flight Service (app layer + docs complete for all four; only their own unit/integration tests are outstanding — tracked in `CLAUDE.md`, not duplicated here)
- **In progress / up next:** Booking Service, then Service Discovery (Eureka), then API Gateway — see Phase 3 below

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

### 3.2 Booking Service — up next
**Business goal:** the actual "reserve this room / this seat" action — the core value proposition of a booking platform.

**Sequencing note**: built *before* Eureka/Gateway exist (deliberately reordered — see `travel-platform-plan.md` §5/§6 for the full reasoning). Calls Hotel Service / Flight Service via plain REST at their Docker Compose hostnames (`http://hotel-service:8083`, `http://flight-service:8084`), and gets its own `JwtService`/`JwtAuthFilter` copy same as every other service so far. Both get deliberately rewritten once 3.3/3.4 below exist — known, accepted rework, not a mistake.

**Part 1 — real overselling-protection on Hotel/Flight — ✅ done**, built ahead of Booking Service itself:
- [x] Flight Service: `POST /flights/{flightId}/seats/{seatId}/reserve` / `.../release` — single atomic guarded `UPDATE`, no explicit locking needed (no date dimension)
- [x] Hotel Service: `POST /hotels/{hotelId}/rooms/{roomId}/reserve` / `.../release` — pessimistic lock on the parent `Room` for the whole transaction, all-or-nothing check-then-write across every night in the stay
- [x] Both TRAVELER-gated (`hasRole("TRAVELER")`, no ownership check) — Booking Service will forward the traveler's own JWT rather than use a special service-to-service credential
- [x] `docs/api-reference.md` updated for both services; `CLAUDE.md` + `docs/architecture&logic.md` design rationale added

**Part 2 — Booking Service itself:**
- [ ] `Booking` entity — `id`, `travelerId`, `type` (`HOTEL`/`FLIGHT`), `status` (`PENDING`, `CANCELLED`, `COMPLETED` for now — `CONFIRMED` waits for Payment Service in Phase 4), `hotelId`/`roomId`/`checkInDate`/`checkOutDate` (nullable, HOTEL only), `flightId`/`flightSeatId` (nullable, FLIGHT only), `createdAt` — one entity, nullable-by-discriminator fields, same pattern `Account.partnerType` uses
- [ ] Repository, Flyway migration, config/infra (port 8085), DTOs (`HotelBookingRequest`, `FlightBookingRequest`, `BookingResponse` — two creation endpoints, not one polymorphic request)
- [ ] Own `JwtService`/`JwtAuthFilter` (no partner authority needed — traveler-only), fully-locked-down `SecurityConfig` like User Service
- [ ] Internal HTTP client wrapping calls to Hotel/Flight's `reserve`/`release`, forwarding the caller's JWT — first real inter-service HTTP code in this project
- [ ] `BookingService` — create (call reserve, save `PENDING` on success, clean `409` on failure), cancel (ownership + status check, call release, set `CANCELLED`), `getMyBookings`
- [ ] Controller, exceptions + `GlobalExceptionHandler` (including a failed/unreachable outbound call to Hotel/Flight — new territory)
- [ ] `docs/api-reference.md` Booking Service section

### 3.3 Service Discovery (Eureka)
**Business goal:** none directly user-facing — this is infrastructure that makes the next step (Gateway) possible without hardcoding ports/hostnames.
- [ ] Eureka Server app (its own small Spring Boot project)
- [ ] Register Auth, User, Hotel, Flight, Booking as Eureka clients
- [ ] Rewrite Booking's direct REST calls to Hotel/Flight as Feign clients resolved via Eureka

### 3.4 API Gateway
**Business goal:** one URL for the whole system instead of six different ports — the shape a real frontend or external client would expect.
- [ ] Spring Cloud Gateway app
- [ ] Route `/auth/**`, `/users/**`, `/hotels/**`, `/flights/**`, `/bookings/**` via Eureka
- [ ] Centralize JWT validation at the Gateway (replaces each service's own `JwtAuthFilter` copy, including Booking's — the "real fix" flagged as deferred since Hotel Service was built)
- [ ] Decide/confirm: do individual services still validate as a defense-in-depth layer, or fully hand off to the Gateway? (Worth a deliberate decision, not a default)

**Definition of done for Phase 3:** a traveler can register, browse/search hotels and flights, reserve a room or seat, see it in their booking history, and cancel it — all through one Gateway URL — even though nothing is actually paid for yet.

---

## Frontend — Angular (starts after Phase 3)

**Business goal:** the first real UI. Also personal: this is new territory for you, so budget more time here than the backend phases suggest — it's not "just wiring a screen to an API," it's a genuine learning curve.
- [ ] Angular project setup + routing basics
- [ ] Auth flow (register/login screens, JWT storage, route guards by role)
- [ ] Browse/search screens (hotels, flights) — GUEST-accessible
- [ ] Booking flow (reserve room/seat, view "My Trips")
- [ ] Partner-side screens (hotel/flight listing CRUD) — only if time allows; traveler-side is the higher-value demo path
- [ ] Note going in: booking status will only ever show `PENDING`/`CANCELLED` until Phase 4 ships Payment — don't treat that as a bug

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
