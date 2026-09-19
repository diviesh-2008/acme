# Architecture and Design Decisions

This document records the architecture of the ACME salary management application and
the reasoning behind significant decisions. It is updated as each increment lands.
What the system must do is in [requirements.md](requirements.md). The schema is in
[database-design.md](database-design.md).

## Context

ACME has ~10,000 employees across several countries. HR manages salaries in
spreadsheets. This application lets an authenticated HR Manager browse employees,
maintain salary history, and view compensation analytics.

## System overview

```
┌──────────────────────┐   HTTPS + JSON    ┌───────────────────────────┐   JDBC   ┌──────────┐
│ Angular SPA          │ ────────────────▶ │ Spring Boot REST API      │ ───────▶ │ MySQL 8  │
│ Angular Material     │  Authorization:   │ Spring Security (JWT)     │          │          │
│ (browser)            │  Bearer <token>   │ Spring Data JPA · Flyway  │          │          │
└──────────────────────┘                   └───────────────────────────┘          └──────────┘
```

```
acme/
├── backend/    Spring Boot 4 (Java 21) REST API, MySQL 8
├── frontend/   Angular + Angular Material SPA
└── docs/
```

- **One API and one SPA.** No microservices, message brokers or caches. At 10k
  employees, one MySQL instance with the right indexes answers every query in milliseconds.
- **REST under `/api`**, JSON only, stateless.
- **The Angular dev server proxies `/api` to the backend** in development, so the browser
  sees a single origin and no CORS configuration is needed. In production, the built
  SPA is served from the same origin as the API (or behind the same reverse proxy).
- **Flyway owns the schema.** Hibernate runs with `ddl-auto: validate`, so startup fails
  fast if entities and tables drift apart.

## Authentication flow

```
Browser (Angular)                 Spring Boot                                MySQL
      │  POST /api/auth/login          │                                       │
      │  { email, password }           │                                       │
      │ ─────────────────────────────▶ │ load app_user by email ──────────────▶│
      │                                │ BCrypt.matches(password, hash)        │
      │                                │ sign JWT (HS256, 1h, role claim)      │
      │ ◀───────────────────────────── │ 200 { accessToken, expiresIn }        │
      │                                │   or 401 ProblemDetail                │
      │                                │                                       │
      │  GET /api/employees            │                                       │
      │  Authorization: Bearer <jwt>   │                                       │
      │ ─────────────────────────────▶ │ verify signature + expiry             │
      │                                │ require ROLE_HR_MANAGER               │
      │ ◀───────────────────────────── │ 200 data / 401 / 403                  │
```

- **Issuing tokens.** `POST /api/auth/login` authenticates through Spring Security's
  `AuthenticationManager`, which uses a `UserDetailsService` over `app_user` and a
  `BCryptPasswordEncoder`. On success, a `JwtEncoder` signs a token with `iss`,
  `sub` = email, a single `role` claim (`HR_MANAGER`, mirroring `app_user.role`),
  `iat` and `exp` (1 hour).
- **Validating tokens.** Every other request is checked by Spring Security's
  OAuth2 Resource Server support (Nimbus `JwtDecoder`). It verifies the signature,
  expiry and issuer and maps the `role` claim to `ROLE_HR_MANAGER`. We write no JWT
  filter. Nimbus JOSE+JWT is already Spring Security's JWT engine and its version is
  managed by Spring Boot, so no separate JWT library is added.
- **Signing key.** HS256 with a secret of at least 32 bytes (256 bits) from
  `ACME_JWT_SECRET`. The application fails to start if it is missing or too short, and
  the error message never includes the secret. A single service issues and verifies its
  own tokens, so asymmetric keys would add key management for no benefit.
- **Long passwords.** BCrypt accepts at most 72 bytes, and Spring Security rejects
  longer input with an exception. A longer login password can never match, so it is
  rejected as bad credentials (`401`) rather than causing a `500`.
- **Status codes.** A bad login returns `401` with a generic message, so attackers
  cannot tell which emails exist. A missing, invalid or expired token returns `401`
  with `WWW-Authenticate: Bearer`, and a missing role returns `403`. The security
  filters hand these failures to the same `GlobalExceptionHandler` as the rest of the
  API (via `SecurityProblemHandler`), so every error body is the same `ProblemDetail` shape.
- **Stateless.** No HTTP session is created. CSRF protection is disabled because
  authentication uses a header, not cookies, so the browser never attaches credentials
  automatically.
- **Initial account.** On startup, if `ACME_INITIAL_HR_EMAIL` and
  `ACME_INITIAL_HR_PASSWORD` are set and no user with that email exists, one
  `HR_MANAGER` user is created with a BCrypt hash. The email is stored in lower case.
  - An existing account is left unchanged, including its password, so this is safe on
    every startup.
  - If neither variable is set, a warning is logged and nothing is created.
  - If only one is set, or the password is shorter than 12 characters or longer than
    72 bytes, startup fails.
  - It runs synchronously (`SmartInitializingSingleton`) after the schema is migrated but
    *before* the web server accepts requests. The account therefore always exists before
    the first login.
  - Credentials never appear in source control.
- **Angular side.** The token is kept in `sessionStorage`, so it survives a page reload
  and is cleared when the tab closes. An HTTP interceptor adds the `Authorization`
  header. A route guard blocks unauthenticated navigation. Any `401` clears the token
  and redirects to login. *Trade-off:* script-readable storage is exposed to XSS. This
  is mitigated by Angular's automatic output escaping and the short token lifetime. An
  HttpOnly cookie would remove that exposure but would bring back CSRF handling.
- **Not in v1:** refresh tokens, server-side revocation, rate limiting (see requirements).

## Backend structure

Code is organized by feature, with layers inside each feature:

```
com.acme.salary
├── auth/        AuthController, AuthService, JwtTokenService, AppUser entity + repository,
│                AppUserDetailsService, InitialHrManagerInitializer, dto/
├── employee/    EmployeeController, EmployeeService, EmployeeRepository, Employee entity, dto/
├── salary/      SalaryController, SalaryService, SalaryRecordRepository, SalaryRecord entity, dto/
├── analytics/   AnalyticsController, AnalyticsService, AnalyticsRepository (native queries), dto/
├── seed/        deterministic seed-data generator and loader (dev only)
└── common/
    ├── security/  SecurityConfig (filter chain, BCrypt, JWT encoder/decoder), JwtProperties,
    │              SecurityProblemHandler (401/403 → GlobalExceptionHandler)
    ├── error/     GlobalExceptionHandler
    └── ClockConfig, PageResponse (with the employee query API)
```

Keeping a feature's controller, service and repository together makes each increment
self-contained and easy to review. The layer rules still apply within a feature:

- **Controllers** handle HTTP only: binding, `@Valid` validation and status codes.
  They call services and never touch repositories.
- **Services** own business rules (for example, "no salary change for a terminated
  employee") and transaction boundaries (`@Transactional`).
- **Repositories** use Spring Data JPA. Dynamic employee filters use
  `JpaSpecificationExecutor`. Analytics use native SQL, where aggregation belongs.
- **DTOs** are Java `record`s. JPA entities never leave the service layer.
- Mapping is hand-written in small static methods. No MapStruct and no Lombok: at this
  size they add build complexity without saving much code.
- **Errors** are handled by one `@RestControllerAdvice` that returns RFC 9457
  `ProblemDetail` for validation (400), authentication (401), authorization (403),
  not found (404), conflicts such as a duplicate effective date (409),
  and unexpected errors (500, with no internal details).
- **Time** comes from an injected `java.time.Clock` (UTC). "Today" drives the current
  salary and date validation, so tests can pin it.

## Salary history model

- Each salary change is one row in `salary_record`: `amount`, `currency` and
  `effective_date`. A new change is always a **new row**, so records for earlier dates
  are never overwritten and the full history is kept.
- **Corrections update in place.** A mistake in an existing record is fixed by updating
  its `amount` and `currency` (`PUT` on that record). The effective date is the record's
  identity within an employee's history and cannot be changed. Salary records are never
  deleted. No audit trail of previous values is kept in v1.
- **Current salary** is *derived, not stored*: the record with the latest
  `effective_date` on or before today (UTC). Nothing needs updating when a date passes.
  There is no scheduled job and no denormalized column that could go stale.
- **Future-dated changes** are ordinary rows with an `effective_date` after today. They
  show as "scheduled" in the history and become current automatically on that date.
- **At most one record per employee per effective date**, enforced by a unique
  constraint. A duplicate returns `409 Conflict`. This keeps "the salary on date X"
  unambiguous.
- The employee list needs the current salary only for the rows on the page (≤ 100),
  so it is fetched with a second, indexed query rather than a join over all history.

## Multi-currency analytics

**Decision: every aggregate is computed per currency. Amounts in different currencies
are never added together or averaged together.**

- Averaging 90,000 USD with 7,000,000 INR produces a number that means nothing. A
  correct cross-currency figure needs an exchange-rate source, a rate history and a
  policy for which date's rate applies. Live conversion is out of scope for v1.
- Analytics group by `(currency)`, `(country, currency)` and `(department, currency)`.
  A country with employees paid in two currencies shows two rows. That is accurate,
  and it shows HR where pay practices diverge.
- Metrics per group: headcount, min, max, average and median of **current salary**, for
  employees who are not `TERMINATED`.
- Current salaries are selected in SQL with a window function
  (`ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY effective_date DESC)`).
  MySQL has no median function. The service computes medians from the current-salary
  rows, which is at most 10,000 small rows per request.
- If exchange rates are added later, a converted "reporting currency" view can sit
  next to the per-currency figures without changing the data model.

## Pagination strategy

- **Offset pagination** with Spring Data `Pageable`:
  `GET /api/employees?page=0&size=20&sort=lastName,asc&search=…&country=…&department=…&status=…`
- **Page size**: default 20, capped at 100 on the server, so a client cannot request all
  10,000 rows in one call.
- **Sorting**: only whitelisted fields can be sorted (`lastName`, `firstName`,
  `employeeCode`, `hireDate`, `country`, `department`). Unknown fields return `400`.
  `id` is always added as a final tie-breaker, so rows never repeat or disappear
  between pages.
- **Response**: our own `PageResponse<T>` record (`content`, `page`, `size`,
  `totalElements`, `totalPages`), not a serialized Spring `Page`. That keeps the JSON
  contract stable and free of Spring Data internals.
- **Search**: case-insensitive substring match on first name, last name, full name,
  email and employee code. User-typed `%` and `_` are escaped, so they are treated as
  literal characters rather than wildcards. Case-insensitivity comes from the
  `utf8mb4_0900_ai_ci` collation, so the SQL needs no `LOWER()` calls.
- **Why not keyset pagination?** The UI needs total counts and jump-to-page. At 10,000
  rows, `COUNT(*)` and `OFFSET` cost milliseconds. Keyset pagination would be worth
  revisiting only at millions of rows.
- **Filter options**: `GET /api/employees/filter-options` returns the distinct
  countries and departments and the list of statuses, so dropdowns always match the data.

## Testing strategy

| Layer | Tooling | Needs Docker | Runs in | What it covers |
|-------|---------|:---:|---------|----------------|
| Unit | JUnit 5, Mockito, AssertJ | No | `./mvnw test` (Surefire, `*Test`) | Service rules (current-salary choice, validation, terminated-employee guard), seed generator determinism, sort whitelist |
| Web slice | `@WebMvcTest` + MockMvc | No | `./mvnw test` (Surefire, `*Test`) | Request validation, status codes, `ProblemDetail` bodies, 401/403 behaviour, DTO JSON shape |
| Integration | `@IntegrationTest` (`@SpringBootTest` + Testcontainers MySQL) | Yes | `./mvnw verify` (Failsafe, `*IT`) | Flyway migrations, repository queries (search, filters, pagination, current salary, analytics SQL), login → protected API end to end |

- **No manual database setup.** Integration tests start a `mysql:8.4` container through
  Testcontainers. Spring Boot's `@ServiceConnection` points the datasource at it
  automatically.
- **Fast.** Every integration test uses the same `@IntegrationTest` meta-annotation. Spring
  therefore caches one application context and starts **one container per test run**,
  not one per class. Most behaviour is covered by unit and slice tests, which need no
  database. `./mvnw test` stays Docker-free for a quick feedback loop.
- **Deterministic.**
  - The MySQL image version is pinned.
  - A fixed `Clock` is injected, so "today" never depends on when tests run.
  - Tests create the exact data they assert on and never rely on the 10k seed.
    Seeding is disabled in tests.
  - Integration tests use MockMvc in the test thread and are `@Transactional`, so each
    test's data is rolled back.
  - No test depends on execution order.
- **Real MySQL, not H2.** H2's "MySQL mode" differs in collation (case-insensitive
  search), window functions and DDL. Those are exactly the behaviours the integration
  tests need to prove.
- **Requirement:** integration tests need a Docker-compatible engine (Docker Desktop, or
  Docker in WSL2). Without one they fail with "Could not find a valid Docker
  environment". They fail rather than skip silently, so a green build always means the
  integration tests actually ran.

## Seed strategy

- **Separate from schema migrations.** Flyway migrations contain only schema and run in
  every environment. Demo data is loaded by a `seed` component that runs only when
  `acme.seed.enabled=true`. That is set by the `dev` profile and off by default,
  including in tests and production.
- **Idempotent.** The loader runs only when the `employee` table is empty, so restarting
  the app never duplicates data.
- **Deterministic.**
  - The generator uses `java.util.Random` with a fixed seed. Its algorithm is fixed by
    the Java SE specification, so every JVM produces the same sequence.
  - All dates are calculated from a fixed reference date (2026-01-01), never from
    `LocalDate.now()`.
  - Employee codes run in order from `EMP-00001` to `EMP-10000`.
  - Emails are built from the name plus the employee code, so they are unique.
- **Realistic shape.**
  - About 8 countries, each paid mostly in its local currency, with a small share paid
    in USD to exercise multi-currency.
  - About 8 departments, with salary bands per country and department.
  - Status mix of roughly 90% `ACTIVE`, 5% `ON_LEAVE` and 5% `TERMINATED`.
  - 1–4 salary records per employee, as yearly raises since hire.
  - About 5% of employees have a future-dated raise.
  - Emails use the reserved `acme.example` domain.
- **Fast.** JDBC batch inserts in a single transaction, with `rewriteBatchedStatements`
  enabled on the MySQL driver. That takes about 10k employee rows and about 25k salary
  rows in a few seconds, rather than through 35k JPA `persist` calls.
- **Tested.** A unit test checks that the generator yields exactly 10,000 employees and
  that two runs produce identical output. An integration test checks that the loader
  inserts them and is idempotent.
- **Why not a Flyway Java migration?** It would mix demo data into schema history and run
  in every environment, including production.

## Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `ACME_DB_URL` | `jdbc:mysql://localhost:3306/acme_salary` | Application database |
| `ACME_DB_USERNAME` / `ACME_DB_PASSWORD` | *none* | Database credentials |
| `ACME_JWT_SECRET` | *none; required* | HS256 signing key (≥ 32 bytes) |
| `ACME_INITIAL_HR_EMAIL` / `ACME_INITIAL_HR_PASSWORD` | *none* | Creates the initial HR Manager account if absent (password 12 characters to 72 bytes) |

Nothing that is secret in a shared environment is committed.

## Delivery increments

Each increment is independently reviewable and committable.

1. **Project foundation**: Spring Boot project, configuration, test infrastructure, docs. *(done)*
2. **Authentication**: `app_user` migration, BCrypt, initial-user bootstrap, login endpoint, JWT issuing and validation, 401/403 handling, and the global `ProblemDetail` error handler. *(done)*
3. **Employee schema and seed data**: `employee` + `salary_record` migrations and the deterministic 10k seed.
4. **Employee query API**: `PageResponse` DTO, pagination, search, filters, filter options, employee detail.
5. **Salary history API**: record a salary change, correct a record, list history, current salary.
6. **Analytics API**: per-currency aggregates overall, by country and by department.
7. **Angular foundation**: project setup, login page, auth interceptor and route guard.
8. **Employee list UI**: Material table with server-side pagination, search and filters.
9. **Employee detail and salary history UI**: history table, add-salary and correct-salary forms.
10. **Analytics UI**.

## Resolved decisions

| Question | Decision |
|----------|----------|
| How is the first HR Manager created? | From environment variables at startup, BCrypt-hashed. Never committed. |
| Which salary is "current"? | The latest `effective_date` on or before today. Future-dated changes are supported. |
| How are multiple currencies aggregated? | Separately per currency. There is no conversion. |
| Can employees be created or edited? | No. Employees are read-only in v1. |
| How is a mistaken salary entry corrected? | By updating that record's amount and currency. There is one record per employee per effective date, and no superseding records. |
| Is there an audit log? | Not in v1. Only fields the application uses are stored. |
| What if Docker is unavailable for integration tests? | They fail, never skip, so a green build means they ran. |
