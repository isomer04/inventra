-- Optimise refresh token expiry checks and the scheduled cleanup job.
-- The cleanup service deletes rows WHERE expires_at < NOW(), and consumeIfActive
-- filters on (token_hash, revoked, expires_at). This index makes both fast.
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);
