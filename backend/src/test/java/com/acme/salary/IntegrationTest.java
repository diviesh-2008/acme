package com.acme.salary;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Marks a full-context integration test backed by the shared MySQL container.
 * <p>
 * Every integration test uses this same configuration, so Spring caches one
 * application context and starts one container for the whole test run.
 * Name integration test classes {@code *IT} so they run in the {@code verify} phase.
 * <p>
 * The secret and HR Manager credentials below are test-only values for a throwaway
 * database; they are not used anywhere else. The 10,000-employee seed is loaded once per
 * run, so queries are tested at realistic volume.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
		"acme.security.jwt.secret=integration-test-signing-secret-of-32-bytes",
		"acme.initial-hr.email=" + IntegrationTest.HR_MANAGER_EMAIL,
		"acme.initial-hr.password=" + IntegrationTest.HR_MANAGER_PASSWORD,
		"acme.seed.enabled=true" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

	/** The initial HR Manager account created on startup. */
	String HR_MANAGER_EMAIL = "hr.manager@acme.example";

	String HR_MANAGER_PASSWORD = "integration-test-password";

}
