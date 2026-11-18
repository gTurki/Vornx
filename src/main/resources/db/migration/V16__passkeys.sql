-- V16: passkeys = sign in with Face ID / Touch ID / fingerprint / Windows Hello (WebAuthn).
-- The phone keeps the private key; we keep only the public key, so a database leak reveals nothing usable.

CREATE TABLE passkeys (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     BIGINT        NOT NULL,
    credential_id   VARCHAR(512)  NOT NULL,          -- base64url, chosen by the phone
    user_handle     VARCHAR(128)  NOT NULL,          -- base64url, random per customer (no email or id inside)
    public_key_cose VARBINARY(2048) NOT NULL,
    sign_count      BIGINT        NOT NULL DEFAULT 0,
    name            VARCHAR(80)   NOT NULL,          -- "iPhone", "Mac"... shown in "حسابي"
    backed_up       BIT(1)        NOT NULL DEFAULT 0, -- synced by iCloud Keychain / Google Password Manager
    last_used_at    DATETIME(6)   NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT uk_passkeys_credential UNIQUE (credential_id),
    CONSTRAINT fk_passkeys_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    INDEX idx_passkeys_customer (customer_id),
    INDEX idx_passkeys_handle (user_handle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One row per "add a passkey" or "sign in with a passkey" attempt: the random challenge the phone must sign.
-- Used once, valid 5 minutes.
CREATE TABLE passkey_challenges (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token_hash   CHAR(64)     NOT NULL,
    purpose      VARCHAR(20)  NOT NULL,              -- REGISTER / LOGIN
    customer_id  BIGINT       NULL,                  -- REGISTER only
    request_json TEXT         NOT NULL,
    ip_address   VARCHAR(64)  NULL,
    expires_at   DATETIME(6)  NOT NULL,
    used_at      DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_passkey_challenge_token UNIQUE (token_hash),
    INDEX idx_passkey_challenge_expiry (expires_at),
    INDEX idx_passkey_challenge_ip (ip_address, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
