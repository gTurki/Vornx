-- V11: messages from the public website's contact form ("Get in Touch").
-- Anyone on the internet can send one, so: size limits, no HTML is ever rendered from it,
-- and the IP is kept (with the time) to rate-limit and to trace abuse.
CREATE TABLE inquiries (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(120)  NOT NULL,
    email         VARCHAR(160)  NOT NULL,
    organization  VARCHAR(160)  NOT NULL,
    job_title     VARCHAR(120)  NULL,
    interest      VARCHAR(40)   NOT NULL,  -- TECHNICAL_INTEGRATION, ENTERPRISE, PILOT, STRATEGIC, OTHER
    message       VARCHAR(2000) NOT NULL,
    language      CHAR(2)       NOT NULL,  -- AR / EN: answer in the visitor's language
    ip_address    VARCHAR(45)   NULL,
    user_agent    VARCHAR(255)  NULL,
    status        VARCHAR(20)   NOT NULL,  -- NEW / HANDLED
    handled_by    BIGINT        NULL,
    handled_at    DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_inquiry_admin FOREIGN KEY (handled_by) REFERENCES admin_users (id),
    INDEX idx_inquiry_status (status, created_at),
    INDEX idx_inquiry_ip (ip_address, created_at),
    INDEX idx_inquiry_email (email, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
