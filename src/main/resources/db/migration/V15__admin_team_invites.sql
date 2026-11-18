-- V15: "فريق VORNX". A SUPER_ADMIN invites colleagues by email instead of sharing passwords.
-- An invited admin has no password until they open the link and choose one.

ALTER TABLE admin_users
    MODIFY password_hash VARCHAR(100) NULL,
    ADD COLUMN invite_token_hash VARCHAR(64) NULL AFTER status,
    ADD COLUMN invite_expires_at DATETIME(6) NULL AFTER invite_token_hash,
    ADD COLUMN last_login_at     DATETIME(6) NULL AFTER invite_expires_at,
    ADD CONSTRAINT uk_admin_users_invite_token UNIQUE (invite_token_hash);
