# ACME Salary Management

A web application that lets an HR Manager manage employee salary information and
understand compensation across ACME's ~10,000 employees.

- `backend/`: Spring Boot 4 REST API (Java 21, Spring Security, Spring Data JPA, MySQL, Flyway)
- `frontend/`: Angular 19 + Angular Material HR Manager app (see [Frontend](#frontend))
- `docs/requirements.md`: scope, what is excluded and why
- `docs/architecture.md`: architecture and design decisions
- `docs/database-design.md`: tables, relationships, constraints and indexes
- `docs/performance.md`: generated SQL, measured query plans and known limits

## Prerequisites

- Java 21
- MySQL 8 on `localhost:3306`, to run the application
- Docker (Docker Desktop, or Docker in WSL2), to run the integration tests
- Maven is **not** required. Use the included wrapper (`mvnw` / `mvnw.cmd`).
- Node.js 20.11+ (or 22+) and npm, for the frontend. Chrome is needed for the frontend tests.

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
export SPRING_PROFILES_ACTIVE="dev"      # loads demo employees and salaries on first start
./mvnw spring-boot:run
```

In PowerShell, set each variable with `$env:NAME = "value"` and start with
`.\mvnw.cmd spring-boot:run`. If you keep the variables in a git-ignored `.env` file at
the repository root, you can load it into PowerShell with:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $name, $value = $_ -split '=', 2; Set-Item "env:$name" $value }
```

Flyway applies migrations automatically on startup. Once the account exists, it is
never modified, and changing `ACME_INITIAL_HR_PASSWORD` later does not reset it.

**Demo data (development only).** With the `dev` profile, the first start loads
deterministic demo data in a few seconds: 10,000 employees and about 24,901 salary
records. Salary histories are included so compensation analytics can be demonstrated
immediately. Each table is filled only while empty, so later starts skip it. A database
seeded with employees by an earlier version gets its salary histories on the next start.
Without `dev`, nothing is seeded; production never generates employees or salaries
automatically.

## API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/login` | Public | `{"email", "password"}` → `{"accessToken", "tokenType", "expiresIn", "expiresAt"}` |
| `GET` | `/api/auth/me` | Bearer token | The signed-in user's `email` and `role` |
| `GET` | `/api/employees` | Bearer token | One page of employees; see below |
| `GET` | `/api/employees/{id}` | Bearer token | One employee, or `404` |
| `GET` | `/api/employees/{id}/salary` | Bearer token | The salary in force today, or `404` |
| `GET` | `/api/employees/{id}/salary/history` | Bearer token | All salary records, newest first (`[]` if none) |
| `POST` | `/api/employees/{id}/salary` | Bearer token | Add a salary record → `201` |
| `PUT` | `/api/employees/{id}/salary/{salaryId}` | Bearer token | Correct a record's amount and currency |
| `GET` | `/api/analytics/overview` | Bearer token | Current-salary statistics per currency |
| `GET` | `/api/analytics/by-country` | Bearer token | Current-salary statistics per country and currency |
| `GET` | `/api/analytics/by-department` | Bearer token | Current-salary statistics per department and currency |

Employees are read-only. `/api/employees` takes these optional query parameters:

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `page` | `0` | Zero-based page index (≥ 0) |
| `size` | `20` | Page size, 1–100 |
| `search` | | Case-insensitive partial match on employee code, first/last/full name or email |
| `country` | | ISO 3166-1 alpha-2 code, e.g. `US` (any letter case) |
| `department` | | Exact department name, e.g. `Engineering` (any letter case) |
| `status` | | `ACTIVE`, `ON_LEAVE` or `TERMINATED` |

Results are always ordered by last name, first name, then id.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"hr@acme.com","password":"<your password>"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl -s localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
curl -s "localhost:8080/api/employees?search=john&country=US&department=Engineering&status=ACTIVE&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/employees/1 -H "Authorization: Bearer $TOKEN"
```

A page looks like:

```json
{
  "content": [
    { "id": 1, "employeeCode": "EMP-00001", "firstName": "Amelia", "lastName": "Moore",
      "email": "amelia.moore1@acme.example", "jobTitle": "Account Executive",
      "department": "Sales", "countryCode": "DE", "employmentStatus": "ACTIVE",
      "hireDate": "2018-04-27" }
  ],
  "page": 0, "size": 20, "totalElements": 10000, "totalPages": 500,
  "hasNext": true, "hasPrevious": false
}
```

### Salary

The **current salary** is the record with the latest `effectiveDate` on or before today.
Future-dated records appear in the history and become current on their date.

```bash
curl -s localhost:8080/api/employees/1/salary -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/employees/1/salary/history -H "Authorization: Bearer $TOKEN"

# Add a salary change (past, current or future date; at most one year ahead)
curl -s -X POST localhost:8080/api/employees/1/salary -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 58000.00, "currency": "EUR", "effectiveDate": "2026-10-01"}'

# Correct an existing record: amount and currency only; the effective date cannot change
curl -s -X PUT localhost:8080/api/employees/1/salary/<salaryId> -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 58500.00, "currency": "EUR"}'
```

A salary record looks like `{"id": 15, "amount": 900000.00, "currency": "INR", "effectiveDate": "2026-01-01"}`.

Rules:
- `amount` must be greater than 0 with at most 2 decimal places.
- `currency` must be an ISO 4217 code (any letter case).
- On `POST`, `effectiveDate` must be between the hire date and one year ahead.
- There is at most one record per employee per date; a duplicate gets `409`.
- Terminated employees can't get new records (`409`), but their records can be corrected.
- Salary records are never deleted, and there is no currency conversion.

### Analytics

Statistics over **current salaries** (latest `effectiveDate` on or before today),
excluding terminated employees. They are **always per currency**: amounts in different
currencies are never combined, and there is no currency conversion.

```bash
curl -s localhost:8080/api/analytics/overview      -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/analytics/by-country    -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/analytics/by-department -H "Authorization: Bearer $TOKEN"
```

With the `dev` seed data, the overview begins:

```json
{
  "generatedAt": "2026-09-20T08:30:00.123Z",
  "asOfDate": "2026-09-20",
  "currencies": [
    { "currency": "AUD", "employeeCount": 915, "averageSalary": 139545.90, "medianSalary": 138000.00,
      "minimumSalary": 69700.00, "maximumSalary": 229700.00 },
    …
  ]
}
```

- `by-country` rows look like
  `{ "country": "IN", "currency": "USD", "employeeCount": 33, "averageSalary": … }`.
  India has separate INR and USD rows.
- `by-department` rows have `department` instead of `country`.
- Rows are ordered by group, then currency.
- Average and median are rounded to 2 decimal places, half up. An even-sized group's
  median is the mean of the two middle values.
- If no one has a current salary, the results are empty (`"currencies": []` or `[]`),
  with `200`.

Errors use RFC 9457 Problem Details (`application/problem+json`):

- `400` for malformed or invalid input, with an `errors` object per field. Unknown JSON
  fields (e.g. `id`, or `effectiveDate` on a correction) are rejected.
- `401` for bad credentials or a missing, invalid or expired token
- `403` for a missing role
- `404` for an unknown employee or salary record, or no salary in force yet
- `409` for a duplicate effective date or a terminated employee

## Running the tests

```bash
cd backend
./mvnw test      # unit and web-slice tests: fast, no Docker or database needed
./mvnw verify    # also runs *IT integration tests against a Testcontainers MySQL (needs Docker)
```

Integration tests start a throwaway `mysql:8.4` container automatically and load the
demo seed into it. No local database or user setup is required.

## Frontend

`frontend/` is an **Angular 19.2** application built with standalone components, signals,
strict TypeScript and **Angular Material 19**. It is the HR Manager's user interface for
everything the API offers.

### Setup and running

Start the backend first (see [Running the backend](#running-the-backend)). It must be on
port 8080. Then:

```bash
cd frontend
npm install
npm start          # ng serve on http://localhost:4200
```

The development server **proxies `/api` to `http://localhost:8080`** (`proxy.conf.json`).
The browser therefore only talks to one origin, and the backend needs no CORS
configuration. The API base path `/api` is configured in one place,
`src/environments/environment.ts`. To use a backend on another port, change the `target`
in `proxy.conf.json`.

Sign in with the `ACME_INITIAL_HR_EMAIL` / `ACME_INITIAL_HR_PASSWORD` account.

### Routes

| Route | Page |
|-------|------|
| `/login` | Sign in (the only public page) |
| `/dashboard` | Per-currency compensation summary, as of the backend's UTC date |
| `/employees` | Employee directory with server-side paging, debounced search and filters |
| `/employees/:id` | Read-only employee details |
| `/employees/:id/salary` | Current salary, salary history (Current/Future/Past), add and correct salary |
| `/analytics` | Overview, by-country and by-department statistics, each per currency |

All routes except `/login` require a signed-in HR Manager. Unknown paths go to `/dashboard`.

### Authentication

- The login form posts to `POST /api/auth/login`. The returned access token and its
  `expiresAt` are kept in **`sessionStorage`**; the password is never stored.
- An HTTP interceptor adds `Authorization: Bearer <token>` to `/api` requests, except
  login.
- A route guard sends signed-out users to `/login` and returns them to the page they
  asked for afterwards.
- On any `401` from the backend, the session is cleared and the user returns to `/login`
  with "Your session has expired. Please sign in again."
- The JWT is never decoded in the browser. The backend decides every request.
- **Why `sessionStorage`:** the token survives a page reload but not closing the tab or
  browser, so it is not kept on a shared machine.
  - The trade-off is that scripts on the page can read it, so a cross-site-scripting
    flaw could steal it for its 1-hour lifetime. Angular's automatic output escaping and
    the short lifetime limit that risk.
  - An `HttpOnly` cookie would hide the token from scripts, but it needs backend changes
    and CSRF protection.
  - There are no refresh tokens (the backend has none); users sign in again after
    expiry.

### Tests and build

```bash
cd frontend
npm test           # unit and component tests, once, in headless Chrome (Karma + Jasmine)
npm run test:watch # the same, re-running on change
npm run lint       # Angular ESLint, including template accessibility rules
npm run build      # production build into frontend/dist/frontend/browser
```

The production build is a static site. Serve `dist/frontend/browser` from the same origin
as the API (or behind the same reverse proxy), routing unknown paths to `index.html`.

## Configuration

| Variable                   | Default                                   | Purpose                                              |
|----------------------------|-------------------------------------------|------------------------------------------------------|
| `ACME_DB_URL`              | `jdbc:mysql://localhost:3306/acme_salary` | Application database                                 |
| `ACME_DB_USERNAME`         | *none*                                    | Database user                                        |
| `ACME_DB_PASSWORD`         | *none*                                    | Database password                                    |
| `ACME_JWT_SECRET`          | *none; required*                          | JWT signing secret, at least 32 bytes                |
| `ACME_INITIAL_HR_EMAIL`    | *none*                                    | Initial HR Manager email (created if absent)         |
| `ACME_INITIAL_HR_PASSWORD` | *none*                                    | Initial HR Manager password, 12 characters to 72 bytes |
| `SPRING_PROFILES_ACTIVE`   | *none*                                    | `dev` loads the demo employees and salary histories |

Only the database URL has a default, which points at a local MySQL. No credentials or
secrets have defaults.
