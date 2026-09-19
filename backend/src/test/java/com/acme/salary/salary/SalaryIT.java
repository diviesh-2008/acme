package com.acme.salary.salary;

import static com.acme.salary.IntegrationTest.HR_MANAGER_EMAIL;
import static com.acme.salary.IntegrationTest.HR_MANAGER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.acme.salary.IntegrationTest;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeNotFoundException;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmploymentStatus;
import com.acme.salary.salary.dto.SalaryRecordResponse;
import com.acme.salary.seed.SeedDataLoader;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Salary schema, seed, current-salary rule and API against real MySQL.
 * <p>
 * Tests use their own fixture employees (codes {@code SAL-*}) and fixed dates far from
 * today, so results do not depend on when they run. Each test is rolled back.
 */
@IntegrationTest
@Transactional
class SalaryIT {

	@Autowired
	private SalaryService salaryService;

	@Autowired
	private SalaryRecordRepository salaryRepository;

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private SeedDataLoader seedLoader;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	private Employee employee;

	private Employee otherEmployee;

	@BeforeEach
	void fixtureEmployees() {
		employee = employeeRepository.saveAndFlush(fixtureEmployee("SAL-1", EmploymentStatus.ACTIVE));
		otherEmployee = employeeRepository.saveAndFlush(fixtureEmployee("SAL-2", EmploymentStatus.ACTIVE));
	}

	// --- schema -------------------------------------------------------------------------------

	@Test
	void uniqueEmployeeDateIndexAlsoServesTheForeignKey() {
		List<String> indexes = jdbcTemplate.queryForList("""
				SELECT DISTINCT index_name FROM information_schema.statistics
				WHERE table_schema = DATABASE() AND table_name = 'salary_record'""", String.class);

		// No separate foreign-key index: the unique index leads with employee_id.
		assertThat(indexes).containsExactlyInAnyOrder("PRIMARY", "uk_salary_record_employee_date");
	}

	@Test
	void databaseRejectsADuplicateEmployeeAndDate() {
		save(employee, "800000.00", "2025-01-01");

		assertThatThrownBy(() -> save(employee, "999.00", "2025-01-01"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsOrphansAndProtectsHistoryFromEmployeeDeletion() {
		assertThatThrownBy(() -> insertSql(Long.MAX_VALUE, "1.00", "USD"))
			.isInstanceOf(DataIntegrityViolationException.class);

		save(employee, "800000.00", "2025-01-01");
		assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM employee WHERE id = ?", employee.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	// Spring reports MySQL CHECK violations (error 3819) as a generic DataAccessException.
	@Test
	void checkConstraintsRejectNonPositiveAmountsAndMalformedCurrencies() {
		assertThatThrownBy(() -> insertSql(employee.getId(), "0.00", "USD")).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_salary_record_amount");
		assertThatThrownBy(() -> insertSql(employee.getId(), "1.00", "usd")).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_salary_record_currency");
	}

	// --- seed ---------------------------------------------------------------------------------

	@Test
	void seedGivesEverySeededEmployeeASalaryInForceOnTheReferenceDate() {
		assertThat(count("SELECT COUNT(*) FROM salary_record WHERE created_at = '2026-01-01 00:00:00'"))
			.isEqualTo(24_901);
		assertThat(count("""
				SELECT COUNT(DISTINCT employee_id) FROM salary_record
				WHERE effective_date <= '2026-01-01' AND created_at = '2026-01-01 00:00:00'""")).isEqualTo(10_000);
		assertThat(count("SELECT COUNT(*) FROM salary_record WHERE effective_date > '2026-01-01'")).isPositive();
	}

	@Test
	void seedDoesNotRunAgainWhenSalariesExist() {
		long before = salaryRepository.count();

		seedLoader.afterSingletonsInstantiated();

		assertThat(salaryRepository.count()).isEqualTo(before);
	}

	// --- current salary rule (the example from the requirements) -------------------------------

	@Test
	void currentSalaryIsTheLatestRecordOnOrBeforeTheDate() {
		save(employee, "800000.00", "2025-01-01");
		save(employee, "900000.00", "2026-01-01");
		save(employee, "1000000.00", "2027-01-01");

		assertThat(amountOn("2024-12-31")).isNull();
		assertThat(amountOn("2025-06-30")).isEqualByComparingTo("800000");
		assertThat(amountOn("2026-01-01")).isEqualByComparingTo("900000");
		assertThat(amountOn("2026-09-19")).isEqualByComparingTo("900000");
		assertThat(amountOn("2026-12-31")).isEqualByComparingTo("900000");
		assertThat(amountOn("2027-01-01")).isEqualByComparingTo("1000000");
	}

	@Test
	void serviceIgnoresFutureRecordsAndOtherEmployees() {
		save(employee, "100.00", "2001-01-01");
		save(employee, "200.00", "2002-01-01");
		save(employee, "900.00", "2999-01-01");
		save(otherEmployee, "555.00", "2003-01-01");

		assertThat(salaryService.currentSalary(employee.getId()).amount()).isEqualByComparingTo("200");
		assertThat(salaryService.history(employee.getId())).extracting(SalaryRecordResponse::effectiveDate)
			.containsExactly(LocalDate.of(2999, 1, 1), LocalDate.of(2002, 1, 1), LocalDate.of(2001, 1, 1));
	}

	@Test
	void employeeWithoutSalaryHasNoCurrentSalaryAndAnEmptyHistory() {
		assertThatThrownBy(() -> salaryService.currentSalary(employee.getId()))
			.isInstanceOf(SalaryRecordNotFoundException.class);
		assertThat(salaryService.history(employee.getId())).isEmpty();
		assertThatThrownBy(() -> salaryService.history(-1)).isInstanceOf(EmployeeNotFoundException.class);
	}

	// --- API ----------------------------------------------------------------------------------

	@Test
	void createStoresTheRecordWithAServerTimestamp() throws Exception {
		Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		String body = create(employee, """
				{"amount": 950000, "currency": "inr", "effectiveDate": "2020-06-01"}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.currency").value("INR"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		SalaryRecord stored = salaryRepository.findById(((Number) JsonPath.read(body, "$.id")).longValue()).orElseThrow();
		assertThat(stored.getAmount()).isEqualTo(new BigDecimal("950000.00"));
		assertThat(stored.getEffectiveDate()).isEqualTo(LocalDate.of(2020, 6, 1));
		assertThat(stored.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
	}

	@Test
	void duplicateEffectiveDateReturns409AndKeepsTheOriginal() throws Exception {
		save(employee, "800000.00", "2020-06-01");

		create(employee, """
				{"amount": 1, "currency": "INR", "effectiveDate": "2020-06-01"}""").andExpect(status().isConflict());

		assertThat(salaryService.history(employee.getId())).singleElement()
			.extracting(SalaryRecordResponse::amount)
			.isEqualTo(new BigDecimal("800000.00"));
	}

	@Test
	void correctionUpdatesAmountAndCurrencyButKeepsDateAndCreationTime() throws Exception {
		SalaryRecord record = save(employee, "900000.00", "2020-06-01");
		Instant createdAt = record.getCreatedAt();

		correct(employee, record.getId(), """
				{"amount": 910000.50, "currency": "usd"}""")
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"amount": 910000.50, "currency": "USD", "effectiveDate": "2020-06-01"}"""));

		var row = jdbcTemplate.queryForMap("SELECT amount, currency, effective_date FROM salary_record WHERE id = ?",
				record.getId());
		assertThat((BigDecimal) row.get("amount")).isEqualTo(new BigDecimal("910000.50"));
		assertThat(row.get("currency")).isEqualTo("USD");
		assertThat(row.get("effective_date").toString()).isEqualTo("2020-06-01");
		assertThat(salaryRepository.findById(record.getId()).orElseThrow().getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void correctionThroughAnotherEmployeesUrlReturns404AndChangesNothing() throws Exception {
		SalaryRecord othersRecord = save(otherEmployee, "555.00", "2020-06-01");

		correct(employee, othersRecord.getId(), """
				{"amount": 1.00, "currency": "INR"}""").andExpect(status().isNotFound());

		assertThat(jdbcTemplate.queryForObject("SELECT amount FROM salary_record WHERE id = ?", BigDecimal.class,
				othersRecord.getId())).isEqualTo(new BigDecimal("555.00"));
	}

	@Test
	void httpEndpointsRequireAToken() throws Exception {
		mockMvc.perform(get("/api/employees/{id}/salary", employee.getId())).andExpect(status().isUnauthorized());
	}

	// --- helpers ------------------------------------------------------------------------------

	private static Employee fixtureEmployee(String code, EmploymentStatus status) {
		return new Employee(code, "Salary", "Fixture", code.toLowerCase() + "@fixture.example", "Analyst", "Finance",
				"IN", status, LocalDate.of(2000, 1, 1));
	}

	private SalaryRecord save(Employee owner, String amount, String effectiveDate) {
		return salaryRepository.saveAndFlush(new SalaryRecord(owner.getId(), new BigDecimal(amount), "INR",
				LocalDate.parse(effectiveDate), Instant.parse("2026-01-01T00:00:00Z")));
	}

	private BigDecimal amountOn(String date) {
		return salaryRepository
			.findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(employee.getId(),
					LocalDate.parse(date))
			.map(SalaryRecord::getAmount)
			.orElse(null);
	}

	private void insertSql(long employeeId, String amount, String currency) {
		jdbcTemplate.update("""
				INSERT INTO salary_record (employee_id, amount, currency, effective_date, created_at)
				VALUES (?, ?, ?, '2020-01-01', '2026-01-01 00:00:00')""", employeeId, new BigDecimal(amount), currency);
	}

	private long count(String sql) {
		return jdbcTemplate.queryForObject(sql, Long.class);
	}

	private ResultActions create(Employee owner, String body) throws Exception {
		return mockMvc.perform(post("/api/employees/{id}/salary", owner.getId()).header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions correct(Employee owner, long salaryId, String body) throws Exception {
		return mockMvc.perform(put("/api/employees/{id}/salary/{salaryId}", owner.getId(), salaryId)
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private String bearer() throws Exception {
		String response = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s"}""".formatted(HR_MANAGER_EMAIL, HR_MANAGER_PASSWORD)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return "Bearer " + JsonPath.read(response, "$.accessToken");
	}

}
