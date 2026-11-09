-- V9: email outbox and password reset tokens.

-- OUTBOX PATTERN: an email is first SAVED here, in the same transaction as the action that caused it.
-- A background job then sends it, with retries. So: no email for a rolled-back action,
-- and no lost email when the mail server is down for a while.
CREATE TABLE email_outbox (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(40)  NOT NULL, -- INVITE, PASSWORD_RESET... (for reporting)
    to_address      VARCHAR(120) NOT NULL,
    subject         VARCHAR(200) NOT NULL,
    html_body       MEDIUMTEXT   NOT NULL,
    text_body       TEXT         NOT NULL, -- plain-text version (helps deliverability)
    status          VARCHAR(20)  NOT NULL, -- PENDING / SENT / FAILED
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500) NULL,
    next_attempt_at DATETIME(6)  NOT NULL,
    sent_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    INDEX idx_outbox_pending (status, next_attempt_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- "Forgot password" links: single use, short-lived, only the hash is stored.
CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_type  VARCHAR(20) NOT NULL,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash),
    INDEX idx_password_reset_user (user_type, user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
