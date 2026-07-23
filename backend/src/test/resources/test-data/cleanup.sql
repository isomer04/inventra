-- Cleanup script run BEFORE_TEST_METHOD by integration tests annotated with
-- @Sql(scripts = "/test-data/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD).
--
-- Truncates all tenant-scoped tables so each test method starts from a clean
-- slate, regardless of what a previous test method left behind. Foreign key
-- checks are disabled for the duration so table order doesn't matter.
--
-- MySQL-specific. Keep the table list in sync with db/migration.

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `audit_log`;
TRUNCATE TABLE `order_status_history`;
TRUNCATE TABLE `order_item`;
TRUNCATE TABLE `order`;
TRUNCATE TABLE `order_sequence`;
TRUNCATE TABLE `stock_movement`;
TRUNCATE TABLE `inventory_item`;
TRUNCATE TABLE `customer`;
TRUNCATE TABLE `product`;
TRUNCATE TABLE `category`;
TRUNCATE TABLE `refresh_token`;
TRUNCATE TABLE `user`;
TRUNCATE TABLE `tenant`;

SET FOREIGN_KEY_CHECKS = 1;
