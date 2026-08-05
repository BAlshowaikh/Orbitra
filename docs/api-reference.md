# API Reference

Endpoint reference for every microservice in Orbitra. One section per service — add a new `##` section here when a new service ships its first endpoint, following the same table format.

## Conventions (apply to every service below)

- **Auth header**: `Authorization: Bearer <jwt>` — required on every endpoint except those explicitly marked `Public`.
- **Roles**: `TRAVELER | PARTNER | ADMIN` (`GUEST` = unauthenticated, i.e. `Public`). A `Role` column value of `Any` means "any authenticated account, regardless of role."
- **Error shape** (all services, all non-2xx responses):
  ```json
  { "timestamp": "2026-07-12T09:38:27.99Z", "status": 404, "message": "No account with id 5" }
  ```
- **DTOs are records** — request/response bodies never expose a JPA entity directly.
- **Entry point**: external clients should call the API Gateway (`http://localhost:8080`, see its own section below) for everything — each service's own port listed below is only reachable from other containers on the Compose network now, not from outside Docker.

---

## API Gateway

Base URL: `http://localhost:8080` · Source: `orbitra-be/api-gateway/` · Package: `com.orbitra.api_gateway`

The single entry point for every service below — each one's own port (8081–8085) is closed off from outside Docker; requests must go through here. Routes to the exact same endpoints documented in the rest of this file, unchanged — the Gateway doesn't add, remove, or reshape any endpoint, only forwards.

### Routing table

| Path prefix | Forwards to |
|---|---|
| `/auth/**` | auth-service |
| `/users/**` | user-service |
| `/hotels/**` | hotel-service |
| `/room-types/**` | hotel-service |
| `/flights/**` | flight-service |
| `/seat-classes/**` | flight-service |
| `/bookings/**` | booking-service |

### Gateway-level rejections

Same error shape as every other service (see Conventions above), but these two originate from the Gateway itself, before a request ever reaches a backend service:

**Missing/invalid token on a protected route** → `401`
```json
{ "timestamp": "2026-08-03T11:17:02.63Z", "status": 401, "message": "Missing or malformed Authorization header" }
```
(or `"Invalid or expired token"` if a token was present but didn't validate)

**Rate limit exceeded** → `429`, capped per client IP (`app.rate-limit.capacity`, default 20 requests per `app.rate-limit.refill-seconds`, default 60)
```json
{ "timestamp": "2026-08-03T11:20:00.00Z", "status": 429, "message": "Rate limit exceeded - try again later" }
```

Public routes (the same ones each service's own `SecurityConfig` already marks `permitAll` — browsing/search GETs, `/auth/register`, `/auth/login`) skip the token check entirely but are still subject to the rate limiter.

---

## Auth Service

Base URL: `http://localhost:8081` · Source: `orbitra-be/auth-service/` · Package: `com.orbitra.auth_service`

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| POST | `/auth/register` | Public | `RegisterRequest` | `AuthResponse` |
| POST | `/auth/login` | Public | `LoginRequest` | `AuthResponse` |
| GET | `/auth/me` | Any | — | `MeResponse` |
| GET | `/auth/admin/accounts` | ADMIN | — (query: `page`, `size`, `sort`) | `PagedResponse<AccountSummaryResponse>` |
| PATCH | `/auth/admin/accounts/{id}/status` | ADMIN | `UpdateAccountStatusRequest` | `AccountSummaryResponse` |

### `RegisterRequest`
```json
{
  "email": "traveler@example.com",
  "password": "at-least-8-chars",
  "role": "TRAVELER",       // TRAVELER | PARTNER | ADMIN
  "partnerType": null       // HOTEL | FLIGHT — required if role=PARTNER, else omit/null
}
```
Notes:
- `role: ADMIN` is accepted only if no ADMIN account exists yet (bootstrap — creates the platform's first admin). Once one exists, this returns `400`.
- Duplicate `email` returns `409`.

### `LoginRequest`
```json
{ "email": "traveler@example.com", "password": "at-least-8-chars" }
```
A disabled account (see admin status endpoint below) gets `400 "Account is disabled"`.

### `AuthResponse` (returned by both register and login)
```json
{ "token": "<jwt>", "expiresInMs": 3600000, "role": "TRAVELER" }
```

### `MeResponse`
```json
{ "accountId": 1, "role": "TRAVELER" }
```
Identity is read straight from the JWT — no DB lookup.

### `AccountSummaryResponse` (admin views — never includes the password hash)
```json
{
  "id": 5,
  "email": "target@example.com",
  "role": "TRAVELER",
  "partnerType": null,
  "enabled": true,
  "createdAt": "2026-07-10T12:00:00Z"
}
```

### `PagedResponse<T>` (generic — reusable by any future service's list endpoints)
```json
{
  "content": [ /* array of T, e.g. AccountSummaryResponse */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```
`GET /auth/admin/accounts` accepts `?page=0&size=20&sort=email,asc` (all optional, defaults shown).

### `UpdateAccountStatusRequest`
```json
{ "enabled": false }
```
Idempotent — setting a value the account already has still returns `200`. Unknown `id` returns `404`; a non-ADMIN caller gets `403`.

---

## User Service

Base URL: `http://localhost:8082` · Source: `orbitra-be/user-service/` · Package: `com.orbitra.user_service`

Owns profile data only (name, contact info, preferences) — credentials/role/enabled status stay in Auth Service. Linked to `Account` only by sharing the same id value (no cross-database foreign key); `id` here is never auto-generated, it's always the caller's own account id from their JWT.

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| GET | `/users/profile` | Any | — | `UserProfileResponse` |
| PUT | `/users/profile` | Any | `UserProfileRequest` | `UserProfileResponse` |

### `UserProfileRequest` (request body shape — not a bound DTO, see note below)
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "phone": "+973-1234-5678",
  "address": "123 Main St, Manama, Bahrain",
  "dateOfBirth": "1995-06-12",
  "profilePhotoUrl": null
}
```
`firstName`/`lastName` required on every call. `PUT` is an upsert — the very first call creates the profile, every call after updates it, same endpoint either way.

**Partial-update semantics**: `phone`/`address`/`dateOfBirth`/`profilePhotoUrl` only change if the field is actually present in the request JSON:
- Field **omitted entirely** → existing saved value is left untouched.
- Field present with an **explicit `null`** → clears that field.
- Field present with a **real value** → updates it.

This means the request body is read as a raw JSON body (not bound to a validated DTO), specifically so "I didn't send this field" and "I sent `null`" can be told apart — a plain object/record can't distinguish those, since both become the same Java `null`.

### `UserProfileResponse`
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "phone": "+973-1234-5678",
  "address": "123 Main St, Manama, Bahrain",
  "dateOfBirth": "1995-06-12",
  "profilePhotoUrl": null
}
```
No `id`/timestamps — this is always the caller's own profile, identified by their JWT, never by an id in the request/response body.

Notes:
- `GET /users/profile` returns `404` until the caller has PUT a profile at least once — there's no proactive creation at registration (see project decision below).
- Validates JWTs with the same shared secret Auth Service signs with, but has no access to the `accounts` table — a deactivated account's still-valid token keeps working here until it naturally expires (resolved once JWT validation centralizes at the API Gateway, Week 2).

## Hotel Service

Base URL: `http://localhost:8083` · Source: `orbitra-be/hotel-service/` · Package: `com.orbitra.hotel_service`

Entities: `Hotel` (owned by a PARTNER account via `ownerId`, a partner may own many) → `Room` (a hotel's own priced/staffed instance of a `RoomType`) → `Availability` (per-room, per-date override; a missing row means "fully available," i.e. `COALESCE(row's availableCount, room.totalInventory)`). `RoomType` is a separate, admin-managed global catalog (e.g. "Deluxe King") partners pick from by id when adding a `Room`.

**Role column notes specific to this service:**
- `PARTNER_HOTEL` = `PARTNER` role **and** `partnerType: HOTEL` on the JWT (a `PARTNER_FLIGHT` account is rejected).
- `(owner)` = ownership is checked in the service layer (JWT `sub` vs. `Hotel.ownerId`), not just role — a `PARTNER_HOTEL` token for a *different* hotel still gets `403`.
- `Public (+owner)` = anyone can call it, but the owning partner's token unlocks extra data (inactive rows) the response otherwise omits.
- `TRAVELER` (reserve/release only) = **not** an ownership check — any authenticated traveler can call these, since they're reserving inventory for themselves, not managing someone else's listing. Called by Booking Service, which forwards the original traveler's own JWT rather than using any special service-to-service credential.

### Hotels

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| GET | `/hotels` | Public | — (query: `city`, `checkIn`, `checkOut`, `guests`, `minPrice`, `maxPrice`, `page`, `size`, all optional) | `PagedResponse<HotelSearchResultResponse>` |
| GET | `/hotels/mine` | PARTNER_HOTEL | — (query: `page`, `size`) | `PagedResponse<HotelResponse>` |
| GET | `/hotels/{id}` | Public | — | `HotelDetailResponse` |
| POST | `/hotels` | PARTNER_HOTEL | `HotelRequest` | `HotelResponse` |
| PATCH | `/hotels/{id}` | PARTNER_HOTEL (owner) | Partial JSON (see below) | `HotelResponse` |
| PATCH | `/hotels/{id}/status` | PARTNER_HOTEL (owner) or ADMIN | `UpdateActiveRequest` | `HotelResponse` |

### Rooms (nested under a hotel)

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| GET | `/hotels/{hotelId}/rooms` | Public (+owner) | — | `List<RoomResponse>` |
| POST | `/hotels/{hotelId}/rooms` | PARTNER_HOTEL (owner) | `RoomRequest` | `RoomResponse` |
| PATCH | `/hotels/{hotelId}/rooms/{roomId}` | PARTNER_HOTEL (owner) | Partial JSON (see below) | `RoomResponse` |
| PATCH | `/hotels/{hotelId}/rooms/{roomId}/status` | PARTNER_HOTEL (owner) | `UpdateActiveRequest` | `RoomResponse` |
| GET | `/hotels/{hotelId}/rooms/{roomId}/availability` | PARTNER_HOTEL (owner) | — (query, required: `startDate`, `endDate`) | `List<AvailabilityResponse>` |
| PUT | `/hotels/{hotelId}/rooms/{roomId}/availability` | PARTNER_HOTEL (owner) | `AvailabilityRangeRequest` | `List<AvailabilityResponse>` |
| POST | `/hotels/{hotelId}/rooms/{roomId}/reserve` | TRAVELER | `ReserveRoomRequest` | `ReserveRoomResponse` |
| POST | `/hotels/{hotelId}/rooms/{roomId}/release` | TRAVELER | `ReserveRoomRequest` | `List<AvailabilityResponse>` |

### Room Types (admin catalog)

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| GET | `/room-types` | Public (+admin) | — | `List<RoomTypeResponse>` |
| POST | `/room-types` | ADMIN | `RoomTypeRequest` | `RoomTypeResponse` |
| PATCH | `/room-types/{id}` | ADMIN | Partial JSON (see below) | `RoomTypeResponse` |
| PATCH | `/room-types/{id}/status` | ADMIN | `UpdateActiveRequest` | `RoomTypeResponse` |

### `HotelRequest` (create only — `POST /hotels`)
```json
{
  "name": "Grand Plaza Hotel",
  "description": "A luxury hotel in the city center",
  "address": "123 Main St",
  "city": "Dubai",
  "country": "UAE",
  "amenities": ["pool", "gym", "free wifi"]
}
```

### `HotelResponse`
```json
{
  "id": 1,
  "ownerId": 7,
  "name": "Grand Plaza Hotel",
  "description": "A luxury hotel in the city center",
  "address": "123 Main St",
  "city": "Dubai",
  "country": "UAE",
  "amenities": ["pool", "gym", "free wifi"],
  "active": true,
  "createdAt": "2026-07-20T10:00:00Z"
}
```

### `HotelSearchResultResponse` (thinner than `HotelResponse` — one card per search result)
```json
{ "id": 1, "name": "Grand Plaza Hotel", "city": "Dubai", "country": "UAE", "minNightlyPrice": 150.00 }
```
`minNightlyPrice` is the cheapest active `Room`'s price at that hotel, `null` if it has no active rooms yet.

### `HotelDetailResponse`
```json
{
  "id": 1,
  "name": "Grand Plaza Hotel",
  "description": "A luxury hotel in the city center",
  "address": "123 Main St",
  "city": "Dubai",
  "country": "UAE",
  "amenities": ["pool", "gym", "free wifi"],
  "rooms": [ /* array of RoomResponse, active only */ ]
}
```

### `RoomRequest` (create only — `POST /hotels/{hotelId}/rooms`)
```json
{
  "roomTypeId": 1,
  "capacity": 2,
  "basePricePerNight": 150.00,
  "totalInventory": 10,
  "facilities": ["WiFi", "TV", "Balcony"]
}
```
`roomTypeId` must reference an active `RoomType`; a hotel can only have one `Room` per `RoomType` (`409` on a duplicate).

### `RoomResponse`
```json
{
  "id": 5,
  "hotelId": 1,
  "roomTypeId": 1,
  "roomTypeName": "Deluxe King",
  "capacity": 2,
  "basePricePerNight": 150.00,
  "totalInventory": 10,
  "facilities": ["WiFi", "TV", "Balcony"],
  "active": true
}
```

### `RoomTypeRequest` (create only — `POST /room-types`)
```json
{ "name": "Deluxe King", "description": "A spacious room with a king-size bed" }
```
`name` must be unique (`409` on a duplicate).

### `RoomTypeResponse`
```json
{ "id": 1, "name": "Deluxe King", "description": "A spacious room with a king-size bed", "active": true }
```

### `AvailabilityRangeRequest` (`PUT .../availability`)
```json
{ "startDate": "2026-08-01", "endDate": "2026-08-07", "availableCount": 5 }
```
Upserts one `Availability` row per date in `[startDate, endDate]` with the given count — full-replace, not merge-patch (every field is required, so there's no omitted-field ambiguity). Range capped at 366 days per request.

### `AvailabilityResponse`
```json
{ "date": "2026-08-01", "availableCount": 5 }
```
The count already has the row-absence-means-default-available fallback applied — callers never need to know whether a given date had an explicit override row.

### `ReserveRoomRequest` (`POST .../reserve` and `.../release`)
```json
{ "checkInDate": "2026-08-01", "checkOutDate": "2026-08-04" }
```
`checkOutDate` is exclusive, same semantics as `GET /hotels`' search filters — this reserves/releases every night in `[checkInDate, checkOutDate)`. `reserve` is all-or-nothing across the whole stay: if even one night in the range has no availability left, the entire call fails with `409` and nothing is decremented (checked under a lock on the parent `Room`, so a concurrent reserve/release for the same room can't interleave mid-check). `release` reverses it, capped so it can never push a night's count above the room's `totalInventory`.

### `ReserveRoomResponse` (`POST .../reserve` only — `release` still returns `List<AvailabilityResponse>`)
```json
{
  "basePricePerNight": 150.00,
  "nights": [
    { "date": "2026-08-01", "availableCount": 4 },
    { "date": "2026-08-02", "availableCount": 4 },
    { "date": "2026-08-03", "availableCount": 4 }
  ]
}
```
`basePricePerNight` is included so the caller (Booking Service) can compute a total price without a second call back to this service — `release` doesn't need it, since nothing is being priced there.

### `UpdateActiveRequest` (shared by every `.../status` endpoint in this service)
```json
{ "active": false }
```

**Partial-update semantics** (`PATCH /hotels/{id}`, `PATCH .../rooms/{roomId}`, `PATCH /room-types/{id}`): every field is optional and only changes if present in the request JSON — omitted means "leave unchanged," same convention as `user-service`'s `PUT /users/profile`. Unlike `user-service`, there's no field that's *always* required on every call — string fields are still validated non-blank if you do send them. Example: updating just a hotel's address —
```json
{ "address": "456 New St" }
```
The plain create-time DTOs (`HotelRequest`/`RoomRequest`/`RoomTypeRequest`) are not bound on these `PATCH` routes; the body is read as a raw JSON object instead.

## Flight Service

Base URL: `http://localhost:8084` · Source: `orbitra-be/flight-service/` · Package: `com.orbitra.flight_service`

Entities: `Flight` (owned by a PARTNER account via `ownerId`, a partner may own many — **a single dated departure, not a recurring schedule**: same route on a different day is a different `Flight` row with its own unique `flightNumber`) → `FlightSeat` (a flight's own priced instance of a `SeatClass`, with `totalInventory` fixed by the partner and `availableCount` decremented only by the `reserve` endpoint below, called by Booking Service — never partner-editable directly). `SeatClass` is a separate, admin-managed global catalog (e.g. "Business") partners pick from by id when adding a `FlightSeat`. No `Availability`/date-range table, unlike Hotel Service — a `Flight` is already pinned to one date, so there's no calendar to override. See `docs/architecture&logic.md` for the full design rationale.

**Role column notes specific to this service:**
- `PARTNER_FLIGHT` = `PARTNER` role **and** `partnerType: FLIGHT` on the JWT (a `PARTNER_HOTEL` account is rejected).
- `(owner)` = ownership is checked in the service layer (JWT `sub` vs. `Flight.ownerId`), not just role — a `PARTNER_FLIGHT` token for a *different* flight still gets `403`.
- `Public (+owner)` = anyone can call it, but the owning partner's token unlocks extra data (inactive rows) the response otherwise omits.
- `TRAVELER` (reserve/release only) = **not** an ownership check — any authenticated traveler can call these. Called by Booking Service, which forwards the original traveler's own JWT rather than using any special service-to-service credential.

### Flights

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| GET | `/flights` | Public | — (query: `originCode`, `destinationCode`, `travelDate`, `passengers`, `seatClassName`, `minPrice`, `maxPrice`, `page`, `size`, all optional) | `PagedResponse<FlightSearchResultResponse>` |
| GET | `/flights/mine` | PARTNER_FLIGHT | — (query: `page`, `size`) | `PagedResponse<FlightResponse>` |
| GET | `/flights/{id}` | Public | — | `FlightDetailResponse` |
| POST | `/flights` | PARTNER_FLIGHT | `FlightRequest` | `FlightResponse` |
| PATCH | `/flights/{id}` | PARTNER_FLIGHT (owner) | Partial JSON (see below) | `FlightResponse` |
| PATCH | `/flights/{id}/status` | PARTNER_FLIGHT (owner) or ADMIN | `UpdateActiveRequest` | `FlightResponse` |

### Flight Seats (nested under a flight)

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| GET | `/flights/{flightId}/seats` | Public (+owner) | — | `List<FlightSeatResponse>` |
| POST | `/flights/{flightId}/seats` | PARTNER_FLIGHT (owner) | `FlightSeatRequest` | `FlightSeatResponse` |
| PATCH | `/flights/{flightId}/seats/{seatId}` | PARTNER_FLIGHT (owner) | Partial JSON (see below) | `FlightSeatResponse` |
| PATCH | `/flights/{flightId}/seats/{seatId}/status` | PARTNER_FLIGHT (owner) | `UpdateActiveRequest` | `FlightSeatResponse` |
| POST | `/flights/{flightId}/seats/{seatId}/reserve` | TRAVELER | — | `FlightSeatResponse` |
| POST | `/flights/{flightId}/seats/{seatId}/release` | TRAVELER | — | `FlightSeatResponse` |

No request body on `reserve`/`release` — there's only one seat to act on, identified entirely by the path. `reserve` atomically decrements `availableCount` by one and returns `409` if it was already `0`; `release` reverses it, capped so it can never push `availableCount` above `totalInventory`. Both are a single guarded `UPDATE` (`availableCount > 0` / `< totalInventory`), which is enough for concurrency safety here — no explicit locking needed, unlike Hotel Service's date-range version of the same idea.

No availability-range endpoint here, unlike Hotel Service's `PUT .../rooms/{roomId}/availability` — `availableCount` only ever changes via `reserve`/`release` above, never a partner-submitted date range.

### Seat Classes (admin catalog)

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| GET | `/seat-classes` | Public (+admin) | — | `List<SeatClassResponse>` |
| POST | `/seat-classes` | ADMIN | `SeatClassRequest` | `SeatClassResponse` |
| PATCH | `/seat-classes/{id}` | ADMIN | Partial JSON (see below) | `SeatClassResponse` |
| PATCH | `/seat-classes/{id}/status` | ADMIN | `UpdateActiveRequest` | `SeatClassResponse` |

### `FlightRequest` (create only — `POST /flights`)
```json
{
  "flightNumber": "AA100-20260805",
  "originCode": "JFK",
  "destinationCode": "LAX",
  "departureTime": "2026-08-05T08:00:00",
  "arrivalTime": "2026-08-05T11:00:00",
  "durationMinutes": 360,
  "seatCount": 150
}
```
`flightNumber` must be unique (`409` on a duplicate) — since a `Flight` is one specific dated departure, the same route on a different day needs a different number. `seatCount` is the aircraft's total physical capacity; the sum of every linked `FlightSeat.totalInventory` must never exceed it (`400` if a create/update would push it over).

### `FlightResponse`
```json
{
  "id": 1,
  "ownerId": 7,
  "flightNumber": "AA100-20260805",
  "originCode": "JFK",
  "destinationCode": "LAX",
  "departureTime": "2026-08-05T08:00:00",
  "arrivalTime": "2026-08-05T11:00:00",
  "durationMinutes": 360,
  "seatCount": 150,
  "active": true,
  "createdAt": "2026-07-28T10:00:00Z"
}
```

### `FlightSearchResultResponse` (thinner than `FlightResponse` — one card per search result)
```json
{
  "id": 1,
  "flightNumber": "AA100-20260805",
  "originCode": "JFK",
  "destinationCode": "LAX",
  "departureTime": "2026-08-05T08:00:00",
  "arrivalTime": "2026-08-05T11:00:00",
  "minPrice": 89.99
}
```
`minPrice` is the cheapest active `FlightSeat`'s price on that flight, `null` if it has no active seats yet. `travelDate` in search filters by a single day (a `Flight` is already one specific dated departure), not a check-in/check-out range like Hotel Service.

### `FlightDetailResponse`
```json
{
  "id": 1,
  "flightNumber": "AA100-20260805",
  "originCode": "JFK",
  "destinationCode": "LAX",
  "departureTime": "2026-08-05T08:00:00",
  "arrivalTime": "2026-08-05T11:00:00",
  "durationMinutes": 360,
  "seats": [ /* array of FlightSeatResponse, active only */ ]
}
```
No `amenities` field, unlike `HotelDetailResponse` — this service has no flight-level amenities table (see architecture doc).

### `FlightSeatRequest` (create only — `POST /flights/{flightId}/seats`)
```json
{
  "seatClassId": 1,
  "basePricePerSeat": 89.99,
  "totalInventory": 30,
  "facilities": ["extra legroom", "meal included"]
}
```
`seatClassId` must reference an active `SeatClass`; a flight can only have one `FlightSeat` per `SeatClass` (`409` on a duplicate). No `capacity` field, unlike `RoomRequest` — a seat always holds exactly one passenger.

### `FlightSeatResponse`
```json
{
  "id": 5,
  "flightId": 1,
  "seatClassId": 1,
  "seatClassName": "Business",
  "basePricePerSeat": 89.99,
  "totalInventory": 30,
  "availableCount": 30,
  "facilities": ["extra legroom", "meal included"],
  "active": true
}
```
`availableCount` starts equal to `totalInventory` and is booking-driven only from then on — there's no field on `FlightSeatRequest`/the partial-update body to set it directly.

### `SeatClassRequest` (create only — `POST /seat-classes`)
```json
{ "name": "Business", "description": "Wider seats, priority boarding" }
```
`name` must be unique (`409` on a duplicate).

### `SeatClassResponse`
```json
{ "id": 1, "name": "Business", "description": "Wider seats, priority boarding", "active": true }
```

### `UpdateActiveRequest` (shared by every `.../status` endpoint in this service)
```json
{ "active": false }
```

**Partial-update semantics** (`PATCH /flights/{id}`, `PATCH .../seats/{seatId}`, `PATCH /seat-classes/{id}`): every field is optional and only changes if present in the request JSON — omitted means "leave unchanged," same convention as Hotel Service. Example: updating just a flight's seat count —
```json
{ "seatCount": 180 }
```
`seatCount` cannot be lowered below what's already allocated across the flight's seat classes (`400`). The plain create-time DTOs (`FlightRequest`/`FlightSeatRequest`/`SeatClassRequest`) are not bound on these `PATCH` routes; the body is read as a raw JSON object instead.

## Booking Service

Base URL: `http://localhost:8085` · Source: `orbitra-be/booking-service/` · Package: `com.orbitra.booking_service`

Entities: `Booking` (abstract, JOINED JPA inheritance — shared parent table holding `travelerId`, `status`, `totalPrice`, `createdAt`) → `HotelBooking`/`FlightBooking` (concrete extension tables holding only their own type-specific fields, no nulls either way). `totalPrice` is snapshotted once at creation (from Hotel/Flight Service's `reserve` response) and never recomputed — a partner changing their price later doesn't retroactively change what a past booking shows. `status` only ever moves `PENDING` → `CANCELLED` for now; `COMPLETED` and a payment-driven `CONFIRMED` transition wait for Payment Service (Phase 4).

**This is the first service in the project that calls other services synchronously** — creating or cancelling a booking calls Hotel Service's or Flight Service's `reserve`/`release` endpoints internally, forwarding the caller's own JWT unchanged (see `docs/inter-service-http-calls.md` for the general pattern, `CLAUDE.md`/`docs/architecture&logic.md` for this project's specifics). Practical consequence: if Hotel Service or Flight Service is down or unreachable when you call this service, you'll get a `503`, not a `500` — see `InventoryServiceUnavailableException` below.

**Role column notes specific to this service:**
- Every single endpoint requires `TRAVELER` — unlike every other service so far, there's no public/partner/admin route mix here at all (`SecurityConfig` is `.anyRequest().hasRole("TRAVELER")`).
- `(owner)` = a booking's ownership check is baked directly into the lookup query (`findByIdAndTravelerId`) — a booking that exists but belongs to someone else returns the exact same `404` as one that doesn't exist at all, so probing other travelers' booking ids can't distinguish the two cases.

### Bookings

| Method | Path | Role | Request body | Response body |
|---|---|---|---|---|
| POST | `/bookings/hotel-rooms` | TRAVELER | `HotelBookingRequest` | `HotelBookingResponse` |
| POST | `/bookings/flight-seats` | TRAVELER | `FlightBookingRequest` | `FlightBookingResponse` |
| PATCH | `/bookings/{id}/cancel` | TRAVELER (owner) | — | — (`204 No Content`) |
| GET | `/bookings/mine` | TRAVELER | — (query: `type` optional — `HOTEL`/`FLIGHT`, omit for both mixed together; `page`, `size`) | `PagedResponse<BookingResponse>` |

### `HotelBookingRequest` (`POST /bookings/hotel-rooms`)
```json
{ "hotelId": 1, "roomId": 5, "checkInDate": "2026-08-01", "checkOutDate": "2026-08-04" }
```
`checkOutDate` is exclusive, same semantics as Hotel Service's own `ReserveRoomRequest`. Calls `POST /hotels/{hotelId}/rooms/{roomId}/reserve` on Hotel Service before saving anything locally — a `409` there (no availability for one of the requested nights) surfaces here as `409` too (`InsufficientAvailabilityException`), and nothing gets saved.

### `FlightBookingRequest` (`POST /bookings/flight-seats`)
```json
{ "flightId": 1, "flightSeatId": 5 }
```
Calls `POST /flights/{flightId}/seats/{seatId}/reserve` on Flight Service the same way — `409` there means the seat is sold out.

### `HotelBookingResponse`
```json
{
  "id": 1,
  "travelerId": 7,
  "status": "PENDING",
  "totalPrice": 450.00,
  "hotelId": 1,
  "roomId": 5,
  "checkInDate": "2026-08-01",
  "checkOutDate": "2026-08-04",
  "createdAt": "2026-07-30T10:00:00Z"
}
```
`totalPrice` = Hotel Service's `basePricePerNight` × number of nights, computed here, not on Hotel Service.

### `FlightBookingResponse`
```json
{
  "id": 2,
  "travelerId": 7,
  "status": "PENDING",
  "totalPrice": 89.99,
  "flightId": 1,
  "flightSeatId": 5,
  "createdAt": "2026-07-30T10:05:00Z"
}
```
`totalPrice` = Flight Service's `basePricePerSeat` directly (a `FlightBooking` is always exactly one seat, no multiplication).

### `BookingResponse` (`GET /bookings/mine` only — sealed, mixes both shapes above)
```json
{
  "content": [
    { "type": "HOTEL", "id": 1, "travelerId": 7, "status": "PENDING", "totalPrice": 450.00, "hotelId": 1, "roomId": 5, "checkInDate": "2026-08-01", "checkOutDate": "2026-08-04", "createdAt": "2026-07-30T10:00:00Z" },
    { "type": "FLIGHT", "id": 2, "travelerId": 7, "status": "PENDING", "totalPrice": 89.99, "flightId": 1, "flightSeatId": 5, "createdAt": "2026-07-30T10:05:00Z" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```
`"type"` is a Jackson-added discriminator (`@JsonTypeInfo`/`@JsonSubTypes`), not a field either DTO declares itself — each item only ever has its own real fields, no cross-type nulls. One query, sorted together, when `?type=` is omitted; add `?type=HOTEL` or `?type=FLIGHT` to narrow to one kind only (reuses the same per-type query either standalone endpoint would have used).

### Cancellation semantics (`PATCH /bookings/{id}/cancel`)
Only a `PENDING` booking can be cancelled (`400` otherwise, `InvalidBookingStateException`). Calls Hotel/Flight Service's `release` endpoint first, and only marks the booking `CANCELLED` locally once that succeeds — **known gap**: if `release` succeeds but the local status update then fails, the booking is stuck `PENDING` while its inventory has already been given back elsewhere. This is a real distributed-consistency issue, intentionally not solved here — it's what this project's Saga/compensating-transaction work (Phase 4) eventually closes.

## Payment Service

*Not yet built.*
