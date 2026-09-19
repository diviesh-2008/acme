package com.acme.salary;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Marks a full-context integration test backed by the shared MySQL container.
 * <p>
 * Every integration test uses this same configuration, so Spring caches one
 * application context and starts one container for the whole test run.
 * Name integration test classes {@code *IT} so they run in the {@code verify} phase.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
