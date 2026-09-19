package com.acme.salary.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The HR Manager account to create on startup, from {@code ACME_INITIAL_HR_EMAIL} and
 * {@code ACME_INITIAL_HR_PASSWORD}. Both are optional; neither has a default.
 */
@ConfigurationProperties("acme.initial-hr")
public record InitialHrManagerProperties(String email, String password) {

	// Records print every component by default; keep the password out of logs.
	@Override
	public String toString() {
		return "InitialHrManagerProperties[email=" + email + ", password=****]";
	}

}
