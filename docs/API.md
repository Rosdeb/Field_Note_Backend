# REST contract

Base path `/api`. JSON camelCase. Authenticated endpoints require `Authorization: Bearer <token>`. Errors use RFC Problem Details with status, title, detail. Validation errors return 400; bean-validation responses include an `errors` array with field/message entries and never echo rejected values. Missing or null expectedVersion is rejected; authentication 401; unavailable/foreign/deleted resources 404; version and idempotency conflicts 409. UUIDs and enum values are validated. Lists are paginated with zero-based page and size (1–100).

## Authentication
- POST /auth/register: `{ "email": "...", "password": "..." }` → 201 session.
- POST /auth/login: same request → 200 session.
- POST /auth/refresh: `{ "refreshToken": "..." }` → 200 session (issues fresh JWT access token and rotates refresh token).
- POST /auth/logout: bearer required (or optional `{ "refreshToken": "..." }`) → 204; revokes current access token and refresh token.
- GET /auth/me → `{id,email}`.
Session: `{ userId, email, token, accessToken, refreshToken, tokenType, expiresIn, expiresAt, refreshExpiresAt }`. Password 10–72 UTF-8 bytes; email normalized lowercase. Duplicate registration returns 409. Store tokens in platform secure storage. When JWT access token expires (returns 401 Unauthorized), the client app sends the refresh token to `POST /api/auth/refresh` to obtain new tokens seamlessly without requiring re-login.


## CRUD
- GET /customers; GET /customers/{id}
- GET /sites?customerId=<optional UUID>; GET /sites/{id}
- GET /notes?q=<optional literal search>&status=<optional status>&siteId=<optional UUID>; GET /notes/{id}
- PUT /customers/{id}, /sites/{id}, /notes/{id}: upsert with client UUID and a mutation envelope `{mutationId, expectedVersion, data}`. New records use version 0; updates use returned version. Response is a mutation acknowledgment, not a bare entity.
- DELETE those same paths: JSON `{mutationId, expectedVersion}`. The Flutter client may use sync push instead of DELETE bodies for HTTP client compatibility.

Customer data: `{name,contactInformation}`. Site: `{siteName,address,customerId}`. Note: `{siteId,title,description,latitude,longitude,occurredAt,status,photoId}`. Latitude/longitude both null or both valid numbers. photoId may be null. occurredAt is ISO-8601 with UTC offset, e.g. `2026-10-08T08:00:00Z`. Update uses full replacement of editable fields. Response data includes id, version, deleted, createdAt, updatedAt and editable fields. Site reads include customerName; note reads include siteName/customerName/customerId via JOINs. These derived names are not stored in note or sync snapshots. List: `{items,page,size}`; an empty next page signals exhaustion.

## Synchronization
POST /sync/push: `{ "mutations": [{"mutationId":"UUID","entityType":"CUSTOMER|SITE|NOTE","entityId":"UUID","operation":"UPSERT|DELETE","expectedVersion":0,"data":{...}}] }`.
DELETE mutations omit/null data. Response `{results:[{mutationId,entityType,entityId,version,revision,data}]}`. Exactly the same mutation payload must be reused on retry. All mutations are committed together; a conflict returns 409 and no new mutation in that batch is committed. Previously committed replay receipts remain available.

GET /sync/changes?after=0&limit=100 returns `{changes:[{revision,entityType,entityId,data}],nextCursor,hasMore}`. `data.deleted` signals removal. Cursor belongs to the authenticated account and must not be reused after switching users. after must be >=0 and <= current server revision. Limit 1–500. Retain pending local edits while updating baseline. Changes carry canonical relation IDs, not joined display names.

## Photos
PUT /photos/{clientUuid}, Content-Type image/jpeg or image/png, raw bytes ≤5 MiB → `{id,contentType,size}`. Image magic bytes must match type. Upload before syncing a note that references photoId. Retry with same ID and bytes succeeds; different bytes returns 409. GET /photos/{id} returns authenticated binary bytes. Uploads are immutable and scoped to their owner; photos do not appear in the change log but their IDs appear in notes. Logout/session expiry requires login before photo fetch.
