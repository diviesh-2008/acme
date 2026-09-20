package com.acme.salary.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.acme.salary.auth.Role;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security: stateless JWT bearer authentication for every API except login.
 * <p>
 * Tokens are validated by Spring Security's OAuth2 Resource Server support (signature,
 * expiry and issuer), so the application has no hand-written JWT filter.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ JwtProperties.class, CorsProperties.class })
public class SecurityConfig {

	/** JWT claim holding the user's role, e.g. {@code "HR_MANAGER"}. */
	public static final String ROLE_CLAIM = "role";

	// HS256 needs a key of at least 256 bits.
	private static final int MIN_SECRET_BYTES = 32;

	// How long a browser may cache a preflight response.
	private static final Duration PREFLIGHT_MAX_AGE = Duration.ofHours(1);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problemHandler,
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		http
			// Auth uses the Authorization header, not cookies, so CSRF does not apply.
			.csrf(AbstractHttpConfigurer::disable)
			// Runs before authorization, so browser preflights are answered without a token.
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
				.requestMatchers("/error").permitAll()
				.anyRequest().hasRole(Role.HR_MANAGER.name()))
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler));
		return http.build();
	}

	/**
	 * Allows the configured origins to call {@code /api/**}. With no configured origin the
	 * source matches nothing, so no {@code Access-Control-Allow-Origin} header is ever sent.
	 * Origins are always listed explicitly: never {@code *}. Credentials stay off because
	 * the browser sends the token in the Authorization header, not in a cookie.
	 */
	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		if (properties.allowedOrigins().isEmpty()) {
			return request -> null;
		}
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setMaxAge(PREFLIGHT_MAX_AGE);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Checks email and password at login; not used for bearer-token requests. */
	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return NimbusJwtEncoder.withSecretKey(signingKey(properties)).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		return decoder;
	}

	// Maps the "role" claim to a ROLE_ authority so hasRole(...) works.
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	// Fails startup on a missing or weak secret. Messages never include the secret itself.
	private static SecretKey signingKey(JwtProperties properties) {
		String secret = properties.secret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(
					"JWT signing secret is not configured. Set the ACME_JWT_SECRET environment variable.");
		}
		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"JWT signing secret must be at least " + MIN_SECRET_BYTES + " bytes (256 bits).");
		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

}
