package com.acme.salary.auth;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import com.acme.salary.common.ClockConfig;
import com.acme.salary.common.security.JwtProperties;
import com.acme.salary.common.security.SecurityConfig;
import com.acme.salary.common.security.SecurityProblemHandler;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Exercises the login and protected endpoints through the real security filter chain,
 * password check, token signing and error handling. Only the database is mocked.
 */
@WebMvcTest(controllers = AuthController.class,
		properties = "acme.security.jwt.secret=web-test-signing-secret-of-at-least-32-bytes")
@Import({ SecurityConfig.class, SecurityProblemHandler.class, AuthService.class, JwtTokenService.class,
		AppUserDetailsService.class, ClockConfig.class })
class AuthControllerTest {

	private static final String EMAIL = "hr.manager@acme.example";

	private static final String PASSWORD = "correct-horse-battery-staple";

	private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JwtProperties jwtProperties;

	@MockitoBean
	private AppUserRepository appUserRepository;

	@BeforeEach
	void storedHrManager() {
		when(appUserRepository.findByEmail(EMAIL))
			.thenReturn(Optional.of(new AppUser(EMAIL, passwordEncoder.encode(PASSWORD), Role.HR_MANAGER)));
	}

	@Nested
	class Login {

		@Test
		void validCredentialsReturn200WithJwt() throws Exception {
			login(EMAIL, PASSWORD).andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.accessToken", not(emptyString())))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.expiresAt").isString())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
		}

		@Test
		void wrongPasswordReturns401() throws Exception {
			login(EMAIL, "wrong-password").andExpect(status().isUnauthorized())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Invalid email or password."))
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(jsonPath("$.exception").doesNotExist());
		}

		@Test
		void unknownEmailReturnsTheSame401() throws Exception {
			login("nobody@acme.example", PASSWORD).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid email or password."));
		}

		@Test
		void malformedJsonReturns400() throws Exception {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.trace").doesNotExist());
		}

		@Test
		void missingFieldsReturn400WithFieldErrors() throws Exception {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.email").exists())
				.andExpect(jsonPath("$.errors.password").exists());
		}

		@Test
		void invalidEmailReturns400() throws Exception {
			login("not-an-email", PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.email").exists());
		}

	}

	@Nested
	class ProtectedEndpoint {

		@Test
		void validJwtIsAccepted() throws Exception {
			String token = JsonPath.read(
					login(EMAIL, PASSWORD).andReturn().getResponse().getContentAsString(), "$.accessToken");

			mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andExpect(jsonPath("$.role").value("HR_MANAGER"));
		}

		@Test
		void missingJwtIsRejectedWith401() throws Exception {
			mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("A valid access token is required."));
		}

		@Test
		void malformedJwtIsRejectedWith401() throws Exception {
			expectUnauthorized("not-a-jwt");
		}

		@Test
		void jwtSignedWithAnotherKeyIsRejectedWith401() throws Exception {
			byte[] otherKey = "a-different-secret-that-is-also-32-bytes-long".getBytes(StandardCharsets.UTF_8);
			JwtEncoder otherEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(otherKey, "HmacSHA256"))
				.algorithm(MacAlgorithm.HS256)
				.build();

			expectUnauthorized(token(otherEncoder, Instant.now(), "HR_MANAGER"));
		}

		@Test
		void expiredJwtIsRejectedWith401() throws Exception {
			expectUnauthorized(token(jwtEncoder, Instant.now().minus(Duration.ofHours(2)), "HR_MANAGER"));
		}

		@Test
		void validJwtWithoutHrManagerRoleIsRejectedWith403() throws Exception {
			mockMvc
				.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,
						"Bearer " + token(jwtEncoder, Instant.now(), "EMPLOYEE")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(PROBLEM_JSON));
		}

		private void expectUnauthorized(String token) throws Exception {
			mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("A valid access token is required."));
		}

		// A token valid for one hour from issuedAt, with the application's issuer.
		private String token(JwtEncoder encoder, Instant issuedAt, String role) {
			JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(jwtProperties.issuer())
				.subject(EMAIL)
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(Duration.ofHours(1)))
				.claim("role", role)
				.build();
			return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		}

	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s"}
					""".formatted(email, password)));
	}

}
