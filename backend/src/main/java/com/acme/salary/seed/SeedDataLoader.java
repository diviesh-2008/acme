package com.acme.salary.seed;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the demo data when {@code acme.seed.enabled=true} (set by the {@code dev} profile):
 * 10,000 employees, then their salary histories. Off by default, so it never runs in
 * production.
 * <p>
 * Each table is seeded only while it is empty, so restarts never duplicate data, and an
 * existing employee table from an earlier version still gets salary histories. Like the
 * initial HR account, this runs synchronously before the web server accepts requests. Rows
 * go in as JDBC batches, one transaction per table, so a failure leaves that table empty
 * and the next startup retries.
 */
@Component
@ConditionalOnBooleanProperty("acme.seed.enabled")
public class SeedDataLoader implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

	static final int BATCH_SIZE = 1_000;

	/** Seeded salary rows count as imported from the spreadsheets on this date (UTC). */
	static final LocalDateTime SEEDED_AT = EmployeeSeedGenerator.REFERENCE_DATE.atStartOfDay();

	static final String INSERT_EMPLOYEE_SQL = """
			INSERT INTO employee (employee_code, first_name, last_name, email, job_title, department,
			                      country_code, employment_status, hire_date)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

	static final String INSERT_SALARY_SQL = """
			INSERT INTO salary_record (employee_id, amount, currency, effective_date, created_at)
			VALUES (?, ?, ?, ?, ?)""";

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	public SeedDataLoader(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public void afterSingletonsInstantiated() {
		List<SeedEmployee> employees = new EmployeeSeedGenerator().generate();
		seedEmployeesIfEmpty(employees);
		seedSalariesIfEmpty(employees);
	}

	void seedEmployeesIfEmpty(List<SeedEmployee> employees) {
		long existing = count("employee");
		if (existing > 0) {
			log.info("Employee seed skipped: {} employees already exist", existing);
			return;
		}

		long start = System.nanoTime();
		transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(INSERT_EMPLOYEE_SQL, employees,
				BATCH_SIZE, (statement, employee) -> {
					statement.setString(1, employee.employeeCode());
					statement.setString(2, employee.firstName());
					statement.setString(3, employee.lastName());
					statement.setString(4, employee.email());
					statement.setString(5, employee.jobTitle());
					statement.setString(6, employee.department());
					statement.setString(7, employee.countryCode());
					statement.setString(8, employee.employmentStatus().name());
					statement.setDate(9, Date.valueOf(employee.hireDate()));
				}));
		log.info("Seeded {} employees in {} ms", employees.size(), elapsedMillis(start));
	}

	void seedSalariesIfEmpty(List<SeedEmployee> employees) {
		long existing = count("salary_record");
		if (existing > 0) {
			log.info("Salary seed skipped: {} salary records already exist", existing);
			return;
		}

		long start = System.nanoTime();
		Map<String, Long> employeeIds = employeeIdsByCode();
		// Only employees that came from the seed get a generated history.
		List<SeedSalary> salaries = new SalarySeedGenerator().generate(employees)
			.stream()
			.filter(salary -> employeeIds.containsKey(salary.employeeCode()))
			.toList();
		if (salaries.isEmpty()) {
			log.info("Salary seed skipped: no seeded employees found");
			return;
		}

		transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(INSERT_SALARY_SQL, salaries,
				BATCH_SIZE, (statement, salary) -> {
					statement.setLong(1, employeeIds.get(salary.employeeCode()));
					statement.setBigDecimal(2, salary.amount());
					statement.setString(3, salary.currency());
					statement.setDate(4, Date.valueOf(salary.effectiveDate()));
					// DATETIME holds UTC wall-clock time; LocalDateTime avoids any driver time-zone shift.
					statement.setObject(5, SEEDED_AT);
				}));
		log.info("Seeded {} salary records in {} ms", salaries.size(), elapsedMillis(start));
	}

	private Map<String, Long> employeeIdsByCode() {
		Map<String, Long> ids = new HashMap<>();
		jdbcTemplate.query("SELECT id, employee_code FROM employee",
				row -> {
					ids.put(row.getString("employee_code"), row.getLong("id"));
				});
		return ids;
	}

	private long count(String table) {
		Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
		return (rows == null) ? 0 : rows;
	}

	private static long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

}
