-- Seed a demo tenant and admin user for development only.
-- This migration runs ONLY when SPRING_PROFILES_ACTIVE=dev because it is
-- located in classpath:db/seed (not db/migration) — see application-dev.yml.
--
-- The BCrypt hash below is cost-10. The plaintext password is documented
-- in the project's private dev-setup notes — not here — to avoid committing
-- credentials to version control.
-- If you need to reset it: bcrypt any password at cost 10 and update the hash.

INSERT IGNORE INTO tenant (id, name, slug, status, created_at)
VALUES (
    'aaaaaaaa-0000-0000-0000-000000000001',
    'Demo Company',
    'demo',
    'ACTIVE',
    NOW()
);

INSERT IGNORE INTO `user` (id, tenant_id, email, password_hash, first_name, last_name, role, status, created_at, updated_at)
VALUES (
    'bbbbbbbb-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000001',
    'admin@demo.com',
    '$2b$10$bVyV2/xELbLsYbJow1WgdO6SGZDZjt854RAsR5ENRkHddk4GHtVnO',
    'Demo',
    'Admin',
    'ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
);
