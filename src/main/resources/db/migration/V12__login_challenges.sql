-- V12: two-step customer login. After the right password, a 6-digit code is sent (email now, SMS later);
-- the login finishes only when the code is typed back.
--   token_hash: SHA-256 of the challenge token the browser holds between the two steps.
--   code_hash:  SHA-256 of (challenge token + code). Without the token (never stored) the 1,000,000 possible
--               codes can't be tried against a leaked database.
CREATE TABLE login_challenges (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_type       VARCHAR(20) NOT NULL,
    user_id         BIGINT      NOT NULL,
    channel         VARCHAR(20) NOT NULL, -- EMAIL (later SMS)
    token_hash      VARCHAR(64) NOT NULL,
    code_hash       VARCHAR(64) NOT NULL,
    code_expires_at DATETIME(6) NOT NULL, -- this code (5 min); a resent code gets a new one
    expires_at      DATETIME(6) NOT NULL, -- the whole challenge (15 min)
    attempts        INT         NOT NULL DEFAULT 0,
    sends           INT         NOT NULL DEFAULT 1,
    last_sent_at    DATETIME(6) NOT NULL,
    consumed_at     DATETIME(6) NULL,     -- used, replaced, or burned by too many wrong codes
    ip_address      VARCHAR(45) NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT uk_login_challenge_token UNIQUE (token_hash),
    INDEX idx_login_challenge_user (user_type, user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
