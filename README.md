# Field Notes Backend

> Production-grade Spring Boot 4.1.1 / Java 21 REST backend and offline-sync engine for field-work data collection.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Enabled-red.svg)](https://flywaydb.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Swagger%20UI-green.svg)](http://localhost:8080/swagger-ui/index.html)

---

## 📖 Overview

**Field Notes Backend** provides a resilient, secure, and offline-first backend service designed for field-work mobile applications (such as a Flutter client). It supports strict multi-user data isolation, optimistic concurrency control, transactional sync push/pull replay, and customer/site/note management with binary photo uploads.

PostgreSQL is the sole application database; schema migrations are automatically managed via Flyway. No seed data is loaded.

> **Note on Mobile Client:** The Flutter companion app is designed with a BLoC/SQLite offline-first architecture; its integration contracts, permissions, local settings, and sync workflows are fully documented in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## ✨ Key Features

- **Authentication & Security**:
  - Secure registration, login, token refresh, and logout with JWT access tokens and long-lived refresh tokens.
  - Strict per-user data isolation at the query level.
- **Relational Data Management**:
  - Full CRUD for **Customers**, **Sites**, and **Notes** with client-generated UUIDs.
  - Relational filtering and literal search escaping.
  - Binary photo uploads (JPEG/PNG up to 5 MiB) with image magic-byte verification and owner scoping.
- **Offline-First Synchronization Engine**:
  - **Atomic Sync Push (`POST /api/sync/push`)**: Batch upserts and deletes committed in a single database transaction with idempotent retry receipts.
  - **Optimistic Concurrency Control**: Explicit `expectedVersion` tracking prevents silent overwrite conflicts (returns HTTP `409 Conflict`).
  - **Change-Log Replay (`GET /api/sync/changes`)**: Monotonic revision tracking with cursor pagination for seamless offline catch-up and local data restoration.
  - **Cascade Tombstones**: Soft-deletion tombstones for cascade deletion reconciliation across mobile clients.
- **Interactive Documentation**: Built-in Swagger UI with OpenAPI 3.1 specification.

---

## 🛠️ Tech Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Java 21 | Modern Java LTS |
| **Framework** | Spring Boot 4.1.1 | WebMVC, Security, Validation |
| **Persistence** | Spring Boot Starter JDBC | Direct SQL queries with HikariCP connection pooling |
| **Database** | PostgreSQL 16 | Relational storage & sync event revisions |
| **Migrations** | Flyway | Automated versioned schema management |
| **API Docs** | SpringDoc OpenAPI 3.1.1 | Swagger UI & OpenAPI schema generator |
| **Testing** | JUnit 5 & Spring Test | Unit tests and isolated PostgreSQL integration tests |

---

## 📚 Documentation

Detailed design specifications and contracts:

- 🏛️ [Architecture and Delivery Plan](docs/ARCHITECTURE.md) – System design, offline workflows, and Flutter client integration.
- 🗄️ [Database Design](docs/DATABASE.md) – Schema tables, indices, revisions, and isolation models.
- 🔌 [REST API and Sync Contract](docs/API.md) – Complete endpoint contracts, payload envelopes, and status codes.
- 🛡️ [Setup, Security, and Operations](docs/OPERATIONS.md) – Deployment, configuration, SSL, and security practices.
- 📜 [PostgreSQL Initial Schema](src/main/resources/db/migration/V1__initial_schema.sql) – Flyway database migration script.

---

## 🚀 Getting Started

### Prerequisites

- **Java 21** or higher
- **PostgreSQL 16** (or compatible instance)
- Gradle (Gradle Wrapper `./gradlew` is included in the project)

---

### 1. Configuration

The application loads configuration from environment variables or an optional local properties file.

#### Option A: Local Properties File (Recommended for Local Dev)

Copy the provided example template to create your local config (this file is gitignored):

```sh
cp application-local.properties.example application-local.properties
```

Configure your PostgreSQL credentials in `application-local.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/YOUR_DATABASE
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

> **SSL Notice:** Use `?sslmode=require` in the JDBC URL when your database provider requires SSL, or `verify-full` with the provider's certificate configuration for identity verification.

#### Option B: Environment Variables

Alternatively, define the variables directly in your environment:

```sh
export DB_URL="jdbc:postgresql://localhost:5432/YOUR_DATABASE"
export DB_USERNAME="YOUR_USERNAME"
export DB_PASSWORD="YOUR_PASSWORD"
```

---

### 2. Start the Backend

Run the Spring Boot application using Gradle:

```sh
./gradlew bootRun
```

* **Flyway Schema Creation**: Flyway runs migrations automatically on startup (requires database privileges to create tables/indexes). With blank credentials, startup deliberately fails instead of silently fallbacking.
* **Default Port**: Listens on port `8080` (override using the `PORT` environment variable).

---

## 🧭 Environment Variables Reference

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `DB_URL` | *(empty)* | PostgreSQL JDBC connection URL |
| `DB_USERNAME` | *(empty)* | Database username |
| `DB_PASSWORD` | *(empty)* | Database password |
| `PORT` | `8080` | HTTP server port |
| `JWT_SECRET` | *(internal default)* | Secret key (>=256 bits) for HMAC-SHA256 JWT signing |
| `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` | `900` (15 mins) | Lifetime of JWT access token in seconds |
| `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` | `30` | Lifetime of refresh token in days |

---

## 📑 API Documentation & Swagger UI

Once the application is running, access Swagger UI and OpenAPI schemas:

- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### How to Authorize in Swagger UI:
1. Register via `POST /api/auth/register` or login via `POST /api/auth/login`.
2. Copy the returned `token` from the response.
3. Click the green **Authorize** button in Swagger UI.
4. Paste only the token string into the text box (do **not** include the `Bearer ` prefix).
5. Protected endpoints will now include the Bearer token automatically.

### Key API Endpoints:

| Area | Method & Endpoint | Description |
| :--- | :--- | :--- |
| **Auth** | `POST /api/auth/register` | Register new user account |
| | `POST /api/auth/login` | Login and receive bearer token |
| | `POST /api/auth/refresh` | Rotate refresh token & issue new JWT access token |
| | `POST /api/auth/logout` | Revoke current access and refresh tokens |
| | `GET /api/auth/me` | Fetch authenticated user information |
| **Customers** | `GET /api/customers` | List user customers (paginated) |
| | `PUT /api/customers/{id}` | Upsert customer with client UUID & mutation envelope |
| | `DELETE /api/customers/{id}` | Soft delete customer with version check |
| **Sites** | `GET /api/sites` | List sites (optional filter by `customerId`) |
| | `PUT /api/sites/{id}` | Upsert site record |
| | `DELETE /api/sites/{id}` | Soft delete site record |
| **Notes** | `GET /api/notes` | Search/filter notes (`q`, `status`, `siteId`) |
| | `PUT /api/notes/{id}` | Upsert note (with optional location & `photoId`) |
| | `DELETE /api/notes/{id}` | Soft delete note record |
| **Photos** | `PUT /api/photos/{id}` | Upload binary photo (JPEG/PNG up to 5 MiB) |
| | `GET /api/photos/{id}` | Fetch binary photo content |
| **Sync** | `POST /api/sync/push` | Atomic batch sync push (upsert / delete mutations) |
| | `GET /api/sync/changes` | Replay revision changes since cursor |

---

## 🧪 Testing & Verification

Run tests and assemble the distribution JAR:

```sh
# Run unit and validation tests
./gradlew test

# Build executable production JAR
./gradlew bootJar
```

### Full Integration Testing with PostgreSQL

Without `TEST_DB_URL`, database integration tests are skipped while validation unit tests run. To run the full integration suite against a dedicated disposable database, set `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`.

> ⚠️ **Warning:** Integration tests create tables and test records: never point them at your production application database.

#### Automated Local Test Helper (macOS / Homebrew):
If PostgreSQL 16 is installed via Homebrew, a helper script is provided to spin up an ephemeral test instance:

```sh
./scripts/test-local-postgres.sh
```

This helper starts a fresh temporary PostgreSQL instance on port `55439`, runs all tests, terminates the instance, and cleans up all temporary data. Override `PG_BIN` for custom PostgreSQL paths or `TEST_PORT` if `55439` is occupied.

---

## 🔒 Security & Secrets

- **Ignored Secrets**: `.env`, `.env.*`, `*.env`, and `application-local.properties` are strictly excluded in [`.gitignore`](.gitignore) and will not be pushed to GitHub.
- **Template Safety**: Use `application-local.properties.example` for configuring local secrets safely.
