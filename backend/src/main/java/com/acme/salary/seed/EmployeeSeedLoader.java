package com.acme.salary.seed;

import java.sql.Date;
import java.util.List;

import com.acme.salary.employee.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the 10,000 demo employees when {@code acme.seed.enabled=true} (set by the {@code dev}
 * profile). Off by default, so it never runs in production.
 * <p>
 * Runs once: if any employee exists, nothing is inserted. Like the initial HR account, it
 * runs synchronously before the web server accepts requests. The rows go in as JDBC
 * batches in a single transaction, so a failure leaves the table empty and the next
 * startup retries.
 */
@Component
@ConditionalOnBooleanProperty("acme.seed.enabled")
public class EmployeeSeedLoader implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(EmployeeSeedLoader.class);

	static final int BATCH_SIZE = 1_000;

	static final String INSERT_SQL = """
			INSERT INTO employee (employee_code, first_name, last_name, email, job_title, department,
			                      country_code, employment_status, hire_date)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

	private final EmployeeRepository employeeRepository;

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	public EmployeeSeedLoader(EmployeeRepository employeeRepository, JdbcTemplate jdbcTemplate,
			PlatformTransactionManager transactionManager) {
		this.employeeRepository = employeeRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public void afterSingletonsInstantiated() {
		seedIfEmpty();
	}

	void seedIfEmpty() {
		long existing = employeeRepository.count();
		if (existing > 0) {
			log.info("Employee seed skipped: {} employees already exist", existing);
			return;
		}

		long start = System.nanoTime();
		List<SeedEmployee> employees = new EmployeeSeedGenerator().generate();
		transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(INSERT_SQL, employees,
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
		log.info("Seeded {} employees in {} ms", employees.size(), (System.nanoTime() - start) / 1_000_000);
	}

}
