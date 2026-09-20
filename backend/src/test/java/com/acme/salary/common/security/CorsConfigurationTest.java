package com.acme.salary.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.acme.salary.auth.AppUserDetailsService;
import com.acme.salary.auth.AppUserRepository;
import com.acme.salary.auth.AuthController;
import com.acme.salary.auth.AuthService;
import com.acme.salary.auth.JwtTokenService;
import com.acme.salary.common.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cross-origin behaviour of the API, which production depends on: the deployed frontend
 * is served from a different origin than the backend.
 */
@WebMvcTest(controllers = AuthController.class,
		properties = { "acme.security.jwt.secret=web-test-signing-secret-of-at-least-32-bytes",
				"acme.security.cors.allowed-origins=https://frontend.example" })
@Import({ SecurityConfig.class, SecurityProblemHandler.class, AuthService.class, JwtTokenService.class,
		AppUserDetailsService.class, ClockConfig.class })
class CorsConfigurationTest {

	private static final String ALLOWED_ORIGIN = "https://frontend.example";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AppUserRepository appUserRepository;

	@Test
	void preflightFromConfiguredOriginIsAnsweredWithoutAToken() throws Exception {
		mockMvc
			.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
	}

	@Test
	void preflightFromAnotherOriginIsRejected() throws Exception {
		mockMvc
			.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, "https://attacker.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void allowedOriginIsNamedExplicitlyAndCredentialsStayOff() throws Exception {
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			// Still unauthenticated: CORS decides who may read the response, not who may skip the token.
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	void noConfiguredOriginMeansNoCorsHeadersAtAll() {
		var source = new SecurityConfig().corsConfigurationSource(new CorsProperties(List.of()));

		assertThat(source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/employees"))).isNull();
	}

}
