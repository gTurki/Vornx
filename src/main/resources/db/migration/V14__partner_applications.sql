-- V14: "انضم كشريك" - a company applies on the website; the VORNX team approves (creates the partner and invites
-- the contact as its manager) or rejects (with a reason, emailed to the applicant).
CREATE TABLE partner_applications (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_name   VARCHAR(150) NOT NULL,
    cr_number      VARCHAR(10)  NOT NULL,              -- commercial registration, 10 digits
    sector         VARCHAR(20)  NOT NULL,
    city           VARCHAR(80)  NOT NULL,
    contact_name   VARCHAR(120) NOT NULL,
    job_title      VARCHAR(120) NULL,
    contact_email  VARCHAR(160) NOT NULL,
    contact_phone  VARCHAR(20)  NOT NULL,
    message        VARCHAR(2000) NULL,
    language       VARCHAR(5)   NOT NULL,               -- AR / EN: the language of the emails we send back
    status         VARCHAR(20)  NOT NULL,               -- PENDING, APPROVED, REJECTED
    reject_reason  VARCHAR(500) NULL,
    partner_id     BIGINT       NULL,                   -- the partner created on approval
    reviewed_by_id BIGINT       NULL,
    reviewed_at    DATETIME(6)  NULL,
    ip_address     VARCHAR(45)  NULL,
    user_agent     VARCHAR(255) NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT fk_application_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_application_reviewer FOREIGN KEY (reviewed_by_id) REFERENCES admin_users (id),
    INDEX idx_application_status (status, created_at),
    INDEX idx_application_cr (cr_number),
    INDEX idx_application_ip (ip_address, created_at),
    INDEX idx_application_email (contact_email, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
