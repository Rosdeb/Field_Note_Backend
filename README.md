# Field Notes backend

Spring Boot 4.1.1 / Java 21 REST backend for a future Flutter field-work application. PostgreSQL is the only application database. No seed data is loaded.

Implemented: online registration/login/logout, per-user data isolation, customer/site/note CRUD, relational search and filtering, optional photos, optimistic version checks, idempotent atomic sync pushes, cascade deletion tombstones, and paginated change-log replay for data restoration.

The Flutter app is not implemented in this phase. Its BLoC/SQLite architecture, permissions, local settings, and offline workflow are documented for integration.

## Documentation

- [Architecture and delivery plan](docs/ARCHITECTURE.md)
- [Database design](docs/DATABASE.md)
- [REST API and sync contract](docs/API.md)
- [Setup, security, and operations](docs/OPERATIONS.md)
- [PostgreSQL schema migration](src/main/resources/db/migration/V1__initial_schema.sql)

## Start locally

Prerequisites: Java 21 and an existing empty PostgreSQL database (PostgreSQL 16 tested). Gradle wrapper is included.

A blank, gitignored `application-local.properties` is already provided in this workspace. For a fresh checkout, create it from the template:

```sh
cp application-local.properties.example application-local.properties
```

Fill the three blank properties in `application-local.properties`:

```properties
spring.datasource.url=jdbc:postgresql://YOUR_HOST:5432/YOUR_DATABASE
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

Use `?sslmode=require` in the URL when your provider requires SSL, or `verify-full` with the provider's certificate configuration for identity verification. This local file is gitignored. Alternatively set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the launching process environment and omit the local file.

```sh
./gradlew bootRun
```

Flyway creates the schema on startup. This requires a database role allowed to create tables/indexes. With blank credentials, startup deliberately fails instead of silently using another database. API listens on port 8080 by default (`PORT` overrides it).

Register through `POST /api/auth/register`, then use the returned bearer token. Create records with client UUIDs and `expectedVersion: 0`; send returned versions when editing/deleting. See the API document for request bodies.

## Verification

```sh
./gradlew test
./gradlew bootJar
```

Without `TEST_DB_URL`, database integration tests are explicitly skipped; validation unit tests still run. To run the full suite on a dedicated disposable database, set `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`. **Integration tests create tables and test records: never point them at your application database.**

On this machine PostgreSQL 16 is installed through Homebrew. This helper starts a fresh temporary instance, runs the suite, stops it, and removes all test data:

```sh
./scripts/test-local-postgres.sh
```

Override `PG_BIN` for another PostgreSQL installation and `TEST_PORT` if 55439 is occupied. The helper requires PostgreSQL binaries and permission to listen on localhost. Test coverage includes real PostgreSQL migrations/queries, auth/revocation, owner isolation, idempotency, conflicts, transaction rollback, concurrent edits, joined search, cascade tombstones, paginated restoration, photo access, required-version validation, literal search escaping, and PostgreSQL index query plans.

## Swagger UI

After configuring PostgreSQL and starting with `./gradlew bootRun`, open
[Swagger UI](http://localhost:8080/swagger-ui/index.html). The OpenAPI JSON is at
`http://localhost:8080/v3/api-docs`.

Use **Try it out** on `/api/auth/register` or `/api/auth/login`, copy the returned
`token`, click **Authorize**, and paste only the token (without `Bearer `).
Protected requests will then include the bearer header. Documentation is publicly
readable; business endpoints still require authentication. Swagger UI runs inside
the backend, so the PostgreSQL connection must work before it can load.
