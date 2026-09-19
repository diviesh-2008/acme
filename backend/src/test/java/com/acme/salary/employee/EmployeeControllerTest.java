package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.acme.salary.auth.JwtTokenService;
import com.acme.salary.common.ClockConfig;
import com.acme.salary.common.PageResponse;
import com.acme.salary.common.error.NotFoundException;
import com.acme.salary.common.security.SecurityConfig;
import com.acme.salary.common.security.SecurityProblemHandler;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The employee endpoints through the real security filter chain, parameter validation and
 * error handling. The service is mocked; query behaviour is covered by EmployeeIT.
 */
@WebMvcTest(controllers = EmployeeController.class,
		properties = "acme.security.jwt.secret=web-test-signing-secret-of-at-least-32-bytes")
@Import({ SecurityConfig.class, SecurityProblemHandler.class, JwtTokenService.class, ClockConfig.class })
class EmployeeControllerTest {

	private static final EmployeeResponse ADA = new EmployeeResponse(1L, "EMP-00001", "Ada", "Lovelace",
			"ada.lovelace1@acme.example", "Software Engineer", "Engineering", "GB", EmploymentStatus.ACTIVE,
			LocalDate.of(2022, 4, 15));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private EmployeeService employeeService;

	// Needed by SecurityConfig's login AuthenticationManager; unused here.
	@MockitoBean
	private UserDetailsService userDetailsService;

	private String hrManagerToken;

	@BeforeEach
	void issueToken() {
		hrManagerToken = token("ROLE_HR_MANAGER");
	}

	@Test
	void authenticatedRequestReturnsEmployeePage() throws Exception {
		when(employeeService.search(any())).thenReturn(new PageResponse<>(List.of(ADA), 0, 20, 10_000, 500, true,
				false));

		mockMvc.perform(authenticated(get("/api/employees")))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.content[0].id").value(1))
			.andExpect(jsonPath("$.content[0].employeeCode").value("EMP-00001"))
			.andExpect(jsonPath("$.content[0].firstName").value("Ada"))
			.andExpect(jsonPath("$.content[0].countryCode").value("GB"))
			.andExpect(jsonPath("$.content[0].employmentStatus").value("ACTIVE"))
			.andExpect(jsonPath("$.content[0].hireDate").value("2022-04-15"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(10_000))
			.andExpect(jsonPath("$.totalPages").value(500))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.hasPrevious").value(false));
	}

	@Test
	void unauthenticatedRequestReturns401() throws Exception {
		mockMvc.perform(get("/api/employees"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(employeeService);
	}

	@Test
	void tokenWithoutHrManagerRoleReturns403() throws Exception {
		mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ROLE_EMPLOYEE")))
			.andExpect(status().isForbidden());
		verifyNoInteractions(employeeService);
	}

	@Test
	void pageAndSizeDefaultTo0And20() throws Exception {
		stubEmptyPage();

		mockMvc.perform(authenticated(get("/api/employees"))).andExpect(status().isOk());

		EmployeeSearchCriteria criteria = capturedCriteria();
		assertThat(criteria.page()).isZero();
		assertThat(criteria.size()).isEqualTo(20);
	}

	@Test
	void searchAndFilterParametersAreAcceptedAndNormalized() throws Exception {
		stubEmptyPage();

		mockMvc
			.perform(authenticated(get("/api/employees").param("page", "2")
				.param("size", "50")
				.param("search", "  john ")
				.param("country", "us")
				.param("department", "Engineering")
				.param("status", "ACTIVE")))
			.andExpect(status().isOk());

		assertThat(capturedCriteria()).isEqualTo(
				new EmployeeSearchCriteria(2, 50, "john", "US", "Engineering", EmploymentStatus.ACTIVE));
	}

	@ParameterizedTest(name = "{0}={1} -> 400")
	@CsvSource({ "page, -1", "page, abc", "size, 0", "size, -1", "size, 101", "size, 1000", "status, FIRED",
			"country, USA", "country, 1A" })
	void invalidParametersReturn400WithFieldError(String parameter, String value) throws Exception {
		mockMvc.perform(authenticated(get("/api/employees").param(parameter, value)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors." + parameter).exists());
		verifyNoInteractions(employeeService);
	}

	@Test
	void maximumPageSizeIsAccepted() throws Exception {
		stubEmptyPage();

		mockMvc.perform(authenticated(get("/api/employees").param("size", "100"))).andExpect(status().isOk());
	}

	@Test
	void employeeDetailReturns200() throws Exception {
		when(employeeService.getById(1)).thenReturn(ADA);

		mockMvc.perform(authenticated(get("/api/employees/1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.email").value("ada.lovelace1@acme.example"))
			.andExpect(jsonPath("$.jobTitle").value("Software Engineer"));
	}

	@Test
	void missingEmployeeReturns404() throws Exception {
		when(employeeService.getById(999)).thenThrow(new NotFoundException("Employee 999 was not found."));

		mockMvc.perform(authenticated(get("/api/employees/999")))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Employee 999 was not found."));
	}

	@Test
	void nonNumericEmployeeIdReturns400() throws Exception {
		mockMvc.perform(authenticated(get("/api/employees/abc"))).andExpect(status().isBadRequest());
		verifyNoInteractions(employeeService);
	}

	@Test
	void employeeDetailRequiresToken() throws Exception {
		mockMvc.perform(get("/api/employees/1")).andExpect(status().isUnauthorized());
		verifyNoInteractions(employeeService);
	}

	private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + hrManagerToken);
	}

	private String token(String authority) {
		return jwtTokenService.issue(new TestingAuthenticationToken("hr@acme.example", null, authority)).value();
	}

	private void stubEmptyPage() {
		when(employeeService.search(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false, false));
	}

	private EmployeeSearchCriteria capturedCriteria() {
		ArgumentCaptor<EmployeeSearchCriteria> captor = ArgumentCaptor.forClass(EmployeeSearchCriteria.class);
		verify(employeeService).search(captor.capture());
		return captor.getValue();
	}

}
