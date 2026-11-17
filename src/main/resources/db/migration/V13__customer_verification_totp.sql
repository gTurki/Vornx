-- V13: verified email and mobile, accepted terms, and the authenticator app (TOTP) for customers.
--
-- email_verified_at: the customer typed a code we emailed (sign-up, any login by email, or a password reset).
-- phone_verified_at: the customer typed a code we sent by WhatsApp to THIS number.
-- terms_version:     which version of the terms + privacy policy they accepted at sign-up, and when.
-- totp_secret:       the authenticator app's secret, ENCRYPTED (AES-GCM) - it can create valid codes, so it is
--                    never stored in the clear. totp_pending_secret = scanned but not confirmed with a code yet.
-- totp_last_step:    the 30-second step of the last accepted code: the same code can't be used twice.
ALTER TABLE customers
    ADD COLUMN email_verified_at   DATETIME(6)  NULL,
    ADD COLUMN phone_verified_at   DATETIME(6)  NULL,
    ADD COLUMN terms_version       VARCHAR(20)  NULL,
    ADD COLUMN terms_accepted_at   DATETIME(6)  NULL,
    ADD COLUMN totp_secret         VARCHAR(255) NULL,
    ADD COLUMN totp_pending_secret VARCHAR(255) NULL,
    ADD COLUMN totp_enabled_at     DATETIME(6)  NULL,
    ADD COLUMN totp_last_step      BIGINT       NULL;

-- The challenge table now serves 3 purposes: LOGIN, SIGNUP (confirm the email) and PHONE (confirm the mobile).
-- destination: for PHONE, the number the code was sent to (if it changes meanwhile, the code is useless).
ALTER TABLE login_challenges
    ADD COLUMN purpose     VARCHAR(20) NOT NULL DEFAULT 'LOGIN',
    ADD COLUMN destination VARCHAR(30) NULL,
    DROP INDEX idx_login_challenge_user,
    ADD INDEX idx_login_challenge_user (user_type, user_id, purpose, created_at);

-- One-time recovery codes for a customer who lost the phone with the authenticator app. Only hashes.
CREATE TABLE customer_recovery_codes (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT      NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    used_at     DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_recovery_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    INDEX idx_recovery_customer (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
