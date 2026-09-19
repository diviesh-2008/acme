package com.acme.salary.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The secret comes from the {@code ACME_JWT_SECRET} environment variable
 * and has no default.
 */
@ConfigurationProperties("acme.security.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

	// Records print every component by default; keep the secret out of logs.
	@Override
	public String toString() {
		return "JwtProperties[secret=****, ttl=" + ttl + ", issuer=" + issuer + "]";
	}

}
