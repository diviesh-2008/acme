package com.acme.salary.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the initial HR Manager account on startup if it does not exist yet.
 * <p>
 * Runs synchronously once all beans are ready (so the schema is migrated) but before the
 * web server starts accepting requests, so the account always exists before the first
 * login. A configuration error stops startup.
 * <p>
 * Safe to run on every startup: an existing account is left unchanged, including its
 * password. Changing {@code ACME_INITIAL_HR_PASSWORD} later does not reset it.
 */
@Component
@EnableConfigurationProperties(InitialHrManagerProperties.class)
public class InitialHrManagerInitializer implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(InitialHrManagerInitializer.class);

	static final int MIN_PASSWORD_LENGTH = 12;

	private final InitialHrManagerProperties properties;

	private final AppUserRepository appUserRepository;

	private final PasswordEncoder passwordEncoder;

	public InitialHrManagerInitializer(InitialHrManagerProperties properties, AppUserRepository appUserRepository,
			PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void afterSingletonsInstantiated() {
		createIfAbsent();
	}

	void createIfAbsent() {
		boolean hasEmail = StringUtils.hasText(properties.email());
		boolean hasPassword = StringUtils.hasText(properties.password());
		if (!hasEmail && !hasPassword) {
			log.warn("No initial HR Manager configured (ACME_INITIAL_HR_EMAIL and ACME_INITIAL_HR_PASSWORD are not set)");
			return;
		}
		if (!hasEmail || !hasPassword) {
			throw new IllegalStateException(
					"Set both ACME_INITIAL_HR_EMAIL and ACME_INITIAL_HR_PASSWORD, or neither");
		}
		validatePassword(properties.password());

		String email = properties.email().strip().toLowerCase(Locale.ROOT);
		if (appUserRepository.existsByEmail(email)) {
			log.info("Initial HR Manager account {} already exists; leaving it unchanged", email);
			return;
		}
		appUserRepository.save(new AppUser(email, passwordEncoder.encode(properties.password()), Role.HR_MANAGER));
		log.info("Created initial HR Manager account {}", email);
	}

	// Messages describe the rule, never the password.
	private static void validatePassword(String password) {
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException(
					"ACME_INITIAL_HR_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > AuthService.BCRYPT_MAX_PASSWORD_BYTES) {
			throw new IllegalStateException("ACME_INITIAL_HR_PASSWORD must be at most "
					+ AuthService.BCRYPT_MAX_PASSWORD_BYTES + " bytes (BCrypt limit)");
		}
	}

}
