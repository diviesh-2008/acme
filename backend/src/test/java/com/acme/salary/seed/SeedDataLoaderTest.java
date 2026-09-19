package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.PlatformTransactionManager;

class SeedDataLoaderTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final SeedDataLoader loader = new SeedDataLoader(jdbcTemplate, mock(PlatformTransactionManager.class));

	private final List<SeedEmployee> employees = new EmployeeSeedGenerator().generate();

	@Test
	void insertsTenThousandEmployeesInBatchesWhenTableIsEmpty() {
		givenRowCount("employee", 0);

		loader.seedEmployeesIfEmpty(employees);

		assertThat(batchInserted(SeedDataLoader.INSERT_EMPLOYEE_SQL)).hasSize(10_000);
	}

	@Test
	void doesNotSeedOrTopUpEmployeesWhenAnyExist() {
		givenRowCount("employee", 1);

		loader.seedEmployeesIfEmpty(employees);

		verify(jdbcTemplate, never()).batchUpdate(eq(SeedDataLoader.INSERT_EMPLOYEE_SQL), any(Collection.class),
				any(Integer.class), any(ParameterizedPreparedStatementSetter.class));
	}

	@Test
	void insertsSalaryHistoriesForSeededEmployeesWhenTableIsEmpty() {
		givenRowCount("salary_record", 0);
		givenEmployeeIdsFor(employees);

		loader.seedSalariesIfEmpty(employees);

		assertThat(batchInserted(SeedDataLoader.INSERT_SALARY_SQL))
			.hasSize(new SalarySeedGenerator().generate(employees).size());
	}

	@Test
	void doesNotSeedSalariesWhenAnyExist() {
		givenRowCount("salary_record", 5);

		loader.seedSalariesIfEmpty(employees);

		verify(jdbcTemplate, never()).batchUpdate(eq(SeedDataLoader.INSERT_SALARY_SQL), any(Collection.class),
				any(Integer.class), any(ParameterizedPreparedStatementSetter.class));
	}

	@Test
	void seedsSalariesOnlyForEmployeesThatCameFromTheSeed() {
		givenRowCount("salary_record", 0);
		givenEmployeeIdsFor(employees.subList(0, 2));

		loader.seedSalariesIfEmpty(employees);

		assertThat(batchInserted(SeedDataLoader.INSERT_SALARY_SQL)).extracting(row -> ((SeedSalary) row).employeeCode())
			.containsOnly("EMP-00001", "EMP-00002");
	}

	private void givenRowCount(String table, long rows) {
		when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).thenReturn(rows);
	}

	// Simulates "SELECT id, employee_code FROM employee" returning these employees.
	private void givenEmployeeIdsFor(List<SeedEmployee> existing) {
		org.mockito.Mockito.doAnswer(invocation -> {
			RowCallbackHandler handler = invocation.getArgument(1);
			long id = 1;
			for (SeedEmployee employee : existing) {
				java.sql.ResultSet row = mock(java.sql.ResultSet.class);
				when(row.getString("employee_code")).thenReturn(employee.employeeCode());
				when(row.getLong("id")).thenReturn(id++);
				handler.processRow(row);
			}
			return null;
		}).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));
	}

	@SuppressWarnings("unchecked")
	private Collection<Object> batchInserted(String sql) {
		ArgumentCaptor<Collection<Object>> rows = ArgumentCaptor.forClass(Collection.class);
		verify(jdbcTemplate).batchUpdate(eq(sql), rows.capture(), eq(SeedDataLoader.BATCH_SIZE),
				any(ParameterizedPreparedStatementSetter.class));
		return rows.getValue();
	}

}
