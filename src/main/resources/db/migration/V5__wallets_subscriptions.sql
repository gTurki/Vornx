-- V5: wallets and subscriptions.

-- One wallet per customer. DECIMAL (never FLOAT/DOUBLE) for money: 0.1 + 0.2 must be exactly 0.3.
CREATE TABLE wallets (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT        NOT NULL,
    iban        VARCHAR(24)   NOT NULL,
    balance     DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency    CHAR(3)       NOT NULL DEFAULT 'SAR',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT uk_wallets_customer UNIQUE (customer_id),
    CONSTRAINT uk_wallets_iban UNIQUE (iban),
    CONSTRAINT fk_wallets_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_wallets_balance CHECK (balance >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Every money movement. Rows are never updated or deleted: the balance can always be re-calculated from them.
CREATE TABLE wallet_transactions (
    id                     BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reference              VARCHAR(20)   NOT NULL,
    wallet_id              BIGINT        NOT NULL,
    type                   VARCHAR(20)   NOT NULL,
    amount                 DECIMAL(15,2) NOT NULL, -- always positive; the type says in or out
    balance_after          DECIMAL(15,2) NOT NULL,
    description            VARCHAR(255)  NULL,
    counterparty_wallet_id BIGINT        NULL, -- the other side of a transfer
    partner_id             BIGINT        NULL, -- who was paid / who refunded
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    CONSTRAINT uk_wallet_tx_reference UNIQUE (reference),
    CONSTRAINT fk_wallet_tx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_wallet_tx_counterparty FOREIGN KEY (counterparty_wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_wallet_tx_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT ck_wallet_tx_amount CHECK (amount > 0),
    INDEX idx_wallet_tx_wallet_time (wallet_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- What a partner sells, e.g. "Annual gym membership". Approving a subscription issues a permit from this template.
CREATE TABLE subscription_plans (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id        BIGINT        NOT NULL,
    site_id           BIGINT        NOT NULL,
    name              VARCHAR(150)  NOT NULL,
    description       VARCHAR(500)  NULL,
    permit_type       VARCHAR(30)   NOT NULL,
    billing_period    VARCHAR(20)   NOT NULL,
    price             DECIMAL(15,2) NOT NULL,
    requires_approval BOOLEAN       NOT NULL DEFAULT TRUE,
    allowed_from      TIME          NULL,
    allowed_to        TIME          NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    CONSTRAINT fk_plans_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_plans_site FOREIGN KEY (site_id) REFERENCES sites (id),
    CONSTRAINT ck_plans_price CHECK (price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE subscription_plan_days (
    plan_id     BIGINT      NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    PRIMARY KEY (plan_id, day_of_week),
    CONSTRAINT fk_plan_days_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE subscription_plan_access_points (
    plan_id         BIGINT NOT NULL,
    access_point_id BIGINT NOT NULL,
    PRIMARY KEY (plan_id, access_point_id),
    CONSTRAINT fk_plan_ap_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_ap_access_point FOREIGN KEY (access_point_id) REFERENCES access_points (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- A customer's subscription to a plan. permit_id = the permit that opens the doors for it.
CREATE TABLE subscriptions (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    number      VARCHAR(20) NOT NULL,
    customer_id BIGINT      NOT NULL,
    plan_id     BIGINT      NOT NULL,
    partner_id  BIGINT      NOT NULL,
    start_at    DATETIME(6) NULL, -- NULL until approved
    end_at      DATETIME(6) NULL,
    status      VARCHAR(20) NOT NULL, -- PENDING / ACTIVE / CANCELLED / REJECTED (EXPIRED is calculated)
    permit_id   BIGINT      NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_subscriptions_number UNIQUE (number),
    CONSTRAINT fk_subscriptions_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id),
    CONSTRAINT fk_subscriptions_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_subscriptions_permit FOREIGN KEY (permit_id) REFERENCES permits (id),
    INDEX idx_subscriptions_customer_plan (customer_id, plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- "طلب تفعيل" / "طلب تجديد": the customer pays, the partner approves or rejects (rejection = automatic refund).
CREATE TABLE service_requests (
    id                         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_number             VARCHAR(20)   NOT NULL,
    subscription_id            BIGINT        NOT NULL,
    customer_id                BIGINT        NOT NULL,
    partner_id                 BIGINT        NOT NULL,
    type                       VARCHAR(20)   NOT NULL,
    status                     VARCHAR(20)   NOT NULL,
    amount                     DECIMAL(15,2) NOT NULL,
    payment_transaction_id     BIGINT        NULL, -- NULL for free plans
    refund_transaction_id      BIGINT        NULL,
    rejection_reason           VARCHAR(500)  NULL,
    decided_at                 DATETIME(6)   NULL,
    decided_by_partner_user_id BIGINT        NULL, -- NULL = decided automatically
    created_at                 DATETIME(6)   NOT NULL,
    updated_at                 DATETIME(6)   NOT NULL,
    CONSTRAINT uk_service_requests_number UNIQUE (request_number),
    CONSTRAINT fk_sr_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_sr_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_sr_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_sr_payment_tx FOREIGN KEY (payment_transaction_id) REFERENCES wallet_transactions (id),
    CONSTRAINT fk_sr_refund_tx FOREIGN KEY (refund_transaction_id) REFERENCES wallet_transactions (id),
    CONSTRAINT fk_sr_decided_by FOREIGN KEY (decided_by_partner_user_id) REFERENCES partner_users (id),
    INDEX idx_sr_partner_status (partner_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
