-- V3: partner staff invitations, sites and access points.

-- Invited staff have no password until they accept the invite.
-- We store only a SHA-256 hash of the invite token (like a password), never the token itself.
ALTER TABLE partner_users
    MODIFY password_hash VARCHAR(100) NULL,
    ADD COLUMN invite_token_hash VARCHAR(64) NULL AFTER status,
    ADD COLUMN invite_expires_at DATETIME(6) NULL AFTER invite_token_hash,
    ADD CONSTRAINT uk_partner_users_invite_token UNIQUE (invite_token_hash);

-- A physical place run by a partner: a hotel, an office tower, a club, a parking lot, a venue...
CREATE TABLE sites (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id BIGINT       NOT NULL,
    name       VARCHAR(150) NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    address    VARCHAR(255) NULL,
    city       VARCHAR(80)  NULL,
    country    VARCHAR(80)  NULL,
    latitude   DOUBLE       NULL,
    longitude  DOUBLE       NULL,
    opens_at   TIME         NULL, -- NULL = open 24 hours
    closes_at  TIME         NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_sites_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Something that can be opened inside a site: a room, door, gate, barrier, turnstile...
CREATE TABLE access_points (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    site_id    BIGINT       NOT NULL,
    code       VARCHAR(30)  NOT NULL, -- e.g. "1204" for a hotel room, "GATE-E" for a gate
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    floor      VARCHAR(20)  NULL,
    status     VARCHAR(20)  NOT NULL,
    online     BOOLEAN      NOT NULL DEFAULT FALSE, -- reported by the reader/lock, not by people
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_access_points_site_code UNIQUE (site_id, code),
    CONSTRAINT fk_access_points_site FOREIGN KEY (site_id) REFERENCES sites (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Which ways of opening each access point supports (CARD, MOBILE, QR, FACE...). One row per method.
CREATE TABLE access_point_methods (
    access_point_id BIGINT      NOT NULL,
    method          VARCHAR(20) NOT NULL,
    PRIMARY KEY (access_point_id, method),
    CONSTRAINT fk_access_point_methods_ap FOREIGN KEY (access_point_id) REFERENCES access_points (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
