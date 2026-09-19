# Database Design

MySQL 8, InnoDB, `utf8mb4` with the `utf8mb4_0900_ai_ci` collation. The schema is
created only by Flyway migrations in `backend/src/main/resources/db/migration`, and
Hibernate validates against it. `app_user` and `employee` exist; `salary_record` is the
planned design and lands with the salary history step. Measured query plans are in
[performance.md](performance.md).

## Entity relationships

```
┌────────────────────┐
│ app_user           │   Stands alone: HR Manager login accounts.
│  id (PK)           │
│  email (UK)        │
│  password_hash     │
│  role              │
└────────────────────┘

┌────────────────────┐ 1          0..* ┌──────────────────────┐
│ employee           │────────────────▶│ salary_record        │
│  id (PK)           │                 │  id (PK)             │
│  employee_code (UK)│                 │  employee_id (FK)    │
│  email (UK)        │                 │  amount, currency    │
│  …                 │                 │  effective_date      │
└────────────────────┘                 └──────────────────────┘
```

- **employee 1 → 0..\* salary_record.** An employee has a full salary history.
  "Current salary" is derived, not stored (see [architecture.md](architecture.md#salary-history-model)).
- **app_user** holds HR Manager login accounts. Employees are not users.

## Tables

### `app_user`

| Column | Type | Null | Notes |
|--------|------|:---:|-------|
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary key |
| `email` | `VARCHAR(255)` | No | Login name. Unique. |
| `password_hash` | `VARCHAR(100)` | No | BCrypt hash (60 chars). Room for an algorithm prefix such as `{bcrypt}`. Never plain text. |
| `role` | `VARCHAR(30)` | No | `CHECK (role IN ('HR_MANAGER'))` |

### `employee`

| Column | Type | Null | Notes |
|--------|------|:---:|-------|
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary key. Used in API URLs (`/api/employees/{id}`). |
| `employee_code` | `VARCHAR(20)` | No | Business ID shown to users, e.g. `EMP-00042`. Unique. |
| `first_name` | `VARCHAR(100)` | No | |
| `last_name` | `VARCHAR(100)` | No | |
| `email` | `VARCHAR(255)` | No | Unique. Case-insensitive through the collation, so `A@x` and `a@x` collide. |
| `job_title` | `VARCHAR(100)` | No | |
| `department` | `VARCHAR(100)` | No | Plain column; see [Design notes](#design-notes). |
| `country_code` | `CHAR(2)` | No | ISO 3166-1 alpha-2 format, `CHECK (REGEXP_LIKE(country_code, '^[A-Z]{2}$', 'c'))`. The `'c'` flag makes the check case-sensitive despite the `_ci` collation, so `us` is rejected. The check enforces the format; whether a code is a real country is up to the data source. |
| `employment_status` | `VARCHAR(20)` | No | `CHECK (employment_status IN ('ACTIVE','ON_LEAVE','TERMINATED'))`. `VARCHAR` rather than MySQL `ENUM`, so adding a status is a simple constraint change. |
| `hire_date` | `DATE` | No | Java `LocalDate` |

Read-only in v1 (the entity is Hibernate `@Immutable`). Created by
`V2__create_employee.sql`.

### `salary_record`

| Column | Type | Null | Notes |
|--------|------|:---:|-------|
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary key |
| `employee_id` | `BIGINT` | No | FK → `employee(id)`, `ON DELETE RESTRICT` |
| `amount` | `DECIMAL(15,2)` | No | Annual gross base salary. `CHECK (amount > 0)` |
| `currency` | `CHAR(3)` | No | ISO 4217, `CHECK (currency REGEXP '^[A-Z]{3}$')`. The application also validates against `java.util.Currency`. |
| `effective_date` | `DATE` | No | Date the salary takes effect. Can be in the future. Not updatable. |

A new salary change inserts a row. A correction updates `amount` and `currency` on the
existing row for that effective date. Rows are never deleted.

## Constraints

| Table | Constraint | Purpose |
|-------|-----------|---------|
| `app_user` | `uk_app_user_email UNIQUE (email)` | One account per email; fast login lookup |
| `app_user` | `ck_app_user_role` | Only known roles |
| `employee` | `uk_employee_code UNIQUE (employee_code)` | Business ID is unique |
| `employee` | `uk_employee_email UNIQUE (email)` | No two employees share an email |
| `employee` | `ck_employee_status`, `ck_employee_country_code` | Only valid statuses and code formats |
| `salary_record` | `fk_salary_record_employee FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE RESTRICT` | No orphaned salaries. Salary history can never be removed by deleting an employee. |
| `salary_record` | `uk_salary_record_employee_date UNIQUE (employee_id, effective_date)` | One salary per employee per date, so "salary on date X" is unambiguous. A mistake is corrected by updating this row, not by adding another. |
| `salary_record` | `ck_salary_record_amount`, `ck_salary_record_currency` | Last line of defence behind application validation |

MySQL enforces `CHECK` constraints from version 8.0.16. Database constraints back up
application validation, and they do not replace it: the API returns friendly `400`
and `409` errors before any constraint is hit.

## Indexes

Each index maps to a query the application actually runs. The employee indexes were
checked with `EXPLAIN ANALYZE` against the 10,000-row seed (see
[performance.md](performance.md)).

| Index | Columns | Serves |
|-------|---------|--------|
| `PRIMARY` | `employee(id)` | Employee detail (`/api/employees/{id}`) |
| `uk_employee_code` | `employee(employee_code)` | Uniqueness. It does *not* speed up search, because `LIKE '%term%'` cannot use it. |
| `uk_employee_email` | `employee(email)` | Uniqueness, case-insensitive. Also not used by substring search. |
| `idx_employee_name` | `employee(last_name, first_name)` | The default list order `last_name, first_name, id`. InnoDB appends the primary key to every secondary index, so this is effectively `(last_name, first_name, id)`, and a page is read in index order with no sort step. Search and status-filtered lists also walk this index and stop once the page is full. |
| `idx_employee_country_name` | `employee(country_code, last_name, first_name)` | Country filter, returned in list order without a sort. The leading column also serves plain country lookups and per-country analytics later. |
| `idx_employee_department_name` | `employee(department, last_name, first_name)` | Department filter, as above. |
| `uk_salary_record_employee_date` *(planned)* | `salary_record(employee_id, effective_date)` | Salary history for one employee (newest first); current salary lookup; `PARTITION BY employee_id ORDER BY effective_date` in analytics. It also serves as the index for the foreign key. |
| `uk_app_user_email` | `app_user(email)` | Login |

**Why the list indexes don't declare `id`.** The list is ordered by
`last_name, first_name, id`, so we evaluated declaring `id` explicitly as the last index
column, e.g. `(country_code, last_name, first_name, id)`. It isn't needed:

- InnoDB stores every secondary-index entry with the primary key appended, so entries are
  already ordered by `(…, last_name, first_name, id)`.
- `EXPLAIN ANALYZE` on the 10,000-row seed confirms it. The default, country and
  department lists read exactly `offset + size` index entries in order, with **no sort
  step** (see [performance.md](performance.md#index-order-and-the-id-tie-breaker)).
- The plans are the same even with `optimizer_switch='use_index_extensions=off'`.
- The integration test `employeesWithTheSameNameAreOrderedById` checks the tie-break
  against MySQL.

Declaring `id` would produce the same physical order, so it would only document intent.
It would matter only on a storage engine that doesn't cluster by primary key, and this
schema is InnoDB-only.

**Evaluated and not added:**

- **`employment_status`.** It has three values, and about 90% of rows are `ACTIVE`, so
  MySQL would not use an index for the common case. Filtering by the rarer statuses
  walks `idx_employee_name` and stops once a page is full (2.7 ms measured for
  `TERMINATED`).
- **Single-column country and department indexes.** The composite versions above serve
  the same filters *and* the sort order, so separate indexes would be redundant.
- **Full-text or n-gram search indexes.** Substring search over 10,000 rows takes
  single-digit milliseconds without them.

## Employee list query

A list request runs two statements, generated by Spring Data from a JPA `Specification`.
Every condition is optional:

```sql
SELECT id, employee_code, first_name, last_name, email, job_title, department,
       country_code, employment_status, hire_date
FROM employee
WHERE (employee_code LIKE :term ESCAPE '!' OR first_name LIKE :term ESCAPE '!'
       OR last_name LIKE :term ESCAPE '!'
       OR CONCAT(first_name, ' ', last_name) LIKE :term ESCAPE '!'
       OR email LIKE :term ESCAPE '!')              -- search, :term = '%john%'
  AND country_code = :country                       -- country filter
  AND department = :department                      -- department filter
  AND employment_status = :status                   -- status filter
ORDER BY last_name, first_name, id
LIMIT :offset, :size;

SELECT COUNT(id) FROM employee WHERE <same conditions>;
```

Spring Data skips the count when the first page already holds every match.

## Key queries

**Current salary for one employee** (a single index range read on
`uk_salary_record_employee_date`):

```sql
SELECT amount, currency, effective_date
FROM salary_record
WHERE employee_id = :employeeId
  AND effective_date <= :today
ORDER BY effective_date DESC
LIMIT 1;
```

**Current salaries for analytics** (one pass over salary history, grouped per currency):

```sql
WITH current_salary AS (
    SELECT employee_id, amount, currency,
           ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY effective_date DESC) AS rn
    FROM salary_record
    WHERE effective_date <= :today
)
SELECT e.country_code, cs.currency,
       COUNT(*) AS headcount, MIN(cs.amount), MAX(cs.amount), AVG(cs.amount)
FROM current_salary cs
JOIN employee e ON e.id = cs.employee_id
WHERE cs.rn = 1
  AND e.employment_status <> 'TERMINATED'
GROUP BY e.country_code, cs.currency;
```

`:today` is always passed in from the application's `Clock`, never `CURRENT_DATE`, so
the result doesn't depend on the database server's time zone and tests can fix the date.

## Design notes

- **Surrogate `BIGINT` keys plus a business `employee_code`.** Internal IDs never
  change, and the business ID stays readable and searchable.
- **Country and department are columns, not lookup tables.** There is no maintenance
  UI for them in v1, so lookup tables would add joins and entities with no behaviour.
  Filter dropdowns come from `SELECT DISTINCT` on indexed columns. If departments gain
  attributes later (head, cost centre), a table can be introduced then.
- **No stored "current salary" column.** It would go stale the moment a future-dated
  record took effect, unless a scheduled job updated it. Deriving it keeps a single
  source of truth.
- **No optimistic-locking `version` columns.** Employees are read-only in v1. The unique
  constraint catches two HR Managers adding the same effective date at once.
  Simultaneous corrections to the same record are last-write-wins, which is acceptable
  for a small HR team.
- **`DECIMAL(15,2)`** holds up to 9,999,999,999,999.99, enough for annual salaries in
  high-denomination currencies such as IDR or VND. Java maps it to `BigDecimal`.
- **No audit or timestamp columns** (`created_at`, `created_by` and similar). v1 has no
  feature that reads them, and audit logging is out of scope. They can be added by a
  later migration if they become required.
- **Collation `utf8mb4_0900_ai_ci`** is declared explicitly on each table, so
  case-insensitive search behaves the same regardless of server defaults. It is also
  accent-insensitive, so a search for `jose` would match `José`.
- **`CHECK` violations surface as generic errors.** Spring reports MySQL error 3819
  (check constraint violated) as `UncategorizedSQLException`, not
  `DataIntegrityViolationException`. That doesn't matter while employees are
  read-only, but a future write path should validate before inserting.

## Migration plan

| Migration | Increment | Contents |
|-----------|-----------|----------|
| `V1__create_app_user.sql` | 2: Authentication *(done)* | `app_user` |
| `V2__create_employee.sql` | 3: Employee domain *(done)* | `employee` with its constraints and indexes |
| `V3__create_salary_record.sql` | Salary history | `salary_record` with its constraints and index |

Seed data is **not** a migration (see [architecture.md](architecture.md#seed-strategy)).
