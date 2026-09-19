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

```bash
mysql -u root -p < backend/db/local-setup.sql
```

This creates the `acme_salary` database with the `utf8mb4_0900_ai_ci` collation
(case-insensitive search) and a local-only `acme` user.

## Running the backend

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Flyway applies migrations automatically on startup.

## Running the tests

```bash
cd backend
./mvnw test      # unit and web-slice tests: fast, no Docker or database needed
./mvnw verify    # also runs *IT integration tests against a Testcontainers MySQL (needs Docker)
```

Integration tests start a throwaway `mysql:8.4` container automatically. No local
database or user setup is required.

## Configuration

| Variable      | Default                                   | Purpose              |
|---------------|-------------------------------------------|----------------------|
| `DB_URL`      | `jdbc:mysql://localhost:3306/acme_salary` | Application database |
| `DB_USERNAME` | `acme`                                    | Database user        |
| `DB_PASSWORD` | `acme`                                    | Database password    |

The defaults are for local development only. Set real values through environment
variables in any shared environment.
