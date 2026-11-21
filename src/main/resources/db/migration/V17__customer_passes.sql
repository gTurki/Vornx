-- V17: cards a customer adds to VORNX by scanning them (loyalty cards, memberships, tickets...).
-- We keep what the card's barcode / QR code contains, so the app can show the same code at the counter.
-- latitude / longitude (optional, set by the customer at the shop): the app shows this card's code when they are near.

CREATE TABLE customer_passes (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id   BIGINT        NOT NULL,
    name          VARCHAR(80)   NOT NULL,          -- "Lavin", "بيت الثقافة"
    code          VARCHAR(2048) NOT NULL,          -- what the barcode / QR contains
    format        VARCHAR(20)   NOT NULL,          -- QR_CODE, PDF417, AZTEC, CODE_128...
    shown_number  VARCHAR(80)   NULL,              -- the number printed under the code, if any
    color         VARCHAR(7)    NOT NULL,          -- #RRGGBB, the card's color
    place_name    VARCHAR(120)  NULL,
    latitude      DOUBLE        NULL,
    longitude     DOUBLE        NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_customer_passes_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    INDEX idx_customer_passes_customer (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
