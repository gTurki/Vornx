-- V8: login sessions and refresh tokens.

-- One row per login on one device ("iPhone 15 Pro - Riyadh"). Logging out = revoking the session.
CREATE TABLE auth_sessions (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_type    VARCHAR(20)  NOT NULL, -- ADMIN / PARTNER_USER / CUSTOMER
    user_id      BIGINT       NOT NULL,
    user_agent   VARCHAR(255) NULL,
    ip_address   VARCHAR(45)  NULL,
    last_used_at DATETIME(6)  NOT NULL,
    expires_at   DATETIME(6)  NOT NULL, -- moves forward on every refresh (sliding)
    revoked_at   DATETIME(6)  NULL,
    revoke_reason VARCHAR(30) NULL,     -- LOGOUT / LOGOUT_ALL / REVOKED_BY_USER / TOKEN_REUSE / ACCOUNT_INACTIVE
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    INDEX idx_sessions_user (user_type, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Each refresh token works ONCE. Using it creates the next one (rotation) in the same session.
-- Only the SHA-256 hash is stored.
CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_expiry (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
