package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Guards the configuration a deployment depends on. These are assertions about the
 * shipped application.yml itself, because getting them wrong is only noticed in
 * production: demo data appearing in a real database, or a secret that has a fallback
 * value and therefore never fails loudly.
 */
class ProductionConfigurationTest {

	private final PropertySource<?> defaults = load("application.yml");

	private final PropertySource<?> devProfile = load("application-dev.yml");

	@Test
	void demoDataIsOffByDefaultAndOnlyTheDevProfileEnablesIt() {
		assertThat(defaults.getProperty("acme.seed.enabled")).isEqualTo(false);
		assertThat(devProfile.getProperty("acme.seed.enabled")).isEqualTo(true);
	}

	@Test
	void secretsAndCredentialsHaveNoCommittedValues() {
		// "${VAR:}" means: take it from the environment, otherwise leave it empty. An empty
		// JWT secret fails startup (SecurityConfig), so production cannot run on a default.
		assertThat(defaults.getProperty("acme.security.jwt.secret")).isEqualTo("${ACME_JWT_SECRET:}");
		assertThat(defaults.getProperty("spring.datasource.username")).isEqualTo("${ACME_DB_USERNAME:}");
		assertThat(defaults.getProperty("spring.datasource.password")).isEqualTo("${ACME_DB_PASSWORD:}");
		assertThat(defaults.getProperty("acme.initial-hr.email")).isEqualTo("${ACME_INITIAL_HR_EMAIL:}");
		assertThat(defaults.getProperty("acme.initial-hr.password")).isEqualTo("${ACME_INITIAL_HR_PASSWORD:}");
	}

	@Test
	void flywayOwnsTheSchemaAndHibernateOnlyValidatesIt() {
		assertThat(defaults.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(defaults.getProperty("spring.flyway.enabled")).isNull();
	}

	@Test
	void theHostingPlatformCanChooseThePortAndLocalRunsStayOn8080() {
		assertThat(defaults.getProperty("server.port")).isEqualTo("${PORT:8080}");
	}

	@Test
	void crossOriginAccessIsClosedUntilAnOriginIsConfigured() {
		assertThat(defaults.getProperty("acme.security.cors.allowed-origins")).isEqualTo("${ACME_ALLOWED_ORIGINS:}");
	}

	private static PropertySource<?> load(String file) {
		try {
			List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
			assertThat(sources).as("%s should contain exactly one document", file).hasSize(1);
			return sources.get(0);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not read " + file, ex);
		}
	}

}
