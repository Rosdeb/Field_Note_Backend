# Configuration and operations

## Database information needed
Fill `application-local.properties` or environment variables with:

| Environment variable | Value |
| --- | --- |
| DB_URL | Complete JDBC URL containing PostgreSQL host, port, database, and SSL options |
| DB_USERNAME | Database role |
| DB_PASSWORD | Database password |
| PORT | Optional HTTP port; default 8080 |

These are intentionally blank in the supplied template. No PostgreSQL superuser, cloud account, or signing secret is needed by the application. The database must already exist; Flyway creates only the schema. The current single-role setup requires DDL permissions for migrations and CRUD permissions for application tables. V2 requires the `pg_trgm` extension: the migration role needs permission to install it, or your database administrator must enable it first. A deployment can run migrations using a separate restricted migration role before starting the runtime role.

## Build and launch
`./gradlew bootJar` builds `build/libs/FieldNotes-0.0.1-SNAPSHOT.jar`. Run it using `java -jar build/libs/FieldNotes-0.0.1-SNAPSHOT.jar` from the project directory so the optional local configuration is found. Java 21 is used by the Gradle toolchain.

Application startup validates the configured datasource by applying Flyway migrations. No database connection was configured for the user's database during this delivery. Test credentials are separate and are never imported from the application environment by the disposable test helper.

## Security decisions
Authentication uses cryptographically random 256-bit opaque bearer tokens. The database stores only their SHA-256 hashes; passwords are BCrypt hashes. Token lookup joins sessions to users and checks expiry. Logout removes the current session. Tokens expire after 30 days; there is no refresh endpoint. Session requests must travel over HTTPS outside localhost. Native mobile clients should store tokens in platform secure storage and partition local account data.

Stateless request authentication is used; CSRF is disabled because authentication uses an explicit Authorization header rather than ambient cookies. All business endpoints require authentication. Queries are owner-scoped and foreign parent/photo resources return 404. Authentication does not grant access based on IDs supplied by the client. JDBC uses bound parameters. Public responses never expose owner metadata, password hashes, session digests, or SQL exceptions.

Client requests cannot set server version, timestamps, ownership, or tombstones. Allowed editable fields are validated independently of database constraints. Unknown JSON properties are rejected. Coordinates must be finite, in range, and supplied as a pair. Photos are bounded in memory at 5 MiB + 1 byte and checked against PNG/JPEG magic signatures; they are not decoded/transcoded or malware scanned. A gateway should also cap body sizes, request timeouts, and registration/login rates.

## Data retention and recovery
Account data stays in PostgreSQL. Local reinstall restoration replays all retained changes from cursor zero; photos are fetched on demand after notes are restored. Back up all application tables together. Do not remove change log entries or mutation receipts without designing cursor invalidation/bootstrap and retry retention rules. Tombstones are retained indefinitely. Photos are immutable; removal from a note only drops its reference. Unreferenced photos and expired sessions currently require a future lifecycle job.

Default note status is strictly a mobile local preference. Clearing the app resets that setting to the mobile default (recommended DRAFT); reinstall restoration only restores account data. The backend requires an explicit valid status on note creation.

## Delivery limitations
Mobile UI, BLoCs, SQLite, device permissions, and automatic connectivity-triggered sync await the Flutter phase. The backend provides the protocol they require. This is a working backend implementation, not a deployed public service. Email verification, password recovery, account deletion, rate limiting, metrics, sync log compaction, and attachment garbage collection are future production work. Global text search has trigram indexes and lists have sort/filter indexes. Very short/common searches and deep OFFSET pages can still cost more; benchmark representative data before promising response times. A batch is atomic and does not accept independent successful mutations when one conflicts; the app must resolve the failure and retry or split the batch.
