-- V6: events & tickets, contracts & invoices, support tickets.

-- ===================== EVENTS =====================

CREATE TABLE events (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id        BIGINT        NOT NULL,
    site_id           BIGINT        NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    description       TEXT          NULL,
    category          VARCHAR(80)   NULL,
    starts_at         DATETIME(6)   NOT NULL,
    ends_at           DATETIME(6)   NOT NULL,
    doors_open_at     DATETIME(6)   NULL,
    fee               DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    capacity          INT           NULL, -- NULL = unlimited
    requires_approval BOOLEAN       NOT NULL DEFAULT FALSE,
    dress_code        VARCHAR(100)  NULL,
    contact_phone     VARCHAR(20)   NULL,
    status            VARCHAR(20)   NOT NULL, -- DRAFT / PUBLISHED / CANCELLED
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    CONSTRAINT fk_events_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_events_site FOREIGN KEY (site_id) REFERENCES sites (id),
    CONSTRAINT ck_events_fee CHECK (fee >= 0),
    INDEX idx_events_status_start (status, starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- "برنامج الفعالية" (schedule). position keeps the order.
CREATE TABLE event_agenda (
    event_id  BIGINT       NOT NULL,
    position  INT          NOT NULL,
    item_time TIME         NOT NULL,
    title     VARCHAR(200) NOT NULL,
    PRIMARY KEY (event_id, position),
    CONSTRAINT fk_event_agenda_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Which doors/halls the ticket opens. No rows = the whole site.
CREATE TABLE event_access_points (
    event_id        BIGINT NOT NULL,
    access_point_id BIGINT NOT NULL,
    PRIMARY KEY (event_id, access_point_id),
    CONSTRAINT fk_event_ap_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_event_ap_access_point FOREIGN KEY (access_point_id) REFERENCES access_points (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE event_tickets (
    id                     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_number          VARCHAR(20)  NOT NULL,
    event_id               BIGINT       NOT NULL,
    customer_id            BIGINT       NOT NULL,
    role                   VARCHAR(20)  NOT NULL,
    pass_type              VARCHAR(50)  NULL,
    seat                   VARCHAR(30)  NULL,
    status                 VARCHAR(20)  NOT NULL,
    reminder               VARCHAR(20)  NULL,
    permit_id              BIGINT       NULL, -- the EVENT_PASS permit (once confirmed)
    payment_transaction_id BIGINT       NULL,
    refund_transaction_id  BIGINT       NULL,
    rejection_reason       VARCHAR(500) NULL,
    checked_in_at          DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    CONSTRAINT uk_event_tickets_number UNIQUE (ticket_number),
    CONSTRAINT fk_tickets_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_tickets_permit FOREIGN KEY (permit_id) REFERENCES permits (id),
    CONSTRAINT fk_tickets_payment FOREIGN KEY (payment_transaction_id) REFERENCES wallet_transactions (id),
    CONSTRAINT fk_tickets_refund FOREIGN KEY (refund_transaction_id) REFERENCES wallet_transactions (id),
    INDEX idx_tickets_event_status (event_id, status),
    INDEX idx_tickets_customer (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ===================== BILLING (VORNX <-> partner) =====================

CREATE TABLE contracts (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    number            VARCHAR(20)   NOT NULL,
    partner_id        BIGINT        NOT NULL,
    plan_name         VARCHAR(100)  NOT NULL, -- e.g. "باقة الضيافة"
    tier              VARCHAR(20)   NOT NULL,
    monthly_fee       DECIMAL(15,2) NOT NULL,
    max_beneficiaries INT           NULL,
    start_date        DATE          NOT NULL,
    end_date          DATE          NOT NULL,
    renewal_count     INT           NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL, -- ACTIVE / TERMINATED (EXPIRING_SOON / EXPIRED are calculated)
    notes             VARCHAR(1000) NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    CONSTRAINT uk_contracts_number UNIQUE (number),
    CONSTRAINT fk_contracts_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT ck_contracts_dates CHECK (end_date > start_date),
    INDEX idx_contracts_partner (partner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE invoices (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    number      VARCHAR(20)   NOT NULL,
    contract_id BIGINT        NOT NULL,
    partner_id  BIGINT        NOT NULL,
    period      CHAR(7)       NOT NULL, -- "2026-11"
    amount      DECIMAL(15,2) NOT NULL,
    issue_date  DATE          NOT NULL,
    due_date    DATE          NOT NULL,
    paid_at     DATETIME(6)   NULL,
    status      VARCHAR(20)   NOT NULL, -- DUE / PAID (OVERDUE is calculated)
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT uk_invoices_number UNIQUE (number),
    CONSTRAINT uk_invoices_contract_period UNIQUE (contract_id, period), -- one invoice per month per contract
    CONSTRAINT fk_invoices_contract FOREIGN KEY (contract_id) REFERENCES contracts (id),
    CONSTRAINT fk_invoices_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ===================== SUPPORT =====================

CREATE TABLE support_tickets (
    id                         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    number                     VARCHAR(20)  NOT NULL,
    partner_id                 BIGINT       NOT NULL,
    opened_by_partner_user_id  BIGINT       NULL,
    category                   VARCHAR(30)  NOT NULL,
    subject                    VARCHAR(200) NOT NULL,
    priority                   VARCHAR(10)  NOT NULL,
    status                     VARCHAR(20)  NOT NULL,
    assigned_admin_id          BIGINT       NULL,
    last_message_at            DATETIME(6)  NOT NULL,
    created_at                 DATETIME(6)  NOT NULL,
    updated_at                 DATETIME(6)  NOT NULL,
    CONSTRAINT uk_support_tickets_number UNIQUE (number),
    CONSTRAINT fk_st_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_st_opened_by FOREIGN KEY (opened_by_partner_user_id) REFERENCES partner_users (id),
    CONSTRAINT fk_st_assigned FOREIGN KEY (assigned_admin_id) REFERENCES admin_users (id),
    INDEX idx_st_status_priority (status, priority)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE ticket_messages (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT       NOT NULL,
    sender_type VARCHAR(20)  NOT NULL, -- ADMIN / PARTNER_USER / SYSTEM
    sender_id   BIGINT       NULL,
    sender_name VARCHAR(100) NOT NULL, -- copied at send time, so history stays readable
    body        TEXT         NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_tm_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets (id) ON DELETE CASCADE,
    INDEX idx_tm_ticket_time (ticket_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
