# Performance

How the employee, salary and analytics APIs stay fast at ACME's size (~10,000
employees), what was measured, and where the limits are. There is deliberately no caching, search engine or read replica.
A well-indexed MySQL table with paging in the database is enough at this scale.

## Principles

- **Paging, filtering and ordering happen in MySQL.** The API never loads all employees
  into Java. Each list request fetches at most one page (max 100 rows) plus a `COUNT`.
- **Page size is capped at 100.** Larger requests get `400`, so no client can pull the
  whole table in one call.
- **No N+1 queries.** `Employee` has no associations. A list page is one `SELECT … LIMIT`
  and one `COUNT`; a detail request is one primary-key lookup.
- **Only needed columns.** The table has exactly the ten columns the API returns, so
  selecting the entity *is* selecting the response. A separate projection would add code
  without reading less data.
- **Read-only transactions.** `EmployeeService` is `@Transactional(readOnly = true)` and
  `Employee` is `@Immutable`, so Hibernate keeps no dirty-checking snapshots and never
  flushes.

## Generated SQL

Captured from Hibernate's SQL log (`logging.level.org.hibernate.SQL=debug`):

```sql
-- GET /api/employees?page=2&size=20
select e1_0.id, e1_0.country_code, … , e1_0.last_name from employee e1_0
order by e1_0.last_name, e1_0.first_name, e1_0.id limit ?, ?
select count(e1_0.id) from employee e1_0

-- GET /api/employees?search=john&country=US&department=Engineering&status=ACTIVE
select … from employee e1_0
where (e1_0.employee_code like ? escape '!' or e1_0.first_name like ? escape '!'
       or e1_0.last_name like ? escape '!'
       or concat(concat(e1_0.first_name, ?), e1_0.last_name) like ? escape '!'
       or e1_0.email like ? escape '!')
  and e1_0.country_code = ? and e1_0.department = ? and e1_0.employment_status = ?
order by e1_0.last_name, e1_0.first_name, e1_0.id limit ?, ?
select count(e1_0.id) from employee e1_0 where <same conditions>

-- GET /api/employees/42
select … from employee e1_0 where e1_0.id = ?
```

All values are bind parameters. Search text is escaped (`!` is the escape character), so
a user typing `%` or `_` matches that literal character.

## Measured plans

`EXPLAIN ANALYZE` on MySQL 8.0.46 with the 10,000-employee seed, on a developer laptop:

| Request | Plan | Time |
|---------|------|-----:|
| Default list, page 3 | Index scan on `idx_employee_name`, stops after 60 rows; no sort | 0.27 ms |
| Default list, last page (offset 9,980) | Table scan + sort | 39 ms |
| Total count (no filters) | Count rows | 4.7 ms |
| `country=US` | Index lookup on `idx_employee_country_name`; no sort | 0.33 ms |
| `department=Engineering` | Index lookup on `idx_employee_department_name`; no sort | 0.40 ms |
| `status=TERMINATED` | Walks `idx_employee_name`, filters until 20 matches (402 rows read) | 2.7 ms |
| `search=john` | Walks `idx_employee_name`, filters until 20 matches (1,041 rows read) | 6.5 ms |
| search + country + department + status | Lookup on `idx_employee_country_name`, filters the rest | 7.7 ms |
| `GET /api/employees/42` | Primary-key lookup | < 0.1 ms |

The composite filter indexes return rows already in list order, so MySQL reads 20 index
entries instead of sorting every match. See [database-design.md](database-design.md#indexes)
for why each index exists and which were rejected.

### Index order and the `id` tie-breaker

The list is ordered by `last_name, first_name, id`, but no index declares `id`. We
re-checked on MySQL 8.0.46 with the seed:

| Query (`ORDER BY last_name, first_name, id`) | Plan | Rows read | Time |
|---|---|---:|---:|
| Default list, page 0 | Index scan `idx_employee_name`, no sort | 20 | 0.13 ms |
| Default list, page 3 | Index scan `idx_employee_name`, no sort | 60 | 0.32 ms |
| `country=US`, page 0 | Index lookup `idx_employee_country_name`, no sort | 20 | 0.30 ms |
| `country=US`, page 50 | Index lookup `idx_employee_country_name`, no sort | 1,020 | 3.7 ms |
| `department=Engineering` | Index lookup `idx_employee_department_name`, no sort | 20 | 0.19 ms |
| `department=Legal&country=IN` | Department index lookup + country filter, no sort | 138 | 0.83 ms |

MySQL reads exactly `offset + size` entries and never sorts. InnoDB appends the primary
key to each secondary-index entry, so the entries are already in `(…, id)` order. The
plans don't change with `use_index_extensions=off`. Declaring `id` in the indexes would
change nothing, so they are left as they are.

### Queries per request

Counted from Hibernate's SQL log against the seeded database:

| Request | SQL statements |
|---|---|
| `GET /api/employees?page=3&size=20` | 2: the page (`… LIMIT 60, 20`) and `COUNT` |
| `GET /api/employees?search=…&country=…&department=…&status=…` | 2: the page and `COUNT`, with every condition in `WHERE` |
| `GET /api/employees/42` | 1: primary-key lookup |

The count doesn't depend on page size, so there is no N+1. The application only ever
receives one page of rows (at most 100).

## Salary queries

Every salary query is scoped to one employee and uses the unique
`(employee_id, effective_date)` index. Measured on MySQL 8.0.46 with the seed of
10,000 employees and 24,901 salary records:

| Request | SQL statements (from Hibernate's log) | Plan of the salary query | Time |
|---------|---------------------------------------|--------------------------|-----:|
| `GET …/salary` (current) | 2: employee exists (`count` by primary key), then `… WHERE employee_id = ? AND effective_date <= ? ORDER BY effective_date DESC LIMIT 1` | Reverse index range scan, stops after 1 row | 0.03 ms |
| `GET …/salary/history` | 2: employee exists, then `… WHERE employee_id = ? ORDER BY effective_date DESC` | Reverse index lookup, no sort | 0.04 ms |
| `POST …/salary` | 3: load employee (hire date, status), duplicate check (unique-index lookup), `INSERT` | Single-row lookups | < 0.1 ms |
| `PUT …/salary/{id}` | 3: employee exists, load record by `id AND employee_id`, `UPDATE salary_record SET amount = ?, currency = ? WHERE id = ?` | Primary-key lookup | < 0.1 ms |

- No request touches another employee's rows. `SalaryRecord` holds `employeeId` as a
  plain column, not a JPA association, so there is no lazy loading and no N+1.
- The correction's `UPDATE` sets only `amount` and `currency`. `employee_id`,
  `effective_date` and `created_at` are mapped `updatable = false`.
- An employee has at most a handful of salary records (1–5 in the seed), so returning the
  full history unpaged is fine.
- The seed inserts 24,901 salary records in about 2–3 seconds (JDBC batches of 1,000).

## Analytics queries

Each analytics request runs **one** SQL statement (see
[database-design.md](database-design.md#key-queries)). MySQL computes counts, sums,
minimums, maximums and medians. The application receives one row per group (7 rows for
the overview, 15 by country and 63 by department on the seed), never salary history.

`EXPLAIN ANALYZE` of the by-country query on MySQL 8.0.46, with 10,000 employees and
24,901 salary records:

| Step | Rows | Cumulative time |
|------|-----:|----------------:|
| Table scan of `salary_record`, filter `effective_date <= today` | 24,901 → 24,414 | 11 ms |
| Sort by `employee_id, effective_date DESC`; `ROW_NUMBER()` picks the current salary | 24,414 → 10,000 | 34 ms |
| Primary-key lookup of each employee; drop `TERMINATED` | 10,000 → 9,377 | 56 ms |
| Sort by `country, currency, amount`; `ROW_NUMBER()` / `COUNT(*) OVER` for median positions | 9,377 | 110 ms |
| Group into `(country, currency)` rows and sort | 15 | 133 ms |

These are `EXPLAIN ANALYZE` times, which include instrumentation overhead. Measured over
HTTP (warm, median of 15 calls, including JWT validation and JSON), each endpoint takes
about **150–190 ms** on a developer laptop.

**The results were checked independently.** An independent query found current salaries
with a `MAX(effective_date)` join instead of a window function, and computed medians with
`ORDER BY … LIMIT` per currency. It matched the overview exactly for all 7 currencies:
count, average, minimum, maximum and median.

**Index decision: no new index.** The existing unique `(employee_id, effective_date)` index
is not used by this query, because the query needs `amount` and `currency` for every
employee. Reading them through the secondary index would cost 24,901 extra lookups, so
MySQL scans the table instead.

- A covering index `(employee_id, effective_date, amount, currency)` was created on a
  throwaway database and measured. MySQL used it but still sorted, because it does not
  read the index backwards for the `DESC` window.
- Overview latency did not improve: a median of 164 ms with the index against 153 ms
  without, which is within noise.
- The index would add write cost and storage for no gain, so it was dropped and is not
  in any migration.

**Trade-offs and limits.**
- The query cost grows linearly with salary history: one scan plus two sorts. That is
  fine at 10,000 employees and about 25,000 records, but analytics is not a
  sub-10-millisecond endpoint.
- If it ever needs to be faster, the next steps, in order, would be:
  1. cache the result for a short time, since the figures change only when salaries are
     added or a date passes;
  2. materialize current salaries on write.

  Neither is needed or implemented now.

## Why this is appropriate for 10,000 employees

The worst case measured, a deep page with no usable ordering index, takes 39 ms. Every
common request finishes in single-digit milliseconds. The whole table is roughly
1–2 MB and stays in the InnoDB buffer pool. Adding Redis, Elasticsearch or a cache would
add moving parts and consistency problems to solve a problem this data size doesn't have.

The 10,000-row dev seed loads in about 3 seconds, as JDBC batches of 1,000 with
`rewriteBatchedStatements=true`, in one transaction.

## Known limitations

- **Deep pages use `OFFSET`.** MySQL still reads and discards every earlier row, so the
  last page costs about 39 ms versus 0.3 ms for the first. That is fine at 10,000 rows. At
  millions of rows, keyset ("seek") pagination on `(last_name, first_name, id)` would
  replace offsets, at the cost of jump-to-page.
- **Substring search cannot use an index.** `LIKE '%term%'` scans rows until a page is
  filled. When few rows match (for example a rare surname), the scan covers the whole
  table, about 10 ms today. That grows linearly with row count. MySQL `FULLTEXT` with the
  n-gram parser is the next step if needed.
- **Every page runs a `COUNT`.** Needed for `totalElements` and `totalPages`. It costs
  about 5 ms here, but at large scale an approximate or cached count would be cheaper.
- **Search is also accent-insensitive** (a side effect of the `_ai_ci` collation). That's
  helpful for names, but worth knowing.

## How to re-check

```sql
EXPLAIN ANALYZE
SELECT * FROM employee
WHERE country_code = 'US'
ORDER BY last_name, first_name, id
LIMIT 0, 20;
```

To see the application's SQL, run with `--logging.level.org.hibernate.SQL=debug`.
