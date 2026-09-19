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
├── employee/    EmployeeController, EmployeeService, EmployeeRepository, EmployeeSpecifications,
│                Employee entity, EmploymentStatus, dto/ (EmployeeResponse, EmployeeSearchCriteria)
├── salary/      SalaryController, SalaryService, SalaryRecordRepository, SalaryRecord entity,
│                SalaryRecordNotFoundException, dto/ (SalaryRecordResponse, CreateSalaryRequest,
│                CorrectSalaryRequest)
├── analytics/   AnalyticsController, AnalyticsService, SalaryStatisticsRepository (native SQL via
│                JdbcTemplate), dto/ (AnalyticsOverviewResponse, Currency/Country/DepartmentSalaryStatistics)
├── seed/        EmployeeSeedGenerator, SalarySeedGenerator (deterministic), SeedDataLoader (dev only)
└── common/
    ├── security/    SecurityConfig (filter chain, BCrypt, JWT encoder/decoder), JwtProperties,
    │                SecurityProblemHandler (401/403 → GlobalExceptionHandler)
    ├── error/       GlobalExceptionHandler, NotFoundException, ConflictException,
    │                FieldValidationException
    ├── validation/  @IsoCurrencyCode
    └── ClockConfig, PageResponse
```

Keeping a feature's controller, service and repository together makes each increment
self-contained and easy to review. Each feature is one flat package plus `dto/`, rather
than `controller/`, `service/` and `entity/` sub-packages. With a handful of classes per
feature, the extra packages would only force wider visibility. The layer rules still apply
within a feature:

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
  `ProblemDetail`:
  - `400` for validation, with an `errors` object per field. This covers Bean Validation,
    business rules that need data (`FieldValidationException`), unparseable values and
    unknown JSON fields.
  - `401` for authentication and `403` for authorization.
  - `404` for anything missing. `EmployeeNotFoundException` and
    `SalaryRecordNotFoundException` are separate classes that share one base.
  - `409` for conflicts: a duplicate effective date or a terminated employee
    (`ConflictException`), or a write rejected by a database constraint.
  - `500` for unexpected errors, with no internal details.
- **Unknown JSON fields are rejected** (`fail-on-unknown-properties`). A client that
  sends `id`, `createdAt`, or an `effectiveDate` with a correction gets `400` naming the
  field, instead of having it silently ignored.
- **Time** comes from an injected `java.time.Clock` (UTC). "Today" drives the current
  salary and date validation, so tests can pin it.

## Salary history model

- Each salary change is one row in `salary_record`: `amount`, `currency`,
  `effective_date` and `created_at`. `created_at` is `DATETIME(6)` in UTC, set from the
  application's `Clock` and never changed afterwards. A new change is always a
  **new row**, so records for earlier dates are never overwritten and the full history
  is kept.
- **Corrections update in place.** A mistake in an existing record is fixed with `PUT`
  on that record, which changes only `amount` and `currency`.
  - The effective date identifies the record within an employee's history, so it cannot
    change. The correction request has no `effectiveDate` field, and sending one is
    rejected with `400`.
  - `employee_id`, `effective_date` and `created_at` are mapped `updatable = false`, so
    Hibernate cannot write them either.
  - The URL's employee must own the record. Otherwise the answer is `404`, so one
    employee's URL can never modify another employee's salary.
  - Corrections are allowed for terminated employees, because they fix historical data.
  - Salary records are never deleted (`DELETE` returns `405`), no superseding records are
    created, and no audit trail of previous values is kept in v1.
- **Current salary** is *derived, not stored*: the record with the latest
  `effective_date` on or before today. "Today" is `LocalDate.now(clock)` from the
  injected UTC `Clock`, so tests can fix it. Nothing needs updating when a date passes.
  There is no scheduled job, no `effective_to` column and no denormalized current-salary
  column that could go stale.
- **Future-dated changes** are ordinary rows with an `effective_date` after today. They
  appear in the history and become current automatically on that date.
- **At most one record per employee per effective date**, enforced by a unique
  constraint. This keeps "the salary on date X" unambiguous.
- **Adding a record** (`POST`) is allowed for past, current and future dates, within
  these rules from the requirements:
  - The date must be on or after the employee's hire date.
  - The date may be at most one year ahead.
  - The employee must not be `TERMINATED`.
  - The date must not duplicate an existing record's date.
- The employee list will need the current salary only for the rows on the page (≤ 100),
  so it will be fetched with a second, indexed query rather than a join over all history.

## Salary API

All endpoints require the `HR_MANAGER` role.

| Endpoint | Success | Errors |
|----------|---------|--------|
| `GET /api/employees/{employeeId}/salary` | `200` the record in force today | `404` if the employee is missing or has no salary effective yet (the two messages differ) |
| `GET /api/employees/{employeeId}/salary/history` | `200` array, newest `effectiveDate` first; `[]` if none | `404` if the employee is missing |
| `POST /api/employees/{employeeId}/salary` `{amount, currency, effectiveDate}` | `201` the new record | `400` invalid field; `404` employee missing; `409` duplicate date or terminated employee |
| `PUT /api/employees/{employeeId}/salary/{salaryId}` `{amount, currency}` | `200` the corrected record | `400` invalid field or `effectiveDate` sent; `404` record not found for this employee |

Responses are `{ id, amount, currency, effectiveDate }`, and `amount` always has two
decimal places. No endpoint returns a JPA entity.

**Validation.**
- `amount` is required, `> 0`, with at most 13 digits before the decimal point and 2 after.
  It is stored as `BigDecimal` / `DECIMAL(15,2)`.
- `currency` is required and must be an ISO 4217 code that the JDK knows
  (`java.util.Currency`). It is accepted in any letter case and stored upper-case.
- `effectiveDate` is required on `POST`. Clients cannot send `id` or `createdAt`.

**Currency decision.** The requirements say "a valid ISO 4217 code" without naming a
fixed set, so any code in the JDK's ISO 4217 table is accepted. That covers INR, USD, GBP,
EUR, AUD, CAD, SGD and the rest. There is no currency table, no exchange-rate API and no
conversion.

**Transactions and concurrency.**
- `create` and `correct` are `@Transactional` at the service level; reads are
  `readOnly`.
- `create` checks for a duplicate date first, to return a clear `409`. The unique
  `(employee_id, effective_date)` constraint is still the final guard. If two requests
  race past the check, the second `INSERT` fails, its transaction rolls back, and the
  handler turns the `DataIntegrityViolationException` into `409`. In a test with 10
  simultaneous POSTs for the same date, exactly one succeeded and nine got `409`.
- Two simultaneous corrections to the same record are last-write-wins. There is no
  version column, which is acceptable for a small HR team.

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
- There are no exchange-rate APIs, conversion tables or hard-coded rates.
- If exchange rates are added later, a converted "reporting currency" view can sit
  next to the per-currency figures without changing the data model.

## Analytics API

All endpoints require the `HR_MANAGER` role. They are read-only, not paginated and have
no filters.

| Endpoint | Returns (always `200`) |
|----------|------------------------|
| `GET /api/analytics/overview` | `{ generatedAt, asOfDate, currencies: [ CurrencySalaryStatistics ] }`, one entry per currency, ordered by currency code |
| `GET /api/analytics/by-country` | `[ CountrySalaryStatistics ]`, one row per `(country, currency)`, ordered by country then currency |
| `GET /api/analytics/by-department` | `[ DepartmentSalaryStatistics ]`, one row per `(department, currency)`, ordered by department then currency |

Each statistics row has `currency`, `employeeCount`, `averageSalary`, `medianSalary`,
`minimumSalary` and `maximumSalary`, plus `country` or `department`. Amounts have two
decimal places.

- **Current salary** uses the same rule as `GET /api/employees/{id}/salary`: the record
  with the latest `effective_date` on or before today.
  - Future-dated records are ignored, and an older record never counts once a newer one
    is in force.
  - Employees with no salary, or only future-dated salaries, are not counted.
- **"Today" is the UTC calendar date from the injected `Clock`.**
  - A salary is current when `effective_date <= asOfDate`, where `asOfDate` is the UTC
    date of `clock.instant()`. `generatedAt` comes from the same instant.
  - The salary API uses the same `Clock`, so an employee's current salary is the same in
    both.
  - The overview returns `asOfDate` so consumers know which business date the statistics
    represent. For example, from 00:00 to 05:30 in India (UTC+5:30) it is still the
    previous UTC date.
  - There is deliberately no time-zone configuration and no per-user local time zone.
- **Terminated employees are excluded.** Analytics count only employees whose
  `employment_status` is not `TERMINATED`, following the approved product requirement
  that compensation analytics reflect the current workforce. `ON_LEAVE` employees are
  still employed and are included. `ACTIVE` employees are, of course, included.
- **Empty results.** If no one has a current salary, `currencies` is `[]` and the other
  endpoints return `[]`, with `200`. There are no zero-valued statistics and no `404`.
- **Database-side aggregation.** Each request runs **one** native SQL query
  (`SalaryStatisticsRepository`). Salary history is never loaded into Java and there is
  no per-employee query.
  - A `ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY effective_date DESC)` over
    records with `effective_date <= :today` picks exactly one current salary per
    employee. The unique `(employee_id, effective_date)` constraint rules out ties.
  - The query then joins `employee` for status, country and department, and groups by
    `(currency)`, `(country, currency)` or `(department, currency)`.
  - Native SQL is needed because JPQL has no window functions. The three endpoints share
    one SQL template and differ only in the grouping column, which comes from an enum,
    never from request input.
  - The per-employee salary endpoint keeps its own single-row query. The two queries
    implement the same rule in different shapes, and `AnalyticsIT` checks that they agree.
- **Median.** MySQL has no `MEDIAN`, so a second window function numbers each group's
  salaries by amount (`ROW_NUMBER() … ORDER BY amount`, with `COUNT(*) OVER` for the group
  size). The median is `AVG` of positions `(n+1) DIV 2` and `(n+2) DIV 2`: the middle
  value for odd `n`, the mean of the two middle values for even `n`. For example,
  [100, 200, 300] gives 200 and [100, 200, 300, 400] gives 250. It is computed
  independently for every group.
- **Precision.** MySQL returns exact `DECIMAL` sums, minimums, maximums and medians, and
  Java uses `BigDecimal` throughout (no `float` or `double`).
  - `averageSalary = SUM / COUNT` and `medianSalary` are each rounded **once**, to 2
    decimal places with `RoundingMode.HALF_UP`, in `AnalyticsService`. For example,
    100.015 becomes 100.02.
  - Minimum and maximum are stored amounts and need no rounding.
- **Demo data.** The `dev` seed (10,000 employees, about 24,901 salary records) spans
  7 currencies, 8 countries (each non-US country also has some USD-paid employees),
  9 departments, future-dated raises and terminated employees. The analytics therefore
  show meaningful, currency-separated figures as soon as the app starts. The seed was
  not changed for this step. Edge cases the seed doesn't cover (no salary, future-only
  salary) are tested with fixtures.

## Employee API

Read-only; both endpoints require the `HR_MANAGER` role.

| Endpoint | Returns |
|----------|---------|
| `GET /api/employees?page=&size=&search=&country=&department=&status=` | `200` `PageResponse<EmployeeResponse>` |
| `GET /api/employees/{id}` | `200` `EmployeeResponse`, or `404` |

Query parameters bind to the `EmployeeSearchCriteria` record, which is validated with
Bean Validation. `EmployeeSpecifications` turns it into a JPA `Specification`, and
`EmployeeRepository.findAll(spec, pageRequest)` runs it. The filtering, ordering, paging
and count all happen in MySQL. Measured plans are in [performance.md](performance.md).

## Pagination strategy

- **Offset pagination** with Spring Data `PageRequest`:
  `GET /api/employees?page=0&size=20`.
- **Page size**: default 20, maximum 100.
- **Invalid values are rejected, not corrected.** `page < 0`, `size < 1`, `size > 100` and
  non-numeric values return `400` with an error per field, from the central handler.
  Silently capping `size=1000` to 100 would hide client bugs.
- **Fixed ordering**: `last_name, first_name, id`, all ascending. The `id` tie-breaker
  makes paging deterministic when names repeat, so rows never appear on two pages or go
  missing. There is **no client-controlled sorting** in v1. Every sortable column would
  need its own index to stay fast, and the UI doesn't need it yet. It can be added later
  with a whitelist of fields.
- **Response**: our own `PageResponse<T>` record (`content`, `page`, `size`,
  `totalElements`, `totalPages`, `hasNext`, `hasPrevious`), not a serialized Spring
  `Page`. That keeps the JSON contract stable and free of Spring Data internals.
- **Why not keyset pagination?** The UI needs total counts and jump-to-page. At 10,000
  rows, `COUNT` and `OFFSET` cost milliseconds (a deep page takes about 39 ms). Keyset
  pagination would be worth revisiting only at millions of rows.

## Search and filter strategy

- **Search** (`search=`) is a case-insensitive substring match on employee code, first
  name, last name, full name (`"first last"`, so `john smith` works) and email. The
  conditions are OR'ed.
- **Filters** (`country`, `department`, `status`) are exact matches, AND'ed with each
  other and with search. Blank values are ignored. `country` is uppercased before
  querying, and `department` matching ignores letter case through the collation.
- **Case-insensitivity comes from the `utf8mb4_0900_ai_ci` collation**, so the SQL has no
  `LOWER()` calls. The collation is also accent-insensitive.
- **Wildcards are escaped.** User-typed `%` and `_` match literally (`!` is the `LIKE`
  escape character; a backslash would clash with MySQL's string escaping).
- **Validation:** `search` and `department` are limited to 100 characters, and `country`
  must be two letters. `status` must be `ACTIVE`, `ON_LEAVE` or `TERMINATED` (exact case).
  Anything else returns `400`.
- **Why Specifications?** They build a WHERE clause from optional conditions using plain
  JPA, with no extra library. Four optional filters would otherwise need 16 repository
  methods or hand-built JPQL strings.
- **Filter options** for UI dropdowns (distinct countries and departments) are planned
  with the employee list UI.

## Testing strategy

| Layer | Tooling | Needs Docker | Runs in | What it covers |
|-------|---------|:---:|---------|----------------|
| Unit | JUnit 5, Mockito, AssertJ | No | `./mvnw test` (Surefire, `*Test`) | Service rules, paging and ordering requests, which SQL conditions each filter produces, seed generator determinism, seed loader idempotency |
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
  - Unit tests inject a fixed `Clock` (e.g. 2026-09-19), so "today" never depends on
    when they run. Integration tests keep the real clock, because issued JWTs must be
    valid now. They use dates far from today (e.g. 2001 and 2999), or call the
    repository with an explicit date.
  - The integration-test context loads the seed (10,000 employees, 24,901 salary
    records) once per run. That tests the loader against MySQL and runs queries at
    realistic volume.
  - Search, filter and salary tests assert on their own fixture rows, whose values the
    seed never generates (surname "Quillfeather", country `NZ`, employee codes `SAL-*`).
    They don't depend on what the generator happened to pick.
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
- **Test counts (after Step 5).** The two suites are separate, so their counts don't
  add up to one another:

  | Suite | Command | Tests | Made up of |
  |-------|---------|------:|------------|
  | Unit and web-slice (`*Test`) | `.\mvnw.cmd test` (no Docker) | 173 | 152 existing + 21 new analytics tests |
  | Integration (`*IT`) | `.\mvnw.cmd verify` (Docker) | 53 | 38 existing + 15 new analytics tests |

  Docker is not available on the development machine. So the 53 integration tests were
  verified against a throwaway MySQL 8.0 instance, using a scratch copy of the backend in
  which only `TestcontainersConfiguration` is replaced. No integration test is skipped or
  disabled in the repository.

## Seed strategy

**Development and demo data only.** With the `dev` profile, the application loads
**10,000 employees and about 24,901 salary records** (exactly 24,901 with the current
generator). Both are deterministic. Salary histories are seeded as well as employees so
that compensation analytics can be demonstrated immediately. The original plan left
salary seeding to a later step; including it now was a deliberate change. Nothing is
seeded without `acme.seed.enabled=true`, so production never generates employees or
salaries automatically.

- **Separate from schema migrations.** Flyway migrations contain only schema and run in
  every environment. Demo data is loaded by `SeedDataLoader`, which exists only when
  `acme.seed.enabled=true`. The `dev` profile (`application-dev.yml`) sets that, and so
  does the integration-test context. It is off by default, so production never seeds.
- **Order.** Employees are seeded first, then salary histories. One loader does both,
  so the order is guaranteed.
- **Idempotent, per table.**
  - Employees are inserted only when the `employee` table is empty.
  - Salaries are inserted only when `salary_record` is empty, and only for employees
    whose codes came from the seed.
  - Restarting never duplicates data or tops up a partially filled table. A database
    seeded with employees in Step 3 gets its salary histories on the next start.
- **Synchronous, before requests.** Like the initial HR account, the loader runs as a
  `SmartInitializingSingleton`, after migrations but before the web server accepts
  requests. No request can see a half-seeded table.
- **All or nothing, per table.** Each table's rows go in one transaction. A failure
  leaves that table empty, and the next startup retries.
- **Deterministic.**
  - `EmployeeSeedGenerator` is a pure class using `java.util.Random` with a fixed seed.
    That algorithm is fixed by the Java SE specification, so every JVM produces the same
    sequence.
  - Hire dates fall between the fixed dates 2010-01-01 and 2026-01-01, never relative to
    `LocalDate.now()`.
  - Each employee consumes the same random draws in the same order.
  - Employee codes run from `EMP-00001` to `EMP-10000`. Emails are
    `first.last<number>@acme.example`, unique by construction, on a reserved domain.
  - A unit test pins the first generated employee, so an accidental change to the data
    is caught.
- **Realistic shape.**
  - 50 first and 50 last names from several cultures. Names repeat, which exercises the
    `id` tie-breaker.
  - 8 countries with weights: US 30%, IN 16%, GB 14%, DE 10%, AU 10%, CA 8%, FR 7%,
    SG 5%.
  - 9 departments, each with its own job titles; Engineering is 35%.
  - Status mix of 90% `ACTIVE`, 4% `ON_LEAVE` and 6% `TERMINATED`.
- **Salary histories** (`SalarySeedGenerator`, its own fixed random seed) come to
  24,901 records:
  - Every employee starts with a salary on their hire date. 0–3 raises of 2–10% follow on
    1 January of recent years, up to 2026.
  - About 5% of non-terminated employees have a raise scheduled for 2027-01-01, so there
    is future-dated data to show.
  - Amounts are whole numbers in the local currency (INR, USD, GBP, EUR, AUD, CAD, SGD),
    scaled per country and department. These are typical pay levels, not converted
    amounts. About 3% of non-US employees are paid in USD, so a country can have two
    currencies. An employee keeps one currency across their history.
  - Every seeded row has `created_at = 2026-01-01T00:00Z`, as if imported from the
    spreadsheets that day. The amounts are pinned by a unit test.
- **Fast.** JDBC batch inserts of 1,000 rows, with `rewriteBatchedStatements` enabled on
  the MySQL driver. It takes about 2–3 seconds per table instead of 35,000 JPA `save()`
  calls.
- **Why not a Flyway Java migration?** It would mix demo data into schema history and run
  in every environment, including production.

## Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `ACME_DB_URL` | `jdbc:mysql://localhost:3306/acme_salary` | Application database |
| `ACME_DB_USERNAME` / `ACME_DB_PASSWORD` | *none* | Database credentials |
| `ACME_JWT_SECRET` | *none; required* | HS256 signing key (≥ 32 bytes) |
| `ACME_INITIAL_HR_EMAIL` / `ACME_INITIAL_HR_PASSWORD` | *none* | Creates the initial HR Manager account if absent (password 12 characters to 72 bytes) |
| `SPRING_PROFILES_ACTIVE` | *none* | `dev` enables the demo seed (employees and salary histories) |

Nothing that is secret in a shared environment is committed.

## Delivery increments

Each increment is independently reviewable and committable.

1. **Project foundation**: Spring Boot project, configuration, test infrastructure, docs. *(done)*
2. **Authentication**: `app_user` migration, BCrypt, initial-user bootstrap, login endpoint, JWT issuing and validation, 401/403 handling, and the global `ProblemDetail` error handler. *(done)*
3. **Employee domain**: `employee` migration, deterministic 10k seed, read-only list API (search, filters, pagination) and employee detail. *(done)*
4. **Salary history API**: `salary_record` migration and seeded histories; record a salary change, correct a record, list history, current salary. *(done)*
5. **Analytics API**: per-currency aggregates overall, by country and by department. *(done)*
6. **Angular foundation**: project setup, login page, auth interceptor and route guard.
7. **Employee list UI**: Material table with server-side pagination, search and filters, plus the filter-options endpoint.
8. **Employee detail and salary history UI**: history table, add-salary and correct-salary forms.
9. **Analytics UI**.

## Resolved decisions

| Question | Decision |
|----------|----------|
| How is the first HR Manager created? | From environment variables at startup, BCrypt-hashed. Never committed. |
| Which salary is "current"? | The latest `effective_date` on or before today. Future-dated changes are supported. |
| How are multiple currencies aggregated? | Separately per currency. There is no conversion. |
| Where are analytics computed? | In MySQL, with one query per request. Java only rounds the average and median (2 dp, `HALF_UP`). |
| Do analytics count terminated employees? | No, as the requirements specify. `ON_LEAVE` employees are counted. |
| Does analytics need a new index? | No. Measured; see [performance.md](performance.md#analytics-queries). |
| Can employees be created or edited? | No. Employees are read-only in v1. |
| How is a mistaken salary entry corrected? | By updating that record's amount and currency. There is one record per employee per effective date, and no superseding records. |
| Is there an audit log? | Not in v1. `salary_record.created_at` records when a row was added; corrections don't keep previous values. |
| Which currencies are accepted? | Any ISO 4217 code in the JDK's table (`java.util.Currency`), in any letter case, stored upper-case. |
| What happens to unknown JSON fields? | `400` naming the field, application-wide. |
| Does `salary_record` need an index besides its unique constraint? | No. The unique `(employee_id, effective_date)` index serves every query and the foreign key. |
| Is salary history seeded? | Yes, in development/demo only: 10,000 employees and about 24,901 deterministic salary records, so analytics can be demonstrated immediately. Never in production. |
| Does `POST …/salary` return a `Location` header? | No. The `201` body contains the new record, and a salary-by-id `GET` endpoint would exist only for REST form. |
| How are simultaneous corrections handled? | Last write wins. There is no version column or audit log in v1. |
| What if Docker is unavailable for integration tests? | They fail, never skip, so a green build means they ran. |
| Can clients choose the sort order? | Not in v1. The order is fixed at last name, first name, id. |
| What happens with `size=1000`? | `400`, not silently capped. The maximum is 100. |
| Which statuses get an index? | None. It's too unselective; see [database-design.md](database-design.md#indexes). |
