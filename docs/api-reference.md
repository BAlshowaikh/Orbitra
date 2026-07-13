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

---

## Auth Service

Base URL: `http://localhost:8081` · Source: `auth-service/` · Package: `com.orbitra.auth_service`

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

*Not yet built.*

## Hotel Service

*Not yet built.*

## Flight Service

*Not yet built.*

## Booking Service

*Not yet built.*

## Payment Service

*Not yet built.*
