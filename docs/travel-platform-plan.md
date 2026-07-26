# Travel Booking Platform — Requirements & Phased Development Plan

## 1. Roles

| Role | Scope |
|---|---|
| `GUEST` | Unauthenticated visitor — browse/search only |
| `TRAVELER` | Registered customer — books, pays, reviews |
| `PARTNER` | Manages listings — scoped by a fixed `partnerType` (`HOTEL` or `FLIGHT`); each partner account has exactly one type |
| `ADMIN` | Platform-wide control, disputes, moderation |

**Note on Packages:** There is no `PACKAGE_PARTNER` role. Packages are not owned by a single partner — they are *compositions* of existing Hotel + Flight inventory, assembled either by the system (auto-bundling) or by Admin (curated deals). This keeps Hotel Partner and Flight Partner data ownership clean and untouched by the bundling logic.

---

## 2. Services

| Service | Responsibility |
|---|---|
| Auth Service | Registration, login, JWT issuance, role management |
| User Service | Profile data, preferences, account management |
| Hotel Service | Hotel listings, rooms, availability, pricing |
| Flight Service | Flight schedules, seats, pricing |
| **Package Service** | Combines a hotel offer + flight offer into a single bundled deal with combined pricing/discount |
| Booking Service | Reservation lifecycle for hotel, flight, and package bookings |
| Payment Service | Payment processing, refunds |
| Review Service | Reviews and ratings (post-completion only) |
| Notification Service | Email/in-app notifications |
| API Gateway | Single entry point, routing, auth filtering |
| Service Discovery (Eureka) | Service registration/lookup |
| Config Server *(optional)* | Centralized config across services |

---

## 3. Feature List by Role

### GUEST
- Browse hotels (read-only)
- Browse flights (read-only)
- Browse packages (read-only)
- Register / Login

### TRAVELER
- All Guest features, plus:
- Search hotels (location, dates, price, guests)
- Search flights (origin, destination, dates, passengers, class)
- **Search/view packages** (pre-built bundles, or auto-generated bundle suggestions based on a hotel + flight combo)
- Reserve hotel room
- Reserve flight seat
- **Reserve a package (hotel + flight together, single transaction)**
- Make payment
- Cancel a reservation (hotel, flight, or full package)
- View booking history (separate sections: Hotels / Flights / Packages, unified under "My Trips")
- Leave review + rating (only for completed bookings)
- Edit/delete own review
- Mark hotel/flight/package as favorite
- View favorites
- Receive notifications (confirmation, payment status, cancellation, reminders)

### PARTNER (`partnerType = HOTEL`)
- Create/update hotel listing (rooms, pricing, availability calendar)
- Mark availability (blackout dates, sold-out rooms)
- View bookings made against their hotel
- Respond to reviews (optional)

### PARTNER (`partnerType = FLIGHT`)
- Create/update flight listing (schedule, seat classes, pricing)
- Mark availability (seat inventory per flight instance)
- View bookings made against their flights
- Respond to reviews (optional)

### ADMIN
- View/manage all users (deactivate/ban)
- View all bookings (support/dispute resolution)
- Manually cancel/refund a booking
- **Create/curate a featured package** (manually pair a specific hotel deal + flight deal with a custom discount)
- Remove inappropriate reviews

---

## 4. Business Rules to Lock Down Before Coding

- **Booking states:** `PENDING → CONFIRMED → CANCELLED / COMPLETED`
- **Package booking = one transaction, two inventory holds.** If flight seat reservation succeeds but hotel room reservation fails (or vice versa), the whole package booking must roll back — this is your Saga pattern use case.
- **Concurrency:** two users must not book the same room/seat simultaneously — needs inventory locking or optimistic concurrency control (version field).
- **Package pricing:** is it a flat combined price, or (hotel price + flight price − discount%)? Decide this now — it affects your Package Service's data model.
- **Cancellation policy:** full refund window / partial refund window / no-refund window — and whether cancelling a package cancels both legs or allows partial cancellation (e.g., keep the flight, cancel the hotel).
- **Review eligibility:** only after `COMPLETED` status, tied to a specific booking ID (prevents fake reviews).

---

## 5. Phased Development Plan

### **Phase 1 — Foundation**
`Auth Service` + `User Service`
- Registration, login, JWT
- Role-based user model (`GUEST`, `TRAVELER`, `PARTNER` with `partnerType`, `ADMIN`)
- Profile management
- Admin: view/deactivate users

### **Phase 2 — Catalog & Search**
`Hotel Service` + `Flight Service`
- CRUD for listings (partner-side)
- Search + filter + sort (traveler-side)
- Availability management
- Introduce **API Gateway** + **Service Discovery** here (once 2+ services exist, this becomes meaningful to practice)
- Introduce caching (Redis) for search-heavy endpoints

### **Phase 3 — Booking**
`Booking Service`
- Reserve hotel room / flight seat
- Booking history (hotel + flight separately for now)
- Concurrency handling for inventory (optimistic/pessimistic locking on room/seat availability)
- Booking status stays `PENDING`/`CANCELLED` only in this phase — `CONFIRMED`/refund-driven `CANCELLED` waits for Payment Service (Phase 5); the Saga/compensating-transaction work (release inventory hold on payment failure) is deferred along with it, since there's no payment leg yet to fail
- **Frontend (Angular) build starts once this phase is done** — Auth/User/Hotel/Flight/Booking + Gateway + Eureka is enough surface area for a real demoable app, and there's no reason to gate frontend learning behind Payment/Packages

### **Phase 4 — Engagement + Payment**
`Payment Service` + `Review Service` + `Notification Service`
- Payment flow (mock gateway), cancellation with refund rules — this is where Booking's status lifecycle actually completes (`PENDING` → `CONFIRMED`/refunded `CANCELLED`)
- This is where you implement your first **Saga / compensating transaction** (payment fails → release inventory hold) — moved here from the old Phase 3 since it needs Payment to exist first
- Reviews/ratings (post-completion only, tied to booking ID)
- Favorites (hotel/flight)
- Notifications: confirmation, payment status, cancellation, reminders
- Admin: moderate reviews

### **Phase 5 — Packages (Composition Layer)** *(deferred / stretch phase — not required for the core project to be demo-complete)*
`Package Service`
- Combine a hotel offer + flight offer into a bundle
- Orchestrate parallel availability checks (call Hotel Service + Flight Service)
- Combined pricing/discount logic
- Package booking → creates linked hotel + flight bookings atomically (extends the Saga pattern from Phase 4 to a two-service, two-leg transaction)
- Admin: curate featured packages
- Revisit this phase only after Phases 1–4 are solid and there's time left — it's the most expensive addition for the least demo-critical payoff, since it's a composition on top of things that already work

### **Cross-Cutting (introduce progressively, not a separate phase)**
- API Gateway — start Phase 2
- Service Discovery — start Phase 2
- Circuit Breaker (Resilience4j) — start Phase 4, once services call each other synchronously
- Message broker (Kafka/RabbitMQ) — start Phase 4 for booking/payment events, reused heavily in Phase 5
- Centralized config/logging — whenever it starts getting annoying to manage manually (usually mid Phase 4)

---

## 6. Why This Order

- Phase 1–2 give you working, demoable pieces before any distributed-transaction complexity.
- Phase 3 (Booking alone) still teaches concurrency/inventory-locking without needing Payment to exist yet — and gives the frontend something real to call.
- Frontend (Angular) starting after Phase 3 means it's learned in parallel with the harder backend phases rather than blocking on all of them first.
- Phase 4 is where the remaining real microservices lessons live: payment, consistency, partial failure, the first Saga/compensating transaction.
- Phase 5 (Packages) reuses everything from Phase 4 but forces you to orchestrate **two** services in one transaction instead of one — a natural step up in difficulty, not a jump. It's explicitly a stretch goal now, not a required phase.
