package com.acme.salary.auth;

import static com.acme.salary.IntegrationTest.HR_MANAGER_EMAIL;
import static com.acme.salary.IntegrationTest.HR_MANAGER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.salary.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end authentication against MySQL: the Flyway migration, the initial account
 * created on startup, login and a protected request.
 */
@IntegrationTest
class AuthenticationIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private InitialHrManagerInitializer initializer;

	@Test
	void initialHrManagerIsStoredWithBcryptHash() {
		AppUser user = appUserRepository.findByEmail(HR_MANAGER_EMAIL).orElseThrow();

		assertThat(user.getRole()).isEqualTo(Role.HR_MANAGER);
		assertThat(user.getPasswordHash()).isNotEqualTo(HR_MANAGER_PASSWORD).startsWith("$2");
	}

	@Test
	void initializerDoesNotCreateASecondAccountOnRestart() {
		initializer.createIfAbsent();

		assertThat(appUserRepository.count()).isEqualTo(1);
	}

	@Test
	void loginTokenGrantsAccessToProtectedEndpoint() throws Exception {
		String token = JsonPath.read(
				login(HR_MANAGER_EMAIL, HR_MANAGER_PASSWORD).andExpect(status().isOk())
					.andReturn()
					.getResponse()
					.getContentAsString(),
				"$.accessToken");

		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(HR_MANAGER_EMAIL))
			.andExpect(jsonPath("$.role").value("HR_MANAGER"));
	}

	@Test
	void emailMatchingIgnoresLetterCase() throws Exception {
		login(HR_MANAGER_EMAIL.toUpperCase(), HR_MANAGER_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void wrongPasswordReturns401() throws Exception {
		login(HR_MANAGER_EMAIL, "wrong-password").andExpect(status().isUnauthorized());
	}

	@Test
	void protectedEndpointRejectsMissingToken() throws Exception {
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s"}
					""".formatted(email, password)));
	}

}
