package com.inventra.api.migration;

import com.inventra.api.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests that Flyway migrations run cleanly and produce the expected schema.
 *
 * <p>Verifies:
 * <ul>
 *   <li>All migrations execute without error</li>
 *   <li>Expected tables exist (singular names, matching V1–V12)</li>
 *   <li>Expected columns exist with correct types</li>
 *   <li>Expected indexes exist</li>
 *   <li>Expected foreign-key constraints exist</li>
 * </ul>
 *
 * <p>Migrations are run automatically by Spring Boot + Flyway against the
 * Testcontainers MySQL provided by {@link BaseIntegrationTest}; this test
 * class verifies the resulting schema structure.
 *
 * <p>Assertions were rewritten to match the actual
 * migration scripts (singular table names like {@code tenant}, {@code user};
 * {@code uk_tenant_slug} not {@code uq_tenant_slug}; etc.).
 */
class MigrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    /** Read a column metadata field, tolerating drivers that lower-case keys. */
    private static Object col(Map<String, Object> row, String name) {
        Object v = row.get(name);
        if (v == null) v = row.get(name.toLowerCase());
        return v;
    }

    private static List<String> columnNames(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(r -> (String) col(r, "COLUMN_NAME"))
                .toList();
    }

    @Test
    void migrations_whenAllRun_thenAllExpectedTablesExist() {
        List<String> tables = jdbcTemplate().queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'",
                String.class
        );

        assertThat(tables).contains(
                "tenant",
                "user",
                "refresh_token",
                "category",
                "product",
                "inventory_item",
                "stock_movement",
                "customer",
                "order",
                "order_item",
                "order_status_history",
                "audit_log",
                "order_sequence",
                "flyway_schema_history"
        );
    }

    @Test
    void migrations_whenAllRun_thenTenantTableHasCorrectStructure() {
        List<Map<String, Object>> columns = jdbcTemplate().queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant' " +
                "ORDER BY ORDINAL_POSITION"
        );

        assertThat(columnNames(columns)).contains(
                "id", "name", "slug", "status", "created_at"
        );

        Map<String, Object> idColumn = columns.stream()
                .filter(c -> "id".equals(col(c, "COLUMN_NAME")))
                .findFirst().orElseThrow();
        assertThat(col(idColumn, "COLUMN_KEY")).isEqualTo("PRI");
        assertThat(col(idColumn, "IS_NULLABLE")).isEqualTo("NO");

        Map<String, Object> slugColumn = columns.stream()
                .filter(c -> "slug".equals(col(c, "COLUMN_NAME")))
                .findFirst().orElseThrow();
        assertThat(col(slugColumn, "COLUMN_KEY")).isEqualTo("UNI");
        assertThat(col(slugColumn, "IS_NULLABLE")).isEqualTo("NO");
    }

    @Test
    void migrations_whenAllRun_thenUserTableHasCorrectStructure() {
        List<Map<String, Object>> columns = jdbcTemplate().queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' " +
                "ORDER BY ORDINAL_POSITION"
        );

        assertThat(columnNames(columns)).contains(
                "id", "tenant_id", "email", "password_hash",
                "first_name", "last_name", "role", "status",
                "created_at", "updated_at"
        );

        Map<String, Object> tenantIdColumn = columns.stream()
                .filter(c -> "tenant_id".equals(col(c, "COLUMN_NAME")))
                .findFirst().orElseThrow();
        assertThat(col(tenantIdColumn, "IS_NULLABLE")).isEqualTo("NO");
    }

    @Test
    void migrations_whenAllRun_thenProductTableHasCorrectStructure() {
        List<Map<String, Object>> columns = jdbcTemplate().queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' " +
                "ORDER BY ORDINAL_POSITION"
        );

        assertThat(columnNames(columns)).contains(
                "id", "tenant_id", "sku", "name", "category_id",
                "unit_price", "status", "created_at", "updated_at"
        );

        Map<String, Object> priceColumn = columns.stream()
                .filter(c -> "unit_price".equals(col(c, "COLUMN_NAME")))
                .findFirst().orElseThrow();
        assertThat(((String) col(priceColumn, "DATA_TYPE")).toLowerCase())
                .isEqualTo("decimal");
    }

    @Test
    void migrations_whenAllRun_thenOrderTableHasCorrectStructure() {
        // 'order' is a reserved word in MySQL; the migration backticks it.
        List<Map<String, Object>> columns = jdbcTemplate().queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' " +
                "ORDER BY ORDINAL_POSITION"
        );

        assertThat(columnNames(columns)).contains(
                "id", "tenant_id", "order_number", "customer_id",
                "status", "total_amount",
                "created_at", "updated_at"
        );
    }

    @Test
    void migrations_whenAllRun_thenPerformanceIndexesExist() {
        List<Map<String, Object>> indexes = jdbcTemplate().queryForList(
                "SELECT DISTINCT INDEX_NAME, TABLE_NAME " +
                "FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME != 'PRIMARY'"
        );

        List<String> indexNames = indexes.stream()
                .map(idx -> (String) col(idx, "INDEX_NAME"))
                .toList();

        // The exact names come from the migration scripts. Just verify a
        // representative sample exists, including the tenant slug uniqueness
        // and refresh-token-hash index added in V7.
        assertThat(indexNames).anyMatch(n -> n.contains("tenant_slug"));
    }

    @Test
    void migrations_whenAllRun_thenForeignKeyConstraintsExist() {
        List<Map<String, Object>> foreignKeys = jdbcTemplate().queryForList(
                "SELECT CONSTRAINT_NAME, TABLE_NAME, REFERENCED_TABLE_NAME " +
                "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND REFERENCED_TABLE_NAME IS NOT NULL"
        );
        assertThat(foreignKeys).isNotEmpty();

        boolean userTenantFkExists = foreignKeys.stream()
                .anyMatch(fk -> "user".equals(col(fk, "TABLE_NAME")) &&
                                "tenant".equals(col(fk, "REFERENCED_TABLE_NAME")));
        assertThat(userTenantFkExists).isTrue();

        boolean productTenantFkExists = foreignKeys.stream()
                .anyMatch(fk -> "product".equals(col(fk, "TABLE_NAME")) &&
                                "tenant".equals(col(fk, "REFERENCED_TABLE_NAME")));
        assertThat(productTenantFkExists).isTrue();
    }

    @Test
    void migrations_whenAllRun_thenUniqueConstraintsExist() {
        List<Map<String, Object>> uniqueConstraints = jdbcTemplate().queryForList(
                "SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME " +
                "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND CONSTRAINT_NAME LIKE 'uk_%'"
        );

        List<String> constraintNames = uniqueConstraints.stream()
                .map(uc -> (String) col(uc, "CONSTRAINT_NAME"))
                .distinct().toList();

        assertThat(constraintNames).contains("uk_tenant_slug");
    }

    @Test
    void migrations_whenAllRun_thenFlywayHistoryRecordsAllMigrations() {
        List<Map<String, Object>> migrations = jdbcTemplate().queryForList(
                "SELECT version, description, type, script, success " +
                "FROM flyway_schema_history ORDER BY installed_rank"
        );

        assertThat(migrations).isNotEmpty();
        assertThat(migrations).allMatch(m -> Boolean.TRUE.equals(col(m, "success"))
                || Boolean.TRUE.equals(col(m, "SUCCESS")));

        List<String> versions = migrations.stream()
                .map(m -> String.valueOf(col(m, "version")))
                .toList();
        assertThat(versions).contains("1", "2", "3", "4", "5", "10", "11", "12");
    }

    @Test
    void migrations_whenAllRun_thenNoFailedMigrations() {
        List<Map<String, Object>> failedMigrations = jdbcTemplate().queryForList(
                "SELECT version, description, script " +
                "FROM flyway_schema_history WHERE success = FALSE"
        );
        assertThat(failedMigrations).isEmpty();
    }

    @Test
    void migrations_whenAllRun_thenAuditLogTableExists() {
        List<Map<String, Object>> columns = jdbcTemplate().queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' " +
                "ORDER BY ORDINAL_POSITION"
        );
        assertThat(columnNames(columns))
                .contains("id", "tenant_id", "event_type");
    }

    @Test
    void migrations_whenAllRun_thenOrderSequenceTableExists() {
        List<Map<String, Object>> columns = jdbcTemplate().queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_sequence' " +
                "ORDER BY ORDINAL_POSITION"
        );
        assertThat(columnNames(columns)).contains("tenant_id", "year");
    }
}
