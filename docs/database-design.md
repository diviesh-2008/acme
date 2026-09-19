# Database Design

MySQL 8, InnoDB, `utf8mb4` with the `utf8mb4_0900_ai_ci` collation. The schema is
created only by Flyway migrations in `backend/src/main/resources/db/migration`, and
Hibernate validates against it. This document is the proposed design. Each table's
DDL lands in the increment that first needs it.

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
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary key. Internal only. |
| `employee_code` | `VARCHAR(20)` | No | Business ID shown to users, e.g. `EMP-00042`. Unique. |
| `first_name` | `VARCHAR(100)` | No | |
| `last_name` | `VARCHAR(100)` | No | |
| `email` | `VARCHAR(255)` | No | Unique |
| `job_title` | `VARCHAR(100)` | No | |
| `department` | `VARCHAR(100)` | No | Plain column; see [Design notes](#design-notes). |
| `country_code` | `CHAR(2)` | No | ISO 3166-1 alpha-2, `CHECK (country_code REGEXP '^[A-Z]{2}$')` |
| `employment_status` | `VARCHAR(20)` | No | `CHECK (employment_status IN ('ACTIVE','ON_LEAVE','TERMINATED'))` |
| `hire_date` | `DATE` | No | |

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

Each index maps to a query the application actually runs.

| Index | Columns | Serves |
|-------|---------|--------|
| `PRIMARY` | `employee(id)` | Employee detail; joins |
| `uk_employee_code` | `employee(employee_code)` | Uniqueness; lookup by business ID |
| `uk_employee_email` | `employee(email)` | Uniqueness; lookup by email |
| `idx_employee_name` | `employee(last_name, first_name)` | Default list order (`last_name, first_name, id`) with `LIMIT` |
| `idx_employee_country` | `employee(country_code)` | Country filter; analytics by country |
| `idx_employee_department` | `employee(department)` | Department filter; analytics by department; filter-option `DISTINCT` |
| `idx_employee_status` | `employee(employment_status)` | Status filter; analytics exclude `TERMINATED` |
| `uk_salary_record_employee_date` | `salary_record(employee_id, effective_date)` | Salary history for one employee (newest first); current salary lookup; `PARTITION BY employee_id ORDER BY effective_date` in analytics. It also serves as the index for the foreign key. |
| `uk_app_user_email` | `app_user(email)` | Login |

**Search note.** Substring search (`LIKE '%term%'`) cannot use a B-tree index, so it
scans the 10,000 employee rows. That takes single-digit milliseconds, so full-text
indexing isn't justified at this size. Single-column filter indexes are cheap to
maintain. The optimizer picks the most selective one, and the plans will be checked
with `EXPLAIN` when the query API is built.

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
  case-insensitive search behaves the same regardless of server defaults.

## Migration plan

| Migration | Increment | Contents |
|-----------|-----------|----------|
| `V1__create_app_user.sql` | 3: Authentication | `app_user` |
| `V2__create_employee_and_salary_record.sql` | 4: Employee schema | `employee`, `salary_record`, all indexes and constraints above |

Seed data is **not** a migration (see [architecture.md](architecture.md#seed-strategy)).
