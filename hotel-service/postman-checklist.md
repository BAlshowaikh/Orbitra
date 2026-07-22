# Hotel Service — Postman Testing Checklist

Base URL: `http://localhost:8083`

Two separate top-level paths — `/room-types` is **not** nested under `/hotels`.

All endpoints except the ones in **Shared** need `Authorization: Bearer <token>` from the matching role's login (via `auth-service`, `http://localhost:8081`).

## Shared (no role, or dual-role)

**1. `GET /hotels`** — search, public, no body
Query params (all optional): `city`, `checkIn`, `checkOut` (ISO dates, must be given together), `guests`, `minPrice`, `maxPrice`, `page`, `size`

**2. `GET /hotels/{id}`** — detail view, public, no body

**3. `GET /room-types`** — catalog list, public, no body. Send an ADMIN token to also see inactive entries; anyone else only sees active ones.

**4. `GET /hotels/{hotelId}/rooms`** — list a hotel's rooms, public, no body. Send the owning partner's token to also see inactive rooms; anyone else only sees active ones.

**5. `PATCH /hotels/{id}/status`** — owning partner OR admin (ownership check is skipped for admin)
```json
{ "active": false }
```

## Admin only

Do these before the partner section — a `Room` can't be created without a `RoomType` to reference.

**6. `POST /room-types`**
```json
{
  "name": "Deluxe King",
  "description": "A spacious room with a king-size bed"
}
```

**7. `PATCH /room-types/{id}`** — partial update, send only what's changing
```json
{ "description": "Updated description" }
```

**8. `PATCH /room-types/{id}/status`**
```json
{ "active": false }
```

## Partner (`PARTNER_HOTEL`)

**9. `POST /hotels`**
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

**10. `GET /hotels/mine`** — no body. Query params: `page`, `size`

**11. `PATCH /hotels/{id}`** — partial update, send only what's changing
```json
{ "address": "456 New St" }
```

**12. `POST /hotels/{hotelId}/rooms`**
```json
{
  "roomTypeId": 1,
  "capacity": 2,
  "basePricePerNight": 150.00,
  "totalInventory": 10,
  "facilities": ["WiFi", "TV", "Balcony"]
}
```

**13. `PATCH /hotels/{hotelId}/rooms/{roomId}`** — partial update
```json
{ "basePricePerNight": 175.00 }
```

**14. `PATCH /hotels/{hotelId}/rooms/{roomId}/status`**
```json
{ "active": false }
```

**15. `GET /hotels/{hotelId}/rooms/{roomId}/availability`** — no body
Query params (required): `startDate`, `endDate` (e.g. `?startDate=2026-08-01&endDate=2026-08-07`)

**16. `PUT /hotels/{hotelId}/rooms/{roomId}/availability`**
```json
{
  "startDate": "2026-08-01",
  "endDate": "2026-08-07",
  "availableCount": 5
}
```
