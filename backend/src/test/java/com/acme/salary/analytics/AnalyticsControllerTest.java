package com.acme.salary.analytics;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.acme.salary.analytics.dto.AnalyticsOverviewResponse;
import com.acme.salary.analytics.dto.CountrySalaryStatistics;
import com.acme.salary.analytics.dto.CurrencySalaryStatistics;
import com.acme.salary.analytics.dto.DepartmentSalaryStatistics;
import com.acme.salary.auth.JwtTokenService;
import com.acme.salary.common.ClockConfig;
import com.acme.salary.common.security.SecurityConfig;
import com.acme.salary.common.security.SecurityProblemHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Analytics endpoints through the real security chain and JSON serialization. The service
 * is mocked; the statistics themselves are covered by AnalyticsServiceTest and AnalyticsIT.
 */
@WebMvcTest(controllers = AnalyticsController.class,
		properties = "acme.security.jwt.secret=web-test-signing-secret-of-at-least-32-bytes")
@Import({ SecurityConfig.class, SecurityProblemHandler.class, JwtTokenService.class, ClockConfig.class })
class AnalyticsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private AnalyticsService analyticsService;

	// Needed by SecurityConfig's login AuthenticationManager; unused here.
	@MockitoBean
	private UserDetailsService userDetailsService;

	private String token;

	@BeforeEach
	void issueToken() {
		token = jwtTokenService.issue(new TestingAuthenticationToken("hr@acme.example", null, "ROLE_HR_MANAGER"))
			.value();
	}

	@Test
	void overviewReturnsOneEntryPerCurrency() throws Exception {
		when(analyticsService.overview()).thenReturn(new AnalyticsOverviewResponse(
				Instant.parse("2026-09-20T08:30:00Z"), LocalDate.of(2026, 9, 20),
				List.of(stats("INR", 1433, "2759634.33", "2738000.00", "1361000.00", "4689000.00"),
						stats("USD", 2999, "126293.86", "125100.00", "61600.00", "209700.00"))));

		mockMvc.perform(authenticated("/api/analytics/overview"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"generatedAt": "2026-09-20T08:30:00Z", "asOfDate": "2026-09-20",
					 "currencies": [
					   {"currency": "INR", "employeeCount": 1433, "averageSalary": 2759634.33,
					    "medianSalary": 2738000.00, "minimumSalary": 1361000.00, "maximumSalary": 4689000.00},
					   {"currency": "USD", "employeeCount": 2999, "averageSalary": 126293.86,
					    "medianSalary": 125100.00, "minimumSalary": 61600.00, "maximumSalary": 209700.00}]}""",
					true))
			.andExpect(content().string(containsString("\"medianSalary\":2738000.00")));
	}

	@Test
	void byCountryReturnsCountryAndCurrencyRows() throws Exception {
		when(analyticsService.byCountry()).thenReturn(List.of(
				new CountrySalaryStatistics("IN", "INR", 2, money("900000.00"), money("900000.00"),
						money("800000.00"), money("1000000.00")),
				new CountrySalaryStatistics("IN", "USD", 1, money("90000.00"), money("90000.00"), money("90000.00"),
						money("90000.00"))));

		mockMvc.perform(authenticated("/api/analytics/by-country"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					[{"country": "IN", "currency": "INR", "employeeCount": 2, "averageSalary": 900000.00,
					  "medianSalary": 900000.00, "minimumSalary": 800000.00, "maximumSalary": 1000000.00},
					 {"country": "IN", "currency": "USD", "employeeCount": 1, "averageSalary": 90000.00,
					  "medianSalary": 90000.00, "minimumSalary": 90000.00, "maximumSalary": 90000.00}]""", true));
	}

	@Test
	void byDepartmentReturnsDepartmentAndCurrencyRows() throws Exception {
		when(analyticsService.byDepartment()).thenReturn(List.of(new DepartmentSalaryStatistics("Engineering", "GBP",
				1, money("70000.00"), money("70000.00"), money("70000.00"), money("70000.00"))));

		mockMvc.perform(authenticated("/api/analytics/by-department"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					[{"department": "Engineering", "currency": "GBP", "employeeCount": 1, "averageSalary": 70000.00,
					  "medianSalary": 70000.00, "minimumSalary": 70000.00, "maximumSalary": 70000.00}]""", true));
	}

	@Test
	void noCurrentSalariesReturn200WithEmptyResults() throws Exception {
		when(analyticsService.overview()).thenReturn(
				new AnalyticsOverviewResponse(Instant.parse("2026-09-20T08:30:00Z"), LocalDate.of(2026, 9, 20), List.of()));
		when(analyticsService.byCountry()).thenReturn(List.of());
		when(analyticsService.byDepartment()).thenReturn(List.of());

		mockMvc.perform(authenticated("/api/analytics/overview"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"currencies": []}"""));
		mockMvc.perform(authenticated("/api/analytics/by-country")).andExpect(status().isOk()).andExpect(content().json("[]"));
		mockMvc.perform(authenticated("/api/analytics/by-department"))
			.andExpect(status().isOk())
			.andExpect(content().json("[]"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/analytics/overview", "/api/analytics/by-country", "/api/analytics/by-department" })
	void missingTokenReturns401(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(analyticsService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/analytics/overview", "/api/analytics/by-country", "/api/analytics/by-department" })
	void invalidOrTamperedTokenReturns401(String path) throws Exception {
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

		mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(analyticsService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/analytics/overview", "/api/analytics/by-country", "/api/analytics/by-department" })
	void tokenWithoutHrManagerRoleReturns403(String path) throws Exception {
		String employeeToken = jwtTokenService
			.issue(new TestingAuthenticationToken("someone@acme.example", null, "ROLE_EMPLOYEE"))
			.value();

		mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
			.andExpect(status().isForbidden());
		verifyNoInteractions(analyticsService);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(String path) {
		return get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static CurrencySalaryStatistics stats(String currency, long count, String average, String median,
			String minimum, String maximum) {
		return new CurrencySalaryStatistics(currency, count, money(average), money(median), money(minimum),
				money(maximum));
	}

	private static BigDecimal money(String amount) {
		return new BigDecimal(amount);
	}

}
