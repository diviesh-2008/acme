package com.acme.salary.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.acme.salary.auth.JwtTokenService;
import com.acme.salary.common.ClockConfig;
import com.acme.salary.common.error.ConflictException;
import com.acme.salary.common.error.FieldValidationException;
import com.acme.salary.common.security.SecurityConfig;
import com.acme.salary.common.security.SecurityProblemHandler;
import com.acme.salary.employee.EmployeeNotFoundException;
import com.acme.salary.salary.dto.CorrectSalaryRequest;
import com.acme.salary.salary.dto.CreateSalaryRequest;
import com.acme.salary.salary.dto.SalaryRecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Salary endpoints through the real security chain, JSON binding, validation and error
 * handling. The service is mocked; persistence is covered by SalaryIT.
 */
@WebMvcTest(controllers = SalaryController.class,
		properties = "acme.security.jwt.secret=web-test-signing-secret-of-at-least-32-bytes")
@Import({ SecurityConfig.class, SecurityProblemHandler.class, JwtTokenService.class, ClockConfig.class })
class SalaryControllerTest {

	private static final String BASE = "/api/employees/7/salary";

	private static final SalaryRecordResponse RECORD = new SalaryRecordResponse(15L, new BigDecimal("900000.00"),
			"INR", LocalDate.of(2026, 1, 1));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private SalaryService salaryService;

	// Needed by SecurityConfig's login AuthenticationManager; unused here.
	@MockitoBean
	private UserDetailsService userDetailsService;

	private String token;

	@BeforeEach
	void issueToken() {
		token = token("ROLE_HR_MANAGER");
	}

	// --- read ---------------------------------------------------------------------------------

	@Test
	void currentSalaryReturnsAmountWithTwoDecimalsAndCurrency() throws Exception {
		when(salaryService.currentSalary(7)).thenReturn(RECORD);

		mockMvc.perform(authenticated(get(BASE)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"id":15,"amount":900000.00,"currency":"INR","effectiveDate":"2026-01-01"}""", true))
			.andExpect(content().string(containsString("\"amount\":900000.00")));
	}

	@Test
	void currentSalaryOfMissingEmployeeReturns404() throws Exception {
		when(salaryService.currentSalary(7)).thenThrow(new EmployeeNotFoundException(7));

		mockMvc.perform(authenticated(get(BASE)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Employee 7 was not found."));
	}

	@Test
	void employeeWithoutCurrentSalaryReturns404() throws Exception {
		when(salaryService.currentSalary(7))
			.thenThrow(SalaryRecordNotFoundException.noCurrentSalary(7, LocalDate.of(2026, 9, 19)));

		mockMvc.perform(authenticated(get(BASE)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Employee 7 has no salary effective on or before 2026-09-19."));
	}

	@Test
	void historyReturnsAnArrayInServiceOrder() throws Exception {
		when(salaryService.history(7)).thenReturn(List.of(RECORD,
				new SalaryRecordResponse(12L, new BigDecimal("800000.00"), "INR", LocalDate.of(2025, 1, 1))));

		mockMvc.perform(authenticated(get(BASE + "/history")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(15))
			.andExpect(jsonPath("$[1].effectiveDate").value("2025-01-01"));
	}

	@Test
	void emptyHistoryIsAnEmptyArray() throws Exception {
		when(salaryService.history(7)).thenReturn(List.of());

		mockMvc.perform(authenticated(get(BASE + "/history"))).andExpect(status().isOk()).andExpect(content().json("[]"));
	}

	// --- create -------------------------------------------------------------------------------

	@Test
	void createReturns201AndPassesANormalizedRequest() throws Exception {
		when(salaryService.create(eq(7L), any())).thenReturn(RECORD);

		postSalary("""
				{"amount": 950000.00, "currency": "inr", "effectiveDate": "2026-10-01"}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(15));

		ArgumentCaptor<CreateSalaryRequest> request = ArgumentCaptor.forClass(CreateSalaryRequest.class);
		verify(salaryService).create(eq(7L), request.capture());
		assertThat(request.getValue())
			.isEqualTo(new CreateSalaryRequest(new BigDecimal("950000.00"), "INR", LocalDate.of(2026, 10, 1)));
	}

	@ParameterizedTest(name = "{0}: {1}")
	@CsvSource(delimiter = '|', textBlock = """
			amount        | {"amount": 0, "currency": "INR", "effectiveDate": "2026-10-01"}
			amount        | {"amount": -1, "currency": "INR", "effectiveDate": "2026-10-01"}
			amount        | {"amount": 1.234, "currency": "INR", "effectiveDate": "2026-10-01"}
			amount        | {"amount": 12345678901234, "currency": "INR", "effectiveDate": "2026-10-01"}
			amount        | {"currency": "INR", "effectiveDate": "2026-10-01"}
			amount        | {"amount": "lots", "currency": "INR", "effectiveDate": "2026-10-01"}
			currency      | {"amount": 1, "currency": "RUPEE", "effectiveDate": "2026-10-01"}
			currency      | {"amount": 1, "currency": "ZZZ", "effectiveDate": "2026-10-01"}
			currency      | {"amount": 1, "currency": "IN", "effectiveDate": "2026-10-01"}
			currency      | {"amount": 1, "effectiveDate": "2026-10-01"}
			effectiveDate | {"amount": 1, "currency": "INR"}
			effectiveDate | {"amount": 1, "currency": "INR", "effectiveDate": "2026-13-45"}
			id            | {"id": 99, "amount": 1, "currency": "INR", "effectiveDate": "2026-10-01"}
			createdAt     | {"amount": 1, "currency": "INR", "effectiveDate": "2026-10-01", "createdAt": "2020-01-01T00:00:00Z"}
			""")
	void invalidCreateRequestsReturn400WithFieldError(String field, String body) throws Exception {
		postSalary(body).andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors." + field).exists());
		verifyNoInteractions(salaryService);
	}

	@Test
	void malformedJsonReturns400() throws Exception {
		postSalary("{ not json").andExpect(status().isBadRequest());
		verifyNoInteractions(salaryService);
	}

	@Test
	void duplicateEffectiveDateReturns409() throws Exception {
		when(salaryService.create(eq(7L), any())).thenThrow(new ConflictException("Employee 7 already has one."));

		postSalary(validCreateBody()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Employee 7 already has one."));
	}

	@Test
	void duplicateCaughtOnlyByTheDatabaseAlsoReturns409() throws Exception {
		when(salaryService.create(eq(7L), any()))
			.thenThrow(new DataIntegrityViolationException("Duplicate entry '7-2026-10-01'"));

		postSalary(validCreateBody()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The request conflicts with existing data."))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Duplicate entry"))));
	}

	@Test
	void businessRuleViolationReturns400ForTheField() throws Exception {
		when(salaryService.create(eq(7L), any()))
			.thenThrow(new FieldValidationException("effectiveDate", "must not be before the employee's hire date"));

		postSalary(validCreateBody()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.effectiveDate").value("must not be before the employee's hire date"));
	}

	// --- correct ------------------------------------------------------------------------------

	@Test
	void correctionReturns200WithTheUpdatedRecord() throws Exception {
		when(salaryService.correct(eq(7L), eq(15L), any())).thenReturn(RECORD);

		putSalary(15, """
				{"amount": 960000.00, "currency": "usd"}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(15));

		ArgumentCaptor<CorrectSalaryRequest> request = ArgumentCaptor.forClass(CorrectSalaryRequest.class);
		verify(salaryService).correct(eq(7L), eq(15L), request.capture());
		assertThat(request.getValue()).isEqualTo(new CorrectSalaryRequest(new BigDecimal("960000.00"), "USD"));
	}

	@Test
	void correctionCannotChangeTheEffectiveDate() throws Exception {
		putSalary(15, """
				{"amount": 960000.00, "currency": "INR", "effectiveDate": "2030-01-01"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.effectiveDate").value("Unknown field"));
		verifyNoInteractions(salaryService);
	}

	@ParameterizedTest
	@ValueSource(strings = { """
			{"amount": 0, "currency": "INR"}""", """
			{"amount": 1.001, "currency": "INR"}""", """
			{"amount": 1, "currency": "XYZ"}""", """
			{"currency": "INR"}""" })
	void invalidCorrectionReturns400(String body) throws Exception {
		putSalary(15, body).andExpect(status().isBadRequest());
		verifyNoInteractions(salaryService);
	}

	@Test
	void correctingAnotherEmployeesRecordReturns404() throws Exception {
		when(salaryService.correct(eq(7L), eq(99L), any())).thenThrow(SalaryRecordNotFoundException.forRecord(7, 99));

		putSalary(99, """
				{"amount": 1.00, "currency": "INR"}""")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Salary record 99 was not found for employee 7."));
	}

	@Test
	void salaryRecordsCannotBeDeleted() throws Exception {
		mockMvc.perform(authenticated(delete(BASE + "/15"))).andExpect(status().isMethodNotAllowed());
		verifyNoInteractions(salaryService);
	}

	// --- security -----------------------------------------------------------------------------

	@Test
	void everyEndpointRequiresAToken() throws Exception {
		mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(BASE + "/history")).andExpect(status().isUnauthorized());
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(validCreateBody()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(put(BASE + "/15").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(salaryService);
	}

	@Test
	void tokenWithoutHrManagerRoleReturns403() throws Exception {
		mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ROLE_EMPLOYEE"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(validCreateBody())).andExpect(status().isForbidden());
		verifyNoInteractions(salaryService);
	}

	// --- helpers ------------------------------------------------------------------------------

	private ResultActions postSalary(String body) throws Exception {
		return mockMvc.perform(authenticated(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions putSalary(long salaryId, String body) throws Exception {
		return mockMvc
			.perform(authenticated(put(BASE + "/" + salaryId)).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String validCreateBody() {
		return """
				{"amount": 950000.00, "currency": "INR", "effectiveDate": "2026-10-01"}""";
	}

	private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private String token(String authority) {
		return jwtTokenService.issue(new TestingAuthenticationToken("hr@acme.example", null, authority)).value();
	}

}
