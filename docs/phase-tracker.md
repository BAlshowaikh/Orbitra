# Phase Tracker — Coming Phases

Living checklist for what's left to build, in order. Companion to `travel-platform-plan.md` (the "what and why" doc) and `6-week-roadmap-elaborated.md` (the learning-hints doc) — this one is just for tracking progress phase by phase. Check items off as they land; add a short note under a phase if something changes mid-build.

---

## Where we are now

- **Done:** Auth Service, User Service, Hotel Service (app layer + docs complete; only its own unit/integration tests and Docker E2E verification are outstanding — tracked in `CLAUDE.md`, not duplicated here)
- **In progress / up next:** Phase 3 below

---

## Phase 3 — Booking (core transactional flow, no Payment yet)

**Business goal:** a traveler can search and reserve a hotel room or flight seat and see it in their booking history — the first "real" transaction in the system, even without money changing hands yet. This is also the phase that unlocks starting the Angular frontend, since it's the first point where there's a full demoable slice (register → browse → search → book).

### 3.1 Flight Service
- [ ] Entities: flight schedule, seat classes/pricing, availability (mirrors Hotel's `Hotel`→`Room`→`RoomType`/`Availability` shape, adapted to flights)
- [ ] Repositories
- [ ] Flyway migrations (one table per file, per this repo's convention)
- [ ] `application.properties` + `.env`/`.env.example` + `docker-compose.yml` block + DB provisioning
- [ ] DTOs
- [ ] `JwtService`/`JwtAuthFilter` own copies, granting `PARTNER_FLIGHT` authority
- [ ] `SecurityConfig` (public search/browse routes, same pattern as Hotel Service)
- [ ] Services — CRUD + search/filter (traveler-side) + availability get/set (partner-side)
- [ ] Ownership enforcement (JWT `sub` vs owning `Account`, same per-resource pattern as Hotel Service)
- [ ] Controllers
- [ ] `GlobalExceptionHandler` + custom exceptions
- [ ] `docs/api-reference.md` Flight Service section

### 3.2 Service Discovery (Eureka)
**Business goal:** none directly user-facing — this is infrastructure that makes the next steps (Gateway, and later Booking calling Hotel/Flight) possible without hardcoding ports/hostnames.
- [ ] Eureka Server app (its own small Spring Boot project)
- [ ] Register Auth, User, Hotel, Flight as Eureka clients

### 3.3 API Gateway
**Business goal:** one URL for the whole system instead of five different ports — the shape a real frontend or external client would expect.
- [ ] Spring Cloud Gateway app
- [ ] Route `/auth/**`, `/users/**`, `/hotels/**`, `/flights/**` (and later `/bookings/**`) via Eureka
- [ ] Centralize JWT validation at the Gateway (replaces each service's own `JwtAuthFilter` copy — the "real fix" flagged as deferred since Hotel Service was built)
- [ ] Decide/confirm: do individual services still validate as a defense-in-depth layer, or fully hand off to the Gateway? (Worth a deliberate decision, not a default)

### 3.4 Booking Service
**Business goal:** the actual "reserve this room / this seat" action — the core value proposition of a booking platform.
- [ ] `Booking` entity — `id`, `userId`, `type` (`HOTEL`/`FLIGHT`), `referenceId`, `status` (`PENDING`, `CANCELLED`, `COMPLETED` for now — `CONFIRMED` waits for Payment Service in Phase 4), timestamps
- [ ] Calls Hotel Service / Flight Service to check + hold availability (Feign client or direct REST — decide which, now that Eureka exists)
- [ ] Concurrency handling — optimistic (`@Version`) or pessimistic locking so two travelers can't book the same room/seat at once
- [ ] Cancellation (no refund logic yet — that's Payment's job in Phase 4)
- [ ] Booking history endpoint (hotel + flight separately for now, matching the "My Trips" split from the requirements doc)
- [ ] `docs/api-reference.md` Booking Service section

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
