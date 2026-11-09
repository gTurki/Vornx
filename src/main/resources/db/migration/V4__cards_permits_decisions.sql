-- V4: the core of VORNX - cards, permits, reader API keys and access decisions.

-- The physical VORNX card. A customer can have several over time (lost -> new one), but only one ACTIVE.
CREATE TABLE cards (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT      NOT NULL,
    card_number VARCHAR(19) NOT NULL,
    issued_at   DATE        NOT NULL,
    expires_at  DATE        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_cards_card_number UNIQUE (card_number),
    CONSTRAINT fk_cards_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Keys used by a partner's readers/locks to call /api/devices/**. Only the SHA-256 hash is stored.
CREATE TABLE api_keys (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id   BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    key_prefix   VARCHAR(16)  NOT NULL, -- first characters, to recognize a key in the list
    key_hash     VARCHAR(64)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_used_at DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_api_keys_key_hash UNIQUE (key_hash),
    CONSTRAINT fk_api_keys_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- A permit = one of the "many cards inside the one card": a hotel key, office access, club membership...
CREATE TABLE permits (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    permit_number     VARCHAR(20)  NOT NULL,
    verification_code VARCHAR(12)  NOT NULL,
    customer_id       BIGINT       NOT NULL,
    partner_id        BIGINT       NOT NULL,
    site_id           BIGINT       NOT NULL,
    type              VARCHAR(30)  NOT NULL,
    level             VARCHAR(20)  NOT NULL,
    valid_from        DATETIME(6)  NOT NULL,
    valid_until       DATETIME(6)  NOT NULL,
    allowed_from      TIME         NULL, -- daily time window (site local time); NULL = any time
    allowed_to        TIME         NULL,
    conditions        VARCHAR(500) NULL,
    status            VARCHAR(20)  NOT NULL, -- ACTIVE / SUSPENDED / REVOKED (EXPIRED is calculated from valid_until)
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    CONSTRAINT uk_permits_permit_number UNIQUE (permit_number),
    CONSTRAINT fk_permits_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_permits_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_permits_site FOREIGN KEY (site_id) REFERENCES sites (id),
    INDEX idx_permits_customer_site (customer_id, site_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Allowed days of the week. No rows = every day.
CREATE TABLE permit_days (
    permit_id   BIGINT      NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    PRIMARY KEY (permit_id, day_of_week),
    CONSTRAINT fk_permit_days_permit FOREIGN KEY (permit_id) REFERENCES permits (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Which access points the permit opens. No rows = every access point in the site.
CREATE TABLE permit_access_points (
    permit_id       BIGINT NOT NULL,
    access_point_id BIGINT NOT NULL,
    PRIMARY KEY (permit_id, access_point_id),
    CONSTRAINT fk_pap_permit FOREIGN KEY (permit_id) REFERENCES permits (id) ON DELETE CASCADE,
    CONSTRAINT fk_pap_access_point FOREIGN KEY (access_point_id) REFERENCES access_points (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Every attempt to open something, allowed OR denied, with the reason. This is the audit trail.
CREATE TABLE access_decisions (
    id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    decision_number    VARCHAR(20) NOT NULL,
    access_point_id    BIGINT      NOT NULL,
    customer_id        BIGINT      NULL, -- NULL when an unknown card was tapped
    card_id            BIGINT      NULL,
    permit_id          BIGINT      NULL,
    api_key_id         BIGINT      NULL, -- which reader asked (NULL for remote open from the app)
    method             VARCHAR(20) NOT NULL,
    direction          VARCHAR(10) NOT NULL,
    decision           VARCHAR(10) NOT NULL,
    deny_reason        VARCHAR(40) NULL,
    enforcement_result VARCHAR(20) NULL, -- reported back by the reader: did the door actually open?
    occurred_at        DATETIME(6) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    CONSTRAINT uk_access_decisions_number UNIQUE (decision_number),
    CONSTRAINT fk_ad_access_point FOREIGN KEY (access_point_id) REFERENCES access_points (id),
    CONSTRAINT fk_ad_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_ad_card FOREIGN KEY (card_id) REFERENCES cards (id),
    CONSTRAINT fk_ad_permit FOREIGN KEY (permit_id) REFERENCES permits (id),
    CONSTRAINT fk_ad_api_key FOREIGN KEY (api_key_id) REFERENCES api_keys (id),
    INDEX idx_ad_customer_time (customer_id, occurred_at),
    INDEX idx_ad_access_point_time (access_point_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
