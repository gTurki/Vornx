-- V2: the three kinds of users who can log in, plus partners (every partner user belongs to one).

CREATE TABLE admin_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(120) NOT NULL,
    phone         VARCHAR(20)  NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_admin_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE partners (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    sector             VARCHAR(30)  NOT NULL,
    cr_number          VARCHAR(30)  NULL,
    phone              VARCHAR(20)  NULL,
    city               VARCHAR(80)  NULL,
    country            VARCHAR(80)  NULL,
    status             VARCHAR(20)  NOT NULL,
    notes              TEXT         NULL,
    account_manager_id BIGINT       NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    CONSTRAINT uk_partners_cr_number UNIQUE (cr_number),
    CONSTRAINT fk_partners_account_manager FOREIGN KEY (account_manager_id) REFERENCES admin_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE partner_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id    BIGINT       NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    last_login_at DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_partner_users_email UNIQUE (email),
    CONSTRAINT fk_partner_users_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE customers (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    vornx_id      VARCHAR(20)  NOT NULL,
    national_id   VARCHAR(20)  NULL,
    first_name    VARCHAR(50)  NOT NULL,
    last_name     VARCHAR(50)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    language      VARCHAR(5)   NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    points        INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_customers_vornx_id UNIQUE (vornx_id),
    CONSTRAINT uk_customers_national_id UNIQUE (national_id),
    CONSTRAINT uk_customers_phone UNIQUE (phone),
    CONSTRAINT uk_customers_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
