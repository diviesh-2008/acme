package com.acme.salary.common.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-origin settings. In production the Angular app is served from a different origin
 * than the API, so that origin has to be named explicitly through
 * {@code ACME_ALLOWED_ORIGINS}. The list is empty by default, which disables CORS
 * entirely; local development needs no entries because {@code ng serve} proxies
 * {@code /api} and requests are therefore same-origin.
 */
@ConfigurationProperties("acme.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}

}
