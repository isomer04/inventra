package com.inventra.api;

import org.junit.jupiter.api.Test;

/**
 * Smoke test that the full application context boots against the real
 * Testcontainers MySQL provided by {@link BaseIntegrationTest}. The
 * previous implementation tried to swap in H2 with
 * {@code flyway.enabled=false} and {@code ddl-auto=validate}, which
 * predictably failed schema validation against an empty database.
 */
class InventraApiApplicationTests extends BaseIntegrationTest {

    @Test
    void contextLoads() {
        // BaseIntegrationTest brings the full Spring context up against
        // the shared Testcontainers MySQL. If that succeeds, the smoke
        // test passes.
    }
}
