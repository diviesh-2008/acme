package com.acme.salary.employee;

import static com.acme.salary.IntegrationTest.HR_MANAGER_EMAIL;
import static com.acme.salary.IntegrationTest.HR_MANAGER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.acme.salary.IntegrationTest;
import com.acme.salary.common.PageResponse;
import com.acme.salary.common.error.NotFoundException;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import com.acme.salary.seed.SeedDataLoader;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee schema, seed, search, filters and paging against real MySQL.
 * <p>
 * The context has the 10,000 seeded employees. Tests that need exact matches add fixture
 * rows with values the seed never produces (surname "Quillfeather", country NZ, department
 * "Research Lab"); each test's changes are rolled back.
 */
@IntegrationTest
@Transactional
class EmployeeIT {

	private static final int SEEDED = 10_000;

	@Autowired
	private EmployeeService employeeService;

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private SeedDataLoader seedLoader;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	// --- schema -------------------------------------------------------------------------------

	@Test
	void migrationCreatesTheExpectedIndexes() {
		List<String> indexes = jdbcTemplate.queryForList("""
				SELECT DISTINCT index_name FROM information_schema.statistics
				WHERE table_schema = DATABASE() AND table_name = 'employee'""", String.class);

		assertThat(indexes).containsExactlyInAnyOrder("PRIMARY", "uk_employee_code", "uk_employee_email",
				"idx_employee_name", "idx_employee_country_name", "idx_employee_department_name");
	}

	@Test
	void emailMustBeUniqueRegardlessOfCase() {
		insert(fixture("FIX-001", "Ada", "Quillfeather", "NZ", "Research Lab", EmploymentStatus.ACTIVE));

		assertThatThrownBy(() -> insertSql("FIX-002", "ADA.QUILLFEATHER.FIX-001@FIXTURE.EXAMPLE", "NZ", "ACTIVE"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	// Spring reports MySQL CHECK violations (error 3819) as a generic DataAccessException,
	// so assert on the column/constraint named in the message.
	@Test
	void checkConstraintsRejectInvalidCountryAndStatus() {
		assertThatThrownBy(() -> insertSql("FIX-003", "a@fixture.example", "nz", "ACTIVE"))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_employee_country_code");
		assertThatThrownBy(() -> insertSql("FIX-004", "b@fixture.example", "NZL", "ACTIVE"))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("country_code");
		assertThatThrownBy(() -> insertSql("FIX-005", "c@fixture.example", "NZ", "RETIRED"))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_employee_status");
	}

	// --- seed ---------------------------------------------------------------------------------

	@Test
	void seedLoadsTenThousandUniqueEmployees() {
		assertThat(employeeRepository.count()).isEqualTo(SEEDED);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT employee_code) FROM employee", Long.class))
			.isEqualTo(SEEDED);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT email) FROM employee", Long.class))
			.isEqualTo(SEEDED);
	}

	@Test
	void seedDoesNotRunAgainWhenEmployeesExist() {
		// The same hook Spring calls on every startup.
		seedLoader.afterSingletonsInstantiated();

		assertThat(employeeRepository.count()).isEqualTo(SEEDED);
	}

	// --- search -------------------------------------------------------------------------------

	@Test
	void searchIsCaseInsensitiveAcrossNames() {
		insertQuillfeathers();

		assertThat(codes(search("QUILLFEATHER"))).containsExactly("FIX-A", "FIX-B", "FIX-Z");
		assertThat(codes(search("zorblax"))).containsExactly("FIX-P", "FIX-Z");
	}

	@Test
	void searchMatchesFullNameEmailAndEmployeeCode() {
		insertQuillfeathers();

		assertThat(codes(search("Zorblax Quillfeather"))).containsExactly("FIX-Z");
		assertThat(codes(search("bea.quillfeather.fix-b@"))).containsExactly("FIX-B");
		assertThat(codes(search("fix-p"))).containsExactly("FIX-P");
	}

	@Test
	void searchTreatsPercentAsALiteralCharacter() {
		assertThat(search("%").totalElements()).isZero();
	}

	// --- filters ------------------------------------------------------------------------------

	@Test
	void countryFilter() {
		insertQuillfeathers();

		// Ordered by last name, then first name: Pennington, Quillfeather Ada, Quillfeather Zorblax.
		assertThat(codes(page(criteria(null, "nz", null, null)))).containsExactly("FIX-P", "FIX-A", "FIX-Z");
	}

	@Test
	void departmentFilterIgnoresLetterCase() {
		insertQuillfeathers();

		assertThat(codes(page(criteria(null, null, "research lab", null)))).containsExactly("FIX-P", "FIX-B",
				"FIX-Z");
	}

	@Test
	void statusFilterMatchesTheDatabaseCount() {
		long onLeave = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM employee WHERE employment_status = 'ON_LEAVE'", Long.class);

		PageResponse<EmployeeResponse> result = page(criteria(null, null, null, EmploymentStatus.ON_LEAVE));

		assertThat(onLeave).isPositive();
		assertThat(result.totalElements()).isEqualTo(onLeave);
		assertThat(result.content()).allMatch(e -> e.employmentStatus() == EmploymentStatus.ON_LEAVE);
	}

	@Test
	void combinedFiltersMustAllMatch() {
		insertQuillfeathers();

		assertThat(codes(page(criteria("quillfeather", "NZ", "Research Lab", EmploymentStatus.ACTIVE))))
			.containsExactly("FIX-Z");
		assertThat(codes(page(criteria(null, null, "Research Lab", EmploymentStatus.TERMINATED))))
			.containsExactly("FIX-P");
	}

	// --- paging and ordering ------------------------------------------------------------------

	@Test
	void pagingReturnsRequestedSizeAndTotals() {
		PageResponse<EmployeeResponse> first = page(new EmployeeSearchCriteria(0, 20, null, null, null, null));
		PageResponse<EmployeeResponse> last = page(new EmployeeSearchCriteria(499, 20, null, null, null, null));
		PageResponse<EmployeeResponse> beyond = page(new EmployeeSearchCriteria(500, 20, null, null, null, null));

		assertThat(first.content()).hasSize(20);
		assertThat(first.totalElements()).isEqualTo(SEEDED);
		assertThat(first.totalPages()).isEqualTo(500);
		assertThat(first.hasNext()).isTrue();
		assertThat(first.hasPrevious()).isFalse();
		assertThat(last.content()).hasSize(20);
		assertThat(last.hasNext()).isFalse();
		assertThat(beyond.content()).isEmpty();
	}

	@Test
	void orderingIsDeterministicAcrossPages() {
		List<EmployeeResponse> twoPages = new ArrayList<>();
		twoPages.addAll(page(new EmployeeSearchCriteria(0, 50, null, null, null, null)).content());
		twoPages.addAll(page(new EmployeeSearchCriteria(1, 50, null, null, null, null)).content());
		List<EmployeeResponse> onePage = page(new EmployeeSearchCriteria(0, 100, null, null, null, null)).content();

		assertThat(twoPages).isEqualTo(onePage);
		assertThat(twoPages).extracting(EmployeeResponse::id).doesNotHaveDuplicates();
		assertThat(twoPages).isSortedAccordingTo(Comparator.comparing(EmployeeResponse::lastName)
			.thenComparing(EmployeeResponse::firstName)
			.thenComparing(EmployeeResponse::id));
	}

	@Test
	void employeesWithTheSameNameAreOrderedById() {
		insertQuillfeathers();
		Employee twin = insert(fixture("FIX-A2", "Ada", "Quillfeather", "NZ", "Studio", EmploymentStatus.ACTIVE));

		List<EmployeeResponse> adas = search("Ada Quillfeather").content();

		assertThat(adas).extracting(EmployeeResponse::employeeCode).containsExactly("FIX-A", "FIX-A2");
		assertThat(adas.getLast().id()).isEqualTo(twin.getId());
	}

	// --- lookup and HTTP ----------------------------------------------------------------------

	@Test
	void lookupByIdFindsSeededEmployee() {
		Long id = jdbcTemplate.queryForObject("SELECT id FROM employee WHERE employee_code = 'EMP-00001'", Long.class);

		assertThat(employeeService.getById(id).employeeCode()).isEqualTo("EMP-00001");
		assertThatThrownBy(() -> employeeService.getById(-1)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void httpApiRequiresTokenAndReturnsSeededData() throws Exception {
		mockMvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());

		String token = JsonPath.read(mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s"}""".formatted(HR_MANAGER_EMAIL, HR_MANAGER_PASSWORD)))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.accessToken");

		mockMvc.perform(get("/api/employees").param("size", "5").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(5))
			.andExpect(jsonPath("$.totalElements").value(SEEDED));
		mockMvc.perform(get("/api/employees/-1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNotFound());
	}

	// --- helpers ------------------------------------------------------------------------------

	/** FIX-A Ada, FIX-B Bea, FIX-Z Zorblax Quillfeather and FIX-P ZORBLAX Pennington. */
	private void insertQuillfeathers() {
		insert(fixture("FIX-Z", "Zorblax", "Quillfeather", "NZ", "Research Lab", EmploymentStatus.ACTIVE));
		insert(fixture("FIX-P", "ZORBLAX", "Pennington", "NZ", "Research Lab", EmploymentStatus.TERMINATED));
		insert(fixture("FIX-A", "Ada", "Quillfeather", "NZ", "Studio", EmploymentStatus.ACTIVE));
		insert(fixture("FIX-B", "Bea", "Quillfeather", "IE", "Research Lab", EmploymentStatus.ACTIVE));
	}

	private static Employee fixture(String code, String firstName, String lastName, String country,
			String department, EmploymentStatus status) {
		// e.g. ada.quillfeather.fix-a@fixture.example
		String email = (firstName + "." + lastName + "." + code).toLowerCase() + "@fixture.example";
		return new Employee(code, firstName, lastName, email, "Researcher", department, country, status,
				LocalDate.of(2024, 1, 15));
	}

	private Employee insert(Employee employee) {
		return employeeRepository.saveAndFlush(employee);
	}

	private void insertSql(String code, String email, String country, String status) {
		jdbcTemplate.update("""
				INSERT INTO employee (employee_code, first_name, last_name, email, job_title, department,
				                      country_code, employment_status, hire_date)
				VALUES (?, 'Test', 'Fixture', ?, 'Researcher', 'Research Lab', ?, ?, '2024-01-15')""", code, email,
				country, status);
	}

	private PageResponse<EmployeeResponse> search(String term) {
		return page(criteria(term, null, null, null));
	}

	private PageResponse<EmployeeResponse> page(EmployeeSearchCriteria criteria) {
		return employeeService.search(criteria);
	}

	private static EmployeeSearchCriteria criteria(String search, String country, String department,
			EmploymentStatus status) {
		return new EmployeeSearchCriteria(0, 100, search, country, department, status);
	}

	private static List<String> codes(PageResponse<EmployeeResponse> page) {
		return page.content().stream().map(EmployeeResponse::employeeCode).toList();
	}

}
