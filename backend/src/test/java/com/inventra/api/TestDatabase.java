package com.inventra.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

/**
 * Shared MySQL Testcontainer and the Spring wiring needed to make any
 * {@code @SpringBootTest} in this project startable.
 *
 * <p>This exists as a standalone helper rather than living only in
 * {@link BaseIntegrationTest} because that base class also disables the rate limiter.
 * A test that needs the database <em>and</em> the rate limiter enabled had no way to
 * get one without the other, and dropping the inheritance to escape the override
 * silently removed the datasource and JWT secret too — the context then fell back to
 * {@code application.yml}'s development defaults and failed to start.
 *
 * <p>Call {@link #register(DynamicPropertyRegistry)} from a {@code @DynamicPropertySource}
 * method. It deliberately says nothing about rate limiting; each test decides that.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    // One container shared across ALL tests for the entire run. Declared static and
    // started manually so Testcontainers doesn't restart it per test class.
    public static final MySQLContainer<?> MY_SQL;

    static {
        MY_SQL = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("inventra_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        MY_SQL.start();
    }

    /** Registers the datasource, Flyway and JWT properties a Spring context needs to boot. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MY_SQL::getJdbcUrl);
        registry.add("spring.datasource.username", MY_SQL::getUsername);
        registry.add("spring.datasource.password", MY_SQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        // Short-lived tokens so expiry tests run fast
        registry.add("app.jwt.access-token-expiry-ms", () -> "900000");
        registry.add("app.jwt.refresh-token-expiry-ms", () -> "604800000");
        // Fixed test secret (≥ 256 bits when base64-decoded)
        registry.add("app.jwt.secret",
                () -> "dGVzdC1vbmx5LXNlY3JldC1mb3ItaW50ZWdyYXRpb24tdGVzdHMtbm90LWZvci1wcm9kdWN0aW9uLW11c3QtYmUtMjU2LWJpdHM=");
    }
}
