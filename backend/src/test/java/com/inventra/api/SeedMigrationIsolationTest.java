package com.inventra.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Guards against accidentally moving the demo-user seed migration into
 * the production migration path (classpath:db/migration).
 *
 * <p>The V6__seed_demo_user.sql migration creates a demo tenant and admin
 * account with a well-known password. It must ONLY run in the dev profile,
 * loaded via classpath:db/seed (see application-dev.yml). If it were ever
 * placed in classpath:db/migration it would run in production and create a
 * publicly-known admin account.
 */
@DisplayName("Seed migration isolation guard")
class SeedMigrationIsolationTest {

    private static final String SEED_MIGRATION_NAME = "V6__seed_demo_user.sql";

    @Test
    @DisplayName("V6 seed migration must NOT exist in classpath:db/migration")
    void seedMigrationMustNotBeInProductionMigrationPath() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] migrationResources = resolver.getResources("classpath:db/migration/*.sql");

        List<String> migrationFileNames = Arrays.stream(migrationResources)
                .map(Resource::getFilename)
                .toList();

        assertThat(migrationFileNames)
                .as("V6__seed_demo_user.sql must live in classpath:db/seed only — "
                        + "it creates a demo account with a known password and must never "
                        + "run in production. Found it in db/migration, which would make "
                        + "it run on every environment including prod.")
                .doesNotContain(SEED_MIGRATION_NAME);
    }

    @Test
    @DisplayName("V6 seed migration must exist in classpath:db/seed")
    void seedMigrationMustExistInSeedPath() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] seedResources = resolver.getResources("classpath:db/seed/*.sql");

        List<String> seedFileNames = Arrays.stream(seedResources)
                .map(Resource::getFilename)
                .toList();

        assertThat(seedFileNames)
                .as("V6__seed_demo_user.sql should exist in classpath:db/seed so it "
                        + "remains available for local development.")
                .contains(SEED_MIGRATION_NAME);
    }
}
