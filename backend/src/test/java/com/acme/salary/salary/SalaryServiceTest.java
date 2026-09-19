package com.acme.salary.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.acme.salary.common.error.ConflictException;
import com.acme.salary.common.error.FieldValidationException;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeNotFoundException;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmploymentStatus;
import com.acme.salary.salary.dto.CorrectSalaryRequest;
import com.acme.salary.salary.dto.CreateSalaryRequest;
import com.acme.salary.salary.dto.SalaryRecordResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Salary rules with the repositories mocked and "today" fixed at 2026-09-19. Which row
 * MySQL returns as "latest on or before a date" is checked against the database in SalaryIT.
 */
class SalaryServiceTest {

	private static final long EMPLOYEE_ID = 7;

	private static final Instant NOW = Instant.parse("2026-09-19T10:15:30Z");

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

	private static final LocalDate HIRE_DATE = LocalDate.of(2020, 3, 1);

	private final SalaryRecordRepository salaryRepository = mock(SalaryRecordRepository.class);

	private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);

	private final SalaryService service = serviceAt(NOW);

	// --- current salary -----------------------------------------------------------------------

	@Test
	void currentSalaryIsTheLatestRecordOnOrBeforeToday() {
		employeeExists();
		when(salaryRepository.findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(EMPLOYEE_ID,
				TODAY))
			.thenReturn(Optional.of(record(15, "900000.00", "INR", LocalDate.of(2026, 1, 1))));

		SalaryRecordResponse current = service.currentSalary(EMPLOYEE_ID);

		assertThat(current).isEqualTo(
				new SalaryRecordResponse(15L, new BigDecimal("900000.00"), "INR", LocalDate.of(2026, 1, 1)));
	}

	@Test
	void currentSalaryFollowsTheClockDate() {
		employeeExists();
		LocalDate newYear = LocalDate.of(2027, 1, 1);
		when(salaryRepository.findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(EMPLOYEE_ID,
				newYear))
			.thenReturn(Optional.of(record(16, "1000000.00", "INR", newYear)));

		SalaryRecordResponse current = serviceAt(Instant.parse("2027-01-01T00:00:00Z")).currentSalary(EMPLOYEE_ID);

		assertThat(current.amount()).isEqualByComparingTo("1000000");
	}

	@Test
	void currentSalaryReportsMissingEmployee() {
		when(employeeRepository.existsById(EMPLOYEE_ID)).thenReturn(false);

		assertThatThrownBy(() -> service.currentSalary(EMPLOYEE_ID)).isInstanceOf(EmployeeNotFoundException.class);
		verifyNoInteractions(salaryRepository);
	}

	@Test
	void currentSalaryReportsMissingSalarySeparatelyFromMissingEmployee() {
		employeeExists();
		when(salaryRepository.findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(EMPLOYEE_ID,
				TODAY))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.currentSalary(EMPLOYEE_ID))
			.isInstanceOf(SalaryRecordNotFoundException.class)
			.hasMessage("Employee 7 has no salary effective on or before 2026-09-19.");
	}

	// --- history ------------------------------------------------------------------------------

	@Test
	void historyKeepsTheRepositoryOrderNewestFirst() {
		employeeExists();
		when(salaryRepository.findByEmployeeIdOrderByEffectiveDateDesc(EMPLOYEE_ID))
			.thenReturn(List.of(record(3, "1000000.00", "INR", LocalDate.of(2027, 1, 1)),
					record(2, "900000.00", "INR", LocalDate.of(2026, 1, 1)),
					record(1, "800000.00", "INR", LocalDate.of(2025, 1, 1))));

		assertThat(service.history(EMPLOYEE_ID)).extracting(SalaryRecordResponse::id).containsExactly(3L, 2L, 1L);
	}

	@Test
	void historyIsEmptyForAnEmployeeWithoutSalary() {
		employeeExists();
		when(salaryRepository.findByEmployeeIdOrderByEffectiveDateDesc(EMPLOYEE_ID)).thenReturn(List.of());

		assertThat(service.history(EMPLOYEE_ID)).isEmpty();
	}

	@Test
	void historyReportsMissingEmployee() {
		assertThatThrownBy(() -> service.history(EMPLOYEE_ID)).isInstanceOf(EmployeeNotFoundException.class);
		verifyNoInteractions(salaryRepository);
	}

	// --- create -------------------------------------------------------------------------------

	@Test
	void createStoresANewRecordWithServerSetTimestamp() {
		employeeWithStatus(EmploymentStatus.ACTIVE);
		when(salaryRepository.saveAndFlush(any())).then(returnsFirstArg());

		service.create(EMPLOYEE_ID, new CreateSalaryRequest(new BigDecimal("950000"), "INR", LocalDate.of(2026, 10, 1)));

		SalaryRecord saved = savedRecord();
		assertThat(saved.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
		assertThat(saved.getAmount()).isEqualTo(new BigDecimal("950000.00"));
		assertThat(saved.getCurrency()).isEqualTo("INR");
		assertThat(saved.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
	}

	@ParameterizedTest(name = "effective {0}")
	@ValueSource(strings = { "2020-03-01", "2023-06-15", "2026-09-19", "2026-12-01", "2027-09-19" })
	void createAllowsPastCurrentAndFutureDatesFromHireDateToOneYearAhead(String date) {
		employeeWithStatus(EmploymentStatus.ACTIVE);
		when(salaryRepository.saveAndFlush(any())).then(returnsFirstArg());

		service.create(EMPLOYEE_ID, request(LocalDate.parse(date)));

		assertThat(savedRecord().getEffectiveDate()).isEqualTo(LocalDate.parse(date));
	}

	@Test
	void createRejectsADateBeforeTheHireDate() {
		employeeWithStatus(EmploymentStatus.ACTIVE);

		assertThatThrownBy(() -> service.create(EMPLOYEE_ID, request(HIRE_DATE.minusDays(1))))
			.isInstanceOf(FieldValidationException.class)
			.hasMessageContaining("hire date (2020-03-01)")
			.extracting("field")
			.isEqualTo("effectiveDate");
		verify(salaryRepository, never()).saveAndFlush(any());
	}

	@Test
	void createRejectsADateMoreThanOneYearAhead() {
		employeeWithStatus(EmploymentStatus.ACTIVE);

		assertThatThrownBy(() -> service.create(EMPLOYEE_ID, request(LocalDate.of(2027, 9, 20))))
			.isInstanceOf(FieldValidationException.class)
			.hasMessageContaining("2027-09-19");
		verify(salaryRepository, never()).saveAndFlush(any());
	}

	@Test
	void createRejectsADuplicateEffectiveDate() {
		employeeWithStatus(EmploymentStatus.ACTIVE);
		when(salaryRepository.existsByEmployeeIdAndEffectiveDate(EMPLOYEE_ID, LocalDate.of(2026, 1, 1)))
			.thenReturn(true);

		assertThatThrownBy(() -> service.create(EMPLOYEE_ID, request(LocalDate.of(2026, 1, 1))))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("already has a salary record effective on 2026-01-01");
		verify(salaryRepository, never()).saveAndFlush(any());
	}

	@Test
	void databaseConstraintStaysTheFinalGuardAgainstRacingDuplicates() {
		employeeWithStatus(EmploymentStatus.ACTIVE);
		when(salaryRepository.existsByEmployeeIdAndEffectiveDate(any(), any())).thenReturn(false);
		when(salaryRepository.saveAndFlush(any()))
			.thenThrow(new DataIntegrityViolationException("Duplicate entry for uk_salary_record_employee_date"));

		// Not swallowed: it rolls back the transaction and becomes 409 in GlobalExceptionHandler.
		assertThatThrownBy(() -> service.create(EMPLOYEE_ID, request(LocalDate.of(2026, 1, 1))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void createRejectsTerminatedEmployees() {
		employeeWithStatus(EmploymentStatus.TERMINATED);

		assertThatThrownBy(() -> service.create(EMPLOYEE_ID, request(LocalDate.of(2026, 1, 1))))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("terminated");
		verify(salaryRepository, never()).saveAndFlush(any());
	}

	@Test
	void createAllowsEmployeesOnLeave() {
		employeeWithStatus(EmploymentStatus.ON_LEAVE);
		when(salaryRepository.saveAndFlush(any())).then(returnsFirstArg());

		service.create(EMPLOYEE_ID, request(LocalDate.of(2026, 1, 1)));

		verify(salaryRepository).saveAndFlush(any());
	}

	@Test
	void createReportsMissingEmployee() {
		when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(EMPLOYEE_ID, request(LocalDate.of(2026, 1, 1))))
			.isInstanceOf(EmployeeNotFoundException.class);
		verifyNoInteractions(salaryRepository);
	}

	// --- correct ------------------------------------------------------------------------------

	@Test
	void correctionChangesOnlyAmountAndCurrency() {
		employeeExists();
		Instant created = Instant.parse("2026-01-02T09:00:00Z");
		SalaryRecord existing = record(15, "900000.00", "INR", LocalDate.of(2026, 1, 1), created);
		when(salaryRepository.findByIdAndEmployeeId(15L, EMPLOYEE_ID)).thenReturn(Optional.of(existing));
		when(salaryRepository.saveAndFlush(any())).then(returnsFirstArg());

		SalaryRecordResponse corrected = service.correct(EMPLOYEE_ID, 15,
				new CorrectSalaryRequest(new BigDecimal("960000.5"), "USD"));

		assertThat(corrected).isEqualTo(
				new SalaryRecordResponse(15L, new BigDecimal("960000.50"), "USD", LocalDate.of(2026, 1, 1)));
		assertThat(existing.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(existing.getCreatedAt()).isEqualTo(created);
		assertThat(existing.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
	}

	@Test
	void correctionCannotReachAnotherEmployeesRecord() {
		employeeExists();
		when(salaryRepository.findByIdAndEmployeeId(15L, EMPLOYEE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.correct(EMPLOYEE_ID, 15, new CorrectSalaryRequest(BigDecimal.TEN, "INR")))
			.isInstanceOf(SalaryRecordNotFoundException.class)
			.hasMessage("Salary record 15 was not found for employee 7.");
		verify(salaryRepository, never()).saveAndFlush(any());
	}

	@Test
	void correctionReportsMissingEmployee() {
		assertThatThrownBy(() -> service.correct(EMPLOYEE_ID, 15, new CorrectSalaryRequest(BigDecimal.TEN, "INR")))
			.isInstanceOf(EmployeeNotFoundException.class);
		verifyNoInteractions(salaryRepository);
	}

	// --- helpers ------------------------------------------------------------------------------

	private SalaryService serviceAt(Instant now) {
		return new SalaryService(salaryRepository, employeeRepository, Clock.fixed(now, ZoneOffset.UTC));
	}

	private void employeeExists() {
		when(employeeRepository.existsById(EMPLOYEE_ID)).thenReturn(true);
	}

	private void employeeWithStatus(EmploymentStatus status) {
		Employee employee = new Employee("EMP-00007", "Ada", "Lovelace", "ada@acme.example", "Engineer",
				"Engineering", "IN", status, HIRE_DATE);
		when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
	}

	private static CreateSalaryRequest request(LocalDate effectiveDate) {
		return new CreateSalaryRequest(new BigDecimal("950000.00"), "INR", effectiveDate);
	}

	private static SalaryRecord record(long id, String amount, String currency, LocalDate effectiveDate) {
		return record(id, amount, currency, effectiveDate, NOW);
	}

	private static SalaryRecord record(long id, String amount, String currency, LocalDate effectiveDate,
			Instant createdAt) {
		SalaryRecord record = new SalaryRecord(EMPLOYEE_ID, new BigDecimal(amount), currency, effectiveDate, createdAt);
		ReflectionTestUtils.setField(record, "id", id);
		return record;
	}

	private SalaryRecord savedRecord() {
		ArgumentCaptor<SalaryRecord> captor = ArgumentCaptor.forClass(SalaryRecord.class);
		verify(salaryRepository).saveAndFlush(captor.capture());
		return captor.getValue();
	}

}
