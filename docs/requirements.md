# Requirements

## Problem

ACME has about 10,000 employees across several countries. HR keeps salary information
in spreadsheets. That makes it hard to keep a reliable history of pay changes, to find
people quickly, and to understand compensation across countries and departments.

This application gives an HR Manager one secure place to look up employees, record
salary changes over time, and see how compensation is distributed.

## Users

| Role | Description |
|------|-------------|
| **HR Manager** (`HR_MANAGER`) | The only role in v1. Can view all employees, record salary changes and view analytics. |

## In scope

### 1. HR Manager login

- An HR Manager signs in with an email address and password.
- Passwords are stored only as BCrypt hashes. Plain-text passwords are never stored or logged.
- There is no self-registration. The initial HR Manager account is created at startup
  from environment variables (`ACME_INITIAL_HR_EMAIL`, `ACME_INITIAL_HR_PASSWORD`), only
  if it does not already exist. No credentials or password hashes are committed to
  source control.
- A failed login returns `401 Unauthorized` with the same message whether the email or
  the password was wrong, so attackers cannot discover which emails exist.

### 2. JWT authentication

- A successful login returns a signed JWT access token with a short lifetime (1 hour).
- All employee, salary and analytics APIs require a valid token and the `HR_MANAGER` role.
- A missing, invalid or expired token returns `401 Unauthorized`. A valid token without
  the required role returns `403 Forbidden`.
- The signing secret comes from the `ACME_JWT_SECRET` environment variable. The
  application refuses to start without one.

### 3. Employee search, filtering and pagination

- The employee list is paginated on the server. The default page size is 20 and the
  maximum is 100. An invalid page or size returns `400`.
- Free-text search matches employee name (first, last or full), email or employee ID
  (e.g. `EMP-00042`). Matching is case-insensitive and finds partial text anywhere in
  the value.
- Results can be filtered by country, department and employment status
  (`ACTIVE`, `ON_LEAVE`, `TERMINATED`). Filters combine with each other and with search.
- Results come in a fixed order (last name, first name, then employee record id), which
  is stable across pages. Client-chosen sorting is not part of v1.
- The HR Manager can open an employee to see their details. Their current salary is
  added with salary management.
- Missing employees return `404`.

### 4. Salary management

- The HR Manager records a salary change for an employee: **amount**, **currency** and
  **effective date**. A successful change returns `201 Created`. The record's id and
  creation time are set by the server; a request that sends them is rejected.
- Validation rules:
  - The amount is required and greater than zero, with at most 2 decimal places and
    at most 13 digits before the decimal point.
  - The currency is required and must be a valid ISO 4217 code (e.g. `USD`, `EUR`, `INR`).
    It is accepted in any letter case and stored upper-case.
  - The effective date is required and may be in the past, today or the future. It
    cannot be before the employee's hire date or more than one year in the future.
  - Only one salary record is allowed per employee per effective date. A duplicate
    returns `409 Conflict`.
  - Salary changes cannot be recorded for `TERMINATED` employees (`409 Conflict`).
- The HR Manager can **correct** an existing salary record, for example to fix a typo in
  the amount, by updating its amount and currency.
  - The effective date identifies the record and cannot be changed. A correction that
    includes one is rejected with `400`.
  - Corrections follow the same amount and currency validation.
  - Corrections are also allowed for terminated employees, because they fix historical
    data.
  - A record can only be corrected through its own employee's URL; otherwise the answer
    is `404`.
- Invalid input returns `400 Bad Request` with the error for each field. A missing
  employee or salary record returns `404 Not Found`.

### 5. Salary history

- A salary change **adds a new record**. It never overwrites the records for earlier
  dates, so the full history of changes is kept.
- Salary records are never deleted.
- Each record stores when it was created (`created_at`, UTC). Corrections do not change it.
- The HR Manager can view an employee's full salary history, newest first. An employee
  with no salary records has an empty history.
- An employee's **current salary** is the record with the latest effective date that
  is on or before today. If no record has taken effect yet, the API returns `404`
  rather than inventing a salary.
- **Future-dated changes are supported** (e.g. an approved raise effective next month).
  They appear in the history as scheduled and become current automatically on their
  effective date.

### 6. Multi-currency support

- Each salary record has its own currency, so different employees can be paid in
  different currencies. This includes, for example, an employee in Germany paid in USD.
- Amounts are always shown with their currency code.
- Any ISO 4217 code known to the Java platform is accepted. There is no fixed shortlist
  and no currency table.
- The application never converts between currencies and never calls an exchange-rate
  service (see Out of scope).

### 7. Compensation analytics

- Analytics use each employee's **current salary**: the record with the latest effective
  date on or before today. Future-dated and superseded records are ignored. Employees
  with no current salary, and `TERMINATED` employees, are not counted.
- **Every figure is calculated separately per currency.** Amounts in different
  currencies are never added together or averaged together, and nothing is converted.
- For each currency: headcount, minimum, maximum, average and median salary. Average
  and median are rounded to 2 decimal places, half up. For an even count, the median is
  the mean of the two middle values.
- Three views, all for the HR Manager only:
  - overview per currency (`GET /api/analytics/overview`)
  - per **country** and currency (`GET /api/analytics/by-country`)
  - per **department** and currency (`GET /api/analytics/by-department`)

  A country or department with employees paid in two currencies has two rows.
- If no one has a current salary, the views return empty results with `200`, not zeros
  or `404`.
- Filtering, date ranges, exports and charts are not part of the analytics API. Charts
  belong to the UI.

### 8. Seed data

- The application can load **10,000 deterministic seed employees with deterministic
  salary histories** for development and demonstration. Salary histories are included
  so that compensation analytics can be demonstrated as soon as it exists, without
  first entering thousands of salaries by hand.
- **Volume:** 10,000 employees and about 24,901 salary records (exactly 24,901 with
  the current generator).
- "Deterministic" means that every run produces exactly the same employees, salaries
  and dates, so demos, screenshots and bug reports can be reproduced.
- The seed data covers several countries and currencies, all departments, all
  employment statuses, multi-entry salary histories and some future-dated changes.
  Every employee has a salary from their hire date, and about 5% have a raise scheduled
  for 2027-01-01.
- **Development and demo data only.** Seeding is off by default and runs only with the
  `dev` profile (or in the integration-test context). Neither employees nor salaries are
  ever generated automatically in production. Each table is loaded only while empty, so
  restarts never duplicate data.

## Non-functional requirements

| Area | Requirement |
|------|-------------|
| Security | BCrypt password hashing, JWT on every protected API, secrets supplied through the environment, no stack traces in API errors. |
| Data integrity | Money is stored as exact decimals (`DECIMAL`), never floating point. Database constraints back up application validation. |
| Performance | Employee list, search and analytics respond in well under a second with 10,000 employees on a developer laptop. |
| API quality | Consistent JSON error format (RFC 9457 Problem Details) and correct HTTP status codes. JPA entities are never exposed through the API. |
| Maintainability | Clear separation of controllers, services, repositories, DTOs and entities. Simple solutions preferred over abstractions. |
| Testability | Meaningful unit and integration tests. Integration tests need no manual database setup. |

## Out of scope

These are deliberately excluded so that v1 stays focused on the core problem of
reliable salary records and compensation insight.

| Excluded | Why |
|----------|-----|
| **Payroll processing** (pay runs, payslips, payments) | Payroll is a separate, regulated system. This application manages salary *records*, not payments. |
| **Taxes and deductions** | Tax rules differ by country and change often. Supporting them would be most of the product rather than a feature. |
| **Benefits** (pensions, insurance, allowances, bonuses) | Base salary is the requirement. Other compensation types each need their own model and rules. |
| **Single sign-on** (SAML, OIDC, corporate identity provider) | Email and password login is enough for one HR role. The JWT design leaves room to add an identity provider later. |
| **Complex role-based access control** (multiple roles, per-country or per-department permissions) | v1 has a single `HR_MANAGER` role. Fine-grained permissions add significant design and testing work that the problem doesn't need yet. |
| **Live currency conversion** | Needs an external exchange-rate source, a rate history, and a policy for which date's rate applies. Reporting per currency avoids misleading converted totals. |
| **Excel import and export** | Bulk import needs its own validation, error reporting and partial-failure handling. The seed data covers the need for realistic volume in v1. |
| **Employee record maintenance** (creating, editing or deleting employees) | The scope is salary management. Employee master data is treated as coming from the HR system of record (the seed data in v1). |
| **Deleting salary records, changing a record's effective date** | Deleting records or moving them between dates would rewrite history. Mistakes in an amount or currency are fixed by correcting the record for its date. |
| **Audit log** (who changed what and when, including previous values of corrected records) | Not required for v1. It can be added later without changing how salaries are stored. |
| **Password reset, self-registration, refresh tokens** | Not needed for a small set of provisioned HR Managers. Short-lived tokens plus re-login are enough for v1. |
| **Login rate limiting and account lockout** | Worth adding before production exposure, but usually handled at the gateway or infrastructure level rather than in the application. |

## Assumptions

- Salary amounts are **annual gross base salary**.
- "Today", for deciding the current salary, is the current date in UTC.
- Employees are identified to users by a business employee ID (`EMP-00001` … `EMP-10000`),
  separate from the internal database key.
- Country is stored as an ISO 3166-1 alpha-2 code (e.g. `GB`) and shown with its full name in the UI.
