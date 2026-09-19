package com.acme.salary.analytics;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Current-salary statistics computed entirely in MySQL: one query per request, whatever
 * the number of employees.
 * <p>
 * Native SQL because the query needs window functions (to pick each employee's current
 * salary and to find each group's median), which JPQL does not support. All three
 * groupings share one query; only the grouping column differs, and it comes from
 * {@link Grouping}, never from request input.
 */
@Repository
class SalaryStatisticsRepository {

	/*
	 * current_salary: the salary in force on the given date for each employee, i.e. the
	 * record with the latest effective_date on or before it. (employee_id, effective_date)
	 * is unique, so exactly one row per employee has recency = 1. Future-dated records are
	 * excluded by the WHERE clause; older records rank below the newer one.
	 *
	 * ranked: numbers each group's salaries by amount so the median can be picked.
	 * TERMINATED employees are left out, as the requirements specify.
	 *
	 * Final SELECT: the median is the mean of positions (n + 1) DIV 2 and (n + 2) DIV 2,
	 * which is the middle value for odd n and the two middle values for even n.
	 * Every group is one currency; amounts in different currencies are never combined.
	 */
	private static final String QUERY_TEMPLATE = """
			WITH current_salary AS (
			    SELECT s.employee_id, s.amount, s.currency,
			           ROW_NUMBER() OVER (PARTITION BY s.employee_id ORDER BY s.effective_date DESC) AS recency
			    FROM salary_record s
			    WHERE s.effective_date <= ?
			),
			ranked AS (
			    SELECT %1$s AS dimension, cs.currency, cs.amount,
			           ROW_NUMBER() OVER (PARTITION BY %2$s ORDER BY cs.amount) AS position,
			           COUNT(*) OVER (PARTITION BY %2$s) AS group_size
			    FROM current_salary cs
			    JOIN employee e ON e.id = cs.employee_id
			    WHERE cs.recency = 1
			      AND e.employment_status <> 'TERMINATED'
			)
			SELECT dimension,
			       currency,
			       COUNT(*)    AS employee_count,
			       SUM(amount) AS total_amount,
			       MIN(amount) AS minimum_amount,
			       MAX(amount) AS maximum_amount,
			       AVG(CASE WHEN position IN ((group_size + 1) DIV 2, (group_size + 2) DIV 2)
			                THEN amount END) AS median_amount
			FROM ranked
			GROUP BY dimension, currency
			ORDER BY dimension, currency""";

	enum Grouping {

		/** One row per currency. */
		CURRENCY(null),
		/** One row per country and currency. */
		COUNTRY("e.country_code"),
		/** One row per department and currency. */
		DEPARTMENT("e.department");

		private final String sql;

		Grouping(String column) {
			String dimension = (column == null) ? "NULL" : column;
			String partition = (column == null) ? "cs.currency" : column + ", cs.currency";
			this.sql = QUERY_TEMPLATE.formatted(dimension, partition);
		}

	}

	private final JdbcTemplate jdbcTemplate;

	SalaryStatisticsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Statistics over current salaries on {@code asOf}, ordered by dimension then currency. */
	List<SalaryStatisticsRow> currentSalaryStatistics(Grouping grouping, LocalDate asOf) {
		return jdbcTemplate.query(grouping.sql,
				(row, rowNumber) -> new SalaryStatisticsRow(row.getString("dimension"), row.getString("currency"),
						row.getLong("employee_count"), row.getBigDecimal("total_amount"),
						row.getBigDecimal("minimum_amount"), row.getBigDecimal("maximum_amount"),
						row.getBigDecimal("median_amount")),
				Date.valueOf(asOf));
	}

}
