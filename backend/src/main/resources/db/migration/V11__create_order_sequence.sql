-- V11: Sequence table for atomic, contention-safe order number generation.
--
-- Replaces the MAX+1 pattern in
-- OrderNumberGenerator which has a race condition under concurrent submissions —
-- two simultaneous requests could read the same max and generate duplicate numbers.
--
-- Pattern: per-(tenant, year) row with SELECT … FOR UPDATE serialisation.
-- Only OrderNumberGenerator should write to this table.
--
-- Columns:
--   tenant_id  — tenant scope (no FK: survives tenant deletion, year rollover)
--   year        — calendar year of the sequence (resets automatically by design)
--   next_seq    — the LAST allocated sequence number (starts at 0, first ORD is 00001)

CREATE TABLE order_sequence (
    tenant_id  CHAR(36)        NOT NULL,
    year       SMALLINT UNSIGNED NOT NULL,
    next_seq   INT UNSIGNED    NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
