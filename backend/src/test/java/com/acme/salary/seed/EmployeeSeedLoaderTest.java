package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;

import com.acme.salary.employee.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.transaction.PlatformTransactionManager;

class EmployeeSeedLoaderTest {

	private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final EmployeeSeedLoader loader = new EmployeeSeedLoader(employeeRepository, jdbcTemplate,
			mock(PlatformTransactionManager.class));

	@Test
	void insertsTenThousandEmployeesInBatchesWhenTableIsEmpty() {
		when(employeeRepository.count()).thenReturn(0L);

		loader.seedIfEmpty();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<SeedEmployee>> rows = ArgumentCaptor.forClass(Collection.class);
		verify(jdbcTemplate).batchUpdate(eq(EmployeeSeedLoader.INSERT_SQL), rows.capture(),
				eq(EmployeeSeedLoader.BATCH_SIZE), any(ParameterizedPreparedStatementSetter.class));
		assertThat(rows.getValue()).hasSize(10_000);
		assertThat(EmployeeSeedLoader.BATCH_SIZE).isEqualTo(1_000);
	}

	@Test
	void doesNothingWhenEmployeesAlreadyExist() {
		when(employeeRepository.count()).thenReturn(10_000L);

		loader.seedIfEmpty();

		verifyNoInteractions(jdbcTemplate);
	}

	@Test
	void doesNotTopUpAPartiallyFilledTable() {
		when(employeeRepository.count()).thenReturn(1L);

		loader.seedIfEmpty();

		verifyNoInteractions(jdbcTemplate);
	}

}
