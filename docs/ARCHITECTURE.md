# Field Notes — architecture and implementation plan

## Scope
Build a Flutter mobile client and Java Spring Boot REST backend backed by PostgreSQL. This delivery implements the backend; the mobile design below is its integration contract. No seed accounts, customers, sites, or notes are inserted. Registration/login require connectivity. CRUD after a prior login must remain available offline.

## Components
Mobile: presentation widgets → BLoCs → repositories → SQLite (source of truth) and REST sync coordinator. Use flutter_bloc, a SQLite library, secure token storage, image_picker, geolocator, and a connectivity signal. Connectivity is only a hint: real HTTP failures determine availability. Default note status belongs to local preferences, never backend data.

Backend: REST controllers → transactional services → JDBC repositories → normalized PostgreSQL tables. Spring Security authenticates HMAC-SHA256 signed JWT bearer tokens. BCrypt hashes passwords; SHA-256 digests of long-lived refresh tokens and revoked access tokens are persisted. Short-lived access tokens expire automatically; client apps refresh them using the refresh token endpoint (`POST /api/auth/refresh`) with refresh token rotation. Sessions can be revoked by logout. TLS is required outside local development. Database credentials come from environment variables.

## Relationships and isolation
users 1:N customers; customers 1:N sites; sites 1:N field_notes. Every business row also has an owner_id for efficient isolation and composite foreign keys enforce the same owner across relationships. Names, addresses, and contact details live only on their owning row. Joined queries expose site/customer names in note views. Clients never supply the authoritative owner. UUIDs are client generated so children can refer to parents offline. Optional photos are immutable owned binary resources referenced by photo_id.

Statuses: DRAFT, OPEN, IN_PROGRESS, COMPLETED. Location is an optional latitude/longitude pair. occurredAt records visit time; updatedAt is server time. All timestamps are UTC instants. Contact information is a free-text field. Each row has version, deleted, created_at, updated_at. Deletion creates tombstones; deleting a customer deletes its sites and notes in the same transaction; deleting a site deletes its notes. Tombstones cannot be resurrected. Use new UUIDs to recreate deleted records.

## Offline sync protocol
1. In a SQLite transaction, update the local entity and enqueue an immutable mutation with a UUID mutationId and expectedVersion. Creation uses expectedVersion=0. Show local data immediately.
2. Serialize sync per account. Upload pending photo bytes first using the photo UUID. Push customers before sites before notes; do child changes before parent deletions. A request supports up to 100 mutations and is atomic: one failure rolls back everything. Prefer small related batches.
3. Server serializes writes per owner using a locked user row. Updates require the current version. A successful mutation increments entity version and appends an immutable snapshot to an owner-specific change log with a monotonically increasing revision. Cascading deletes append snapshots for all affected rows. Revision allocation is transactional, avoiding sequence/commit ordering gaps.
4. Successful mutation receipts retain a SHA-256 digest of the request and the original response. Retrying the same mutationId and payload returns the same acknowledgment; reusing an ID with different data is a 409. Thus an interrupted HTTP response does not duplicate work. CRUD writes use the same service and mutation IDs.
5. Pull GET /api/sync/changes?after=0&limit=100. Read responses in revision order, apply records and nextCursor in one SQLite transaction. Follow pages until hasMore=false; concurrent new changes are picked up on the next request. Pages are bounded to a server revision observed at request start.
6. Preserve queued edits when applying server snapshots; keep a separate server baseline. On 409, pull latest state and surface a conflict. User can discard the local edit or merge and submit with a NEW mutationId and latest expectedVersion. Deleted parents require cancelling/reparenting local children. Never silently overwrite pending edits.
7. On reinstall, login and replay from cursor 0 into an empty SQLite database. This restores the latest state, including deletions, from the retained log. Initial hydration can require multiple pages. Log and receipts are retained indefinitely in this initial implementation; future compaction must introduce a snapshot/bootstrap contract before pruning.

Multiple queued edits to one entity require coalescing unsent changes or assigning the next expectedVersion only after the previous acknowledgment. Never mutate a request that may have reached the server. Separate local stores/queues by user; clear visible state at logout. Offline access relies on an already unlocked local account; an expired token pauses sync but need not block local editing.

## Mobile screens and permissions
Authentication → customer list/details → site list/details → note list/editor. Notes also have a global search/filter view; search title, description, related site, and related customer. BLoCs: Auth, Customers, Sites, Notes, Sync, Settings. Display pending/syncing/conflict states and retry actions.

Editor defaults status from persisted Settings preferences, allows override, includes title, description, date/time, location, and optional photo. Request camera permission only on camera use and location permission only on location capture. Explain denial, permit manual coordinates/no location, and link system settings for permanently denied access. Use the platform photo picker where available; handle restricted/limited gallery access. Copy selected photos to app-owned files before queuing uploads. Settings status survives restart and is not synced. Photo download is authenticated and cached locally.

## Delivery plan and acceptance
1. Document schema, APIs, sync decisions, and operational setup.
2. Add migrations/configuration and authentication/data isolation.
3. Implement CRUD, relation validation, joined search/filter, photo resources.
4. Implement versioned push, replay receipts, cascade tombstones, paginated pull.
5. Test registration/login/logout, isolation, constraints, conflicts, retry, atomic rollback, cascade deletion, search, restore, photo ownership, and validation on PostgreSQL.
6. Integrate Flutter later against this contract; test offline create/edit/delete, reconnect, process restart, reinstall, permissions, and local default status.

## Operational boundaries
Back up PostgreSQL including photo bytes. No server seed data. Photos are capped at 5 MiB and stored in PostgreSQL for a simple fully backed-up deployment; immutable unreferenced uploads remain until a future safe garbage collector is added. Sessions and receipts currently have no scheduled cleanup. Add gateway rate limiting, monitoring, HTTPS, password recovery/email verification, and log/attachment lifecycle policies before a public production launch. CORS is unnecessary for native mobile and is not enabled. Backend cannot enforce camera/location permissions; Flutter must implement them. Deployment and the mobile application are outside this implementation phase.

Official references checked for the existing stack: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Security bearer authentication](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html).
