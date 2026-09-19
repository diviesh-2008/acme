# Performance

How the employee API stays fast at ACME's size (~10,000 employees), what was measured,
and where the limits are. There is deliberately no caching, search engine or read replica.
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
