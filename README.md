# ACME Salary Management

A web application that lets an HR Manager manage employee salary information and
understand compensation across ACME's ~10,000 employees.

- `backend/`: Spring Boot 4 REST API (Java 21, Spring Security, Spring Data JPA, MySQL, Flyway)
- `frontend/`: Angular + Angular Material (coming in a later increment)
- `docs/requirements.md`: scope, what is excluded and why
- `docs/architecture.md`: architecture and design decisions
- `docs/database-design.md`: tables, relationships, constraints and indexes

## Prerequisites

- Java 21
- MySQL 8 on `localhost:3306`, to run the application
- Docker (Docker Desktop, or Docker in WSL2), to run the integration tests
- Maven is **not** required. Use the included wrapper (`mvnw` / `mvnw.cmd`).

## Local database setup (one time)

This is needed only to run the application. Tests don't use it.

Replace the example password in `backend/db/local-setup.sql` with your own, then run it
as a MySQL administrator:

```bash
mysql -u root -p < backend/db/local-setup.sql
```

This creates only the `acme_salary` database, with the `utf8mb4_0900_ai_ci` collation
(case-insensitive search), and a local `acme` user with rights on that database alone.

## Running the backend

All credentials come from environment variables. Choose your own values and never
commit them. The initial HR Manager account is created on first startup, before the
server accepts requests.

```bash
cd backend
export ACME_DB_USERNAME="acme"
export ACME_DB_PASSWORD="<the password from local-setup.sql>"
export ACME_JWT_SECRET="$(openssl rand -base64 48)"
export ACME_INITIAL_HR_EMAIL="hr@acme.com"
export ACME_INITIAL_HR_PASSWORD="<at least 12 characters>"
./mvnw spring-boot:run
```

In PowerShell, set each variable with `$env:NAME = "value"` and start with
`.\mvnw.cmd spring-boot:run`.

Flyway applies migrations automatically on startup. Once the account exists, it is
never modified, and changing `ACME_INITIAL_HR_PASSWORD` later does not reset it.

## API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/login` | Public | `{"email", "password"}` → `{"accessToken", "tokenType", "expiresIn", "expiresAt"}` |
| `GET` | `/api/auth/me` | Bearer token | The signed-in user's `email` and `role` |

```bash
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"hr@acme.com","password":"<your password>"}'
curl -s localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken>"
```

Errors use RFC 9457 Problem Details (`application/problem+json`):

- `400` for malformed or invalid input
- `401` for bad credentials or a missing, invalid or expired token
- `403` for a missing role

## Running the tests

```bash
cd backend
./mvnw test      # unit and web-slice tests: fast, no Docker or database needed
./mvnw verify    # also runs *IT integration tests against a Testcontainers MySQL (needs Docker)
```

Integration tests start a throwaway `mysql:8.4` container automatically. No local
database or user setup is required.

## Configuration

| Variable                   | Default                                   | Purpose                                              |
|----------------------------|-------------------------------------------|------------------------------------------------------|
| `ACME_DB_URL`              | `jdbc:mysql://localhost:3306/acme_salary` | Application database                                 |
| `ACME_DB_USERNAME`         | *none*                                    | Database user                                        |
| `ACME_DB_PASSWORD`         | *none*                                    | Database password                                    |
| `ACME_JWT_SECRET`          | *none; required*                          | JWT signing secret, at least 32 bytes                |
| `ACME_INITIAL_HR_EMAIL`    | *none*                                    | Initial HR Manager email (created if absent)         |
| `ACME_INITIAL_HR_PASSWORD` | *none*                                    | Initial HR Manager password, 12 characters to 72 bytes |

Only the database URL has a default, which points at a local MySQL. No credentials or
secrets have defaults.
