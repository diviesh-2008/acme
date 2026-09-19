package com.acme.salary;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides a throwaway MySQL database for integration tests. Spring Boot wires the
 * datasource to the container automatically, so no local database setup is needed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Pinned so every run uses the same MySQL version.
	private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4");

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(MYSQL_IMAGE);
	}

}
