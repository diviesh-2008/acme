package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.acme.salary.auth.dto.LoginRequest;
import com.acme.salary.auth.dto.LoginResponse;
import com.acme.salary.common.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Runs the real login pipeline (Spring Security's DaoAuthenticationProvider, BCrypt and
 * JWT signing) with only the database replaced by a mock.
 */
class AuthServiceTest {

	private static final String EMAIL = "hr.manager@acme.example";

	private static final String PASSWORD = "correct-horse-battery-staple";

	private static final String SECRET = "unit-test-signing-secret-of-at-least-32-bytes";

	private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

	// Low BCrypt cost keeps the test fast; production uses the default cost.
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	private final String passwordHash = passwordEncoder.encode(PASSWORD);

	private final AppUserRepository appUserRepository = mock(AppUserRepository.class);

	private final SecretKey signingKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

	private AuthService authService;

	@BeforeEach
	void setUp() {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new AppUserDetailsService(appUserRepository));
		provider.setPasswordEncoder(passwordEncoder);

		JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1), "acme-test");
		NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(signingKey).algorithm(MacAlgorithm.HS256).build();
		JwtTokenService tokenService = new JwtTokenService(encoder, properties, Clock.fixed(NOW, ZoneOffset.UTC));

		authService = new AuthService(new ProviderManager(provider), tokenService);

		when(appUserRepository.findByEmail(EMAIL))
			.thenReturn(Optional.of(new AppUser(EMAIL, passwordHash, Role.HR_MANAGER)));
	}

	@Test
	void validCredentialsReturnSignedTokenForTheUser() {
		LoginResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(3600);
		assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));

		Jwt jwt = decode(response.accessToken());
		assertThat(jwt.getSubject()).isEqualTo(EMAIL);
		assertThat(jwt.getClaimAsString("role")).isEqualTo("HR_MANAGER");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("acme-test");
		assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
		assertThat(jwt.getExpiresAt()).isEqualTo(response.expiresAt());
	}

	@Test
	void incorrectPasswordIsRejected() {
		Throwable thrown = catchThrowable(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")));

		assertThat(thrown).isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void unknownEmailIsRejectedExactlyLikeAWrongPassword() {
		Throwable unknownEmail = catchThrowable(
				() -> authService.login(new LoginRequest("nobody@acme.example", PASSWORD)));
		Throwable wrongPassword = catchThrowable(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")));

		assertThat(unknownEmail).isInstanceOf(BadCredentialsException.class)
			.hasMessage(wrongPassword.getMessage());
	}

	@Test
	void passwordLongerThanBcryptLimitIsRejectedAsBadCredentials() {
		Throwable thrown = catchThrowable(() -> authService.login(new LoginRequest(EMAIL, "x".repeat(73))));

		assertThat(thrown).isInstanceOf(BadCredentialsException.class);
		verify(appUserRepository, never()).findByEmail(anyString());
	}

	@Test
	void passwordAndHashAreNeverReturned() {
		LoginResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

		assertThat(response.toString()).doesNotContain(PASSWORD).doesNotContain(passwordHash);
		Jwt jwt = decode(response.accessToken());
		assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "iat", "exp", "role");
		assertThat(jwt.getClaims().values()).doesNotContain(PASSWORD, passwordHash);
	}

	// Tokens are issued at a fixed past time, so skip the expiry check; the signature is still verified.
	private Jwt decode(String token) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
		return decoder.decode(token);
	}

}
