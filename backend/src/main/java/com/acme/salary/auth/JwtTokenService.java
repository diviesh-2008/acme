package com.acme.salary.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.acme.salary.common.security.JwtProperties;
import com.acme.salary.common.security.SecurityConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed access tokens for authenticated users.
 */
@Service
public class JwtTokenService {

	private static final String ROLE_PREFIX = "ROLE_";

	private final JwtEncoder jwtEncoder;

	private final JwtProperties properties;

	private final Clock clock;

	public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public IssuedToken issue(Authentication authentication) {
		// JWT times have one-second precision; truncate so expiresAt matches the "exp" claim.
		Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = issuedAt.plus(properties.ttl());

		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(authentication.getName())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.claim(SecurityConfig.ROLE_CLAIM, roleOf(authentication))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, issuedAt, expiresAt);
	}

	// Spring Security also grants non-role authorities such as FACTOR_PASSWORD;
	// only the role belongs in the token.
	private static String roleOf(Authentication authentication) {
		return authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(authority -> authority.startsWith(ROLE_PREFIX))
			.map(authority -> authority.substring(ROLE_PREFIX.length()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
	}

	public record IssuedToken(String value, Instant issuedAt, Instant expiresAt) {
	}

}
