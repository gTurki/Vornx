-- V10: wallet top-ups paid by card through Moyasar (https://moyasar.com).
--
-- Flow: we create a Moyasar INVOICE and send the customer to Moyasar's hosted payment page.
-- Card details never reach our servers. When the customer comes back (or Moyasar calls us, or the
-- reconcile job runs) we FETCH the invoice from Moyasar with our secret key, and only if it is
-- "paid" with the exact amount do we credit the wallet - once (wallet_transaction_id is set then).
CREATE TABLE wallet_top_ups (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reference             VARCHAR(20)   NOT NULL,              -- ours, e.g. TU-7KQ2M9XA4B (shown to the customer)
    customer_id           BIGINT        NOT NULL,
    amount                DECIMAL(15,2) NOT NULL,
    currency              CHAR(3)       NOT NULL,
    provider              VARCHAR(20)   NOT NULL,              -- MOYASAR
    provider_invoice_id   VARCHAR(64)   NULL,                  -- Moyasar's invoice id (uuid)
    checkout_url          VARCHAR(500)  NULL,                  -- Moyasar's hosted payment page
    status                VARCHAR(20)   NOT NULL,              -- PENDING / PAID / FAILED / EXPIRED
    failure_reason        VARCHAR(255)  NULL,
    wallet_transaction_id BIGINT        NULL,                  -- set when the wallet was credited
    expires_at            DATETIME(6)   NOT NULL,
    paid_at               DATETIME(6)   NULL,
    last_checked_at       DATETIME(6)   NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    CONSTRAINT uq_top_up_reference UNIQUE (reference),
    CONSTRAINT uq_top_up_invoice UNIQUE (provider_invoice_id),
    CONSTRAINT uq_top_up_wallet_tx UNIQUE (wallet_transaction_id), -- one payment can never credit twice
    CONSTRAINT fk_top_up_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_top_up_wallet_tx FOREIGN KEY (wallet_transaction_id) REFERENCES wallet_transactions (id),
    CONSTRAINT ck_top_up_amount CHECK (amount > 0),
    INDEX idx_top_up_customer (customer_id, created_at),
    INDEX idx_top_up_pending (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
