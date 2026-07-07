# Travel Booking Platform — 6-Week Roadmap (Elaborated with Learning Hints)

> Each step includes: what to do, why it matters, and what to search/learn if you're unfamiliar with it.

---

## WEEK 1 — Environment Setup + Foundation (Auth & User Service)

**Step 1: Set up your dev environment**
Install JDK 17+ (LTS), an IDE (IntelliJ IDEA Community is the standard for Spring), Maven or Gradle, Docker Desktop, and Postman.
*Search:* "install JDK 17 [your OS]", "IntelliJ Spring Boot setup", "Postman basics for REST API testing"

**Step 2: Learn Spring Boot fundamentals**
Before writing real code, understand what `@SpringBootApplication` does, how auto-configuration works, and the standard project structure (`src/main/java`, `application.yml`, Maven's `pom.xml`).
*Search:* "Spring Boot project structure explained", "Spring Boot auto-configuration", "Spring Initializr" (use this site to generate your first project skeleton — don't hand-write the boilerplate)

**Step 3: Decide your repo strategy**
Monorepo (all services in one Git repo, different folders) is easier to manage as a solo learner. Multi-repo is more "real-world" but adds Git overhead you don't need yet.
*Search:* "monorepo vs multi-repo microservices" — just to understand the tradeoff, then pick monorepo for this project.

**Step 4: Design the User entity + roles**
This is a design task, not coding yet. Sketch out fields: `id`, `email`, `password (hashed)`, `role` (enum: `TRAVELER`, `PARTNER`, `ADMIN`), `partnerType` (nullable enum: `HOTEL`, `FLIGHT`).
*Search:* "JPA @Entity enum mapping", "storing enums in database Spring Boot" (there are two ways — `ORDINAL` vs `STRING` — you want `STRING`, look up why)

**Step 5: Build Auth Service — register & login**
This service issues JWTs. You'll need Spring Security + a JWT library (e.g., `jjwt`).
*Search:* "Spring Boot JWT authentication tutorial", "Spring Security password encoding BCrypt", "Spring Boot register login JWT example"

**Step 6: Build User Service — profile CRUD**
Simple REST controller + JPA repository. This is your first "normal" Spring Boot CRUD service — a good confidence builder before things get harder.
*Search:* "Spring Boot REST CRUD tutorial", "Spring Data JPA repository basics"

**Step 7: Connect to a real database**
Don't use H2 in-memory for anything beyond a quick test — set up PostgreSQL (or MySQL) locally or via Docker.
*Search:* "run PostgreSQL in Docker", "Spring Boot connect PostgreSQL application.yml"

**Step 8: Test both services independently**
Use Postman to hit register/login/profile endpoints manually before moving on. Don't skip this — catching bugs here is 10x cheaper than after Week 2.

✅ **Week 1 done when:** You can register, log in, receive a JWT, and use it to access a protected profile endpoint.

---

## WEEK 2 — Catalog Services (Hotel & Flight) + Gateway + Discovery

**Step 1: Learn Spring Cloud basics**
Two new concepts this week: Service Discovery (Eureka) and API Gateway. Understand *why* microservices need these before implementing — services need to find each other dynamically, and clients need one entry point instead of hitting 5 different ports.
*Search:* "what is Eureka service discovery", "what is an API Gateway microservices", "Spring Cloud Gateway vs Netflix Zuul" (use Spring Cloud Gateway — Zuul is legacy)

**Step 2: Build Hotel Service**
Listing CRUD (for partners) + search/filter (for travelers). Reuse patterns from your User Service CRUD work.
*Search:* "Spring Boot search filter query parameters", "JPA Specification dynamic queries" (useful once your search filters grow beyond 2-3 fields)

**Step 3: Build Flight Service**
Same pattern as Hotel Service, different domain model (flight schedule, seat classes instead of rooms).

**Step 4: Register services with Eureka**
Set up a Eureka Server (its own small Spring Boot app), then add the Eureka Client dependency to Auth, User, Hotel, Flight.
*Search:* "Spring Cloud Eureka server setup tutorial", "Eureka client registration Spring Boot"

**Step 5: Set up API Gateway routing**
One Gateway app that routes `/auth/**`, `/users/**`, `/hotels/**`, `/flights/**` to the right service via Eureka.
*Search:* "Spring Cloud Gateway routes application.yml example"

**Step 6: Centralize JWT validation at the Gateway**
Instead of every service validating JWTs individually, do it once at the Gateway (a filter).
*Search:* "Spring Cloud Gateway JWT filter", "global filter Spring Cloud Gateway"

**Step 7: Add basic Redis caching**
Cache search results for hotels/flights — a good first taste of performance optimization in a distributed system.
*Search:* "Spring Boot Redis cache tutorial", "@Cacheable annotation Spring"

✅ **Week 2 done when:** Everything goes through one Gateway URL, JWT is validated centrally, and Hotel/Flight search works with basic caching.

---

## WEEK 3 — Booking & Payment (Core Transactional Flow)

**Step 1: Design the Booking entity + status lifecycle**
Fields: `id`, `userId`, `type` (`HOTEL`/`FLIGHT`), `referenceId`, `status` (`PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`), timestamps.
*Search:* "state machine pattern Java enum" (helps you model status transitions cleanly instead of scattered if/else checks)

**Step 2: Build Booking Service**
Handles reserve-hotel-room and reserve-flight-seat use cases. It will call Hotel Service / Flight Service to check + hold availability.
*Search:* "Spring Boot Feign client tutorial" (Feign is how services call each other over REST cleanly — you'll use this a lot from here on)

**Step 3: Build Payment Service (mock gateway)**
No need for real Stripe/PayPal integration yet — simulate success/failure with a random or configurable outcome.
*Search:* "mock payment gateway design pattern", "idempotency key payment API" (important concept — look this up even if you don't implement it fully yet)

**Step 4: Implement concurrency handling**
Prevent two users from booking the same room/seat at the same time.
*Search:* "optimistic locking JPA @Version", "pessimistic locking vs optimistic locking database"

**Step 5: Connect Booking → Payment (synchronous first)**
Simplest version: Booking Service calls Payment Service directly via Feign, waits for the result.

**Step 6: Implement cancellation + refund rules**
Add business logic for refund windows based on your Phase 3 business rules from the earlier requirements doc.

**Step 7: Test the full happy path + failure path**
Search → Reserve → Pay → Confirm → Cancel. Also manually test: what happens if payment fails right now? (Spoiler: probably a "ghost" reservation — that's exactly the problem Week 4 solves.)

✅ **Week 3 done when:** A traveler can fully book and pay for a hotel or flight, and cancel it — even if the failure handling isn't perfect yet.

---

## WEEK 4 — Messaging & Resilience (Leveling Up)

**Step 1: Learn messaging fundamentals**
Understand pub/sub vs point-to-point messaging, and why event-driven communication reduces tight coupling between services.
*Search:* "what is Kafka simple explanation", "Kafka vs RabbitMQ for beginners" (pick one — Kafka is more common in job listings, RabbitMQ is arguably easier to learn first; either is fine for this project)

**Step 2: Set up your message broker locally**
Run Kafka or RabbitMQ via Docker Compose — don't install it natively.
*Search:* "Kafka Docker Compose setup", "Spring Boot Kafka producer consumer tutorial"

**Step 3: Convert key events to be event-driven**
Booking confirmed, payment failed — these become events published to a topic/queue instead of direct synchronous calls.
*Search:* "event-driven microservices Spring Boot example"

**Step 4: Implement the Saga pattern**
This is the core lesson of the whole project: if payment fails after a hotel room was held, you need a *compensating action* (release the room) — not just an error message.
*Search:* "Saga pattern microservices explained", "choreography vs orchestration saga" (choreography = services react to each other's events; orchestration = a central coordinator — pick whichever feels more intuitive to start with)

**Step 5: Add Circuit Breaker**
Protects your system when a service is down or slow — instead of hanging forever, it fails fast.
*Search:* "Resilience4j circuit breaker Spring Boot tutorial"

**Step 6: Add basic structured logging**
Doesn't need to be fancy — just consistent log format across services so you can trace a request through the system.
*Search:* "structured logging Spring Boot", "correlation ID microservices logging" (this concept — tagging a request with one ID across all services — will save you hours of debugging confusion)

✅ **Week 4 done when:** A failed payment no longer leaves a stuck/ghost reservation — the system cleans up after itself.

---

## WEEK 5 — Packages (Composition Layer)

**Step 1: Design the Package Service**
A package = a hotel offer + a flight offer + a combined price. Decide now: flat price or (hotel + flight − discount%)?

**Step 2: Implement parallel availability checks**
Package Service needs to ask Hotel Service AND Flight Service "is this available?" at the same time, not one after another.
*Search:* "CompletableFuture Java parallel calls", "async REST calls Spring Boot" (this is where async programming becomes genuinely useful, not just theoretical)

**Step 3: Implement combined pricing/discount logic**
Straightforward business logic — but write it as its own clean service/class so it's easy to test independently.

**Step 4: Extend the Saga pattern to two legs**
A package booking = hotel booking + flight booking. If either fails, roll back both. This directly reuses what you built in Week 4, just with one more moving part.
*Search:* "saga pattern multiple services rollback example" — helpful to see a diagram of a 2-leg saga specifically.

**Step 5: Add Admin package curation**
Simple CRUD endpoint restricted to `ADMIN` role — lets them manually create a featured package.

✅ **Week 5 done when:** A traveler can book a Package, and if either the hotel or flight leg fails, the whole booking rolls back cleanly.

---

## WEEK 6 — Engagement Features + Polish + Deployment

**Step 1: Build Review Service**
Reviews only allowed for bookings with `COMPLETED` status, tied to a specific `bookingId` — this prevents fake reviews.
*Search:* nothing new here — pure CRUD, good place to move fast since you've done this pattern several times now.

**Step 2: Build Favorites**
Simple many-to-many-style relation (user ↔ hotel/flight/package). Low complexity, good confidence booster this late in the project.

**Step 3: Build Notification Service**
Trigger on booking confirmed, payment failed, cancellation, upcoming trip reminder. Can be email (via SMTP/Mailtrap for testing) or just in-app/log-based if you want to save time.
*Search:* "Spring Boot send email tutorial", "Mailtrap Spring Boot testing" (Mailtrap is great for testing email without spamming a real inbox)

**Step 4: Dockerize every service**
One `Dockerfile` per service — this is what makes your project actually runnable by someone else (or by you, in a year).
*Search:* "Dockerfile Spring Boot Maven multi-stage build"

**Step 5: Docker Compose for local orchestration**
One `docker-compose.yml` that spins up all services + Postgres + Kafka/RabbitMQ + Redis together.
*Search:* "Docker Compose Spring Boot microservices example"

**Step 6: Add API documentation**
Swagger/OpenAPI per service — makes your project look professional and is genuinely useful for your own reference later.
*Search:* "springdoc-openapi Spring Boot 3 setup"

**Step 7: Write your README**
Architecture diagram (even a simple hand-drawn one), how to run it, what patterns you implemented and why (Saga, Circuit Breaker, etc.) — this is what you'll actually show people.

**Step 8 (Stretch goal): Deploy somewhere**
Free-tier cloud (Render, Railway, or a basic VPS) — or just present it running fully via Docker Compose if deployment eats too much time.

✅ **Week 6 done when:** The whole system runs via `docker-compose up`, is documented, and is demo-ready.

---

## General Notes
- Don't chase perfection in Weeks 1–2 — they're foundational, not the interesting part. Move once it works.
- Weeks 4–5 are intentionally the hardest — that's where you become a microservices developer instead of "someone who used Spring Boot."
- If a week's search terms feel overwhelming, that's normal — you don't need to master each concept fully before using it once. Build first, deepen understanding after it works.
