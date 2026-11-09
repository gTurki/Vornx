-- V7: in-app notifications, activity (audit) log, and event reminder tracking.

-- One notification for one recipient:
--   CUSTOMER -> recipient_id = customers.id
--   PARTNER  -> recipient_id = partners.id (the whole team sees it, like a shared inbox)
--   ADMINS   -> recipient_id = NULL (the VORNX team inbox)
CREATE TABLE notifications (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_type VARCHAR(20)  NOT NULL,
    recipient_id   BIGINT       NULL,
    type           VARCHAR(40)  NOT NULL,
    title_ar       VARCHAR(200) NOT NULL,
    title_en       VARCHAR(200) NOT NULL,
    body_ar        VARCHAR(1000) NOT NULL,
    body_en        VARCHAR(1000) NOT NULL,
    reference_type VARCHAR(40)  NULL, -- what to open when tapped, e.g. "SUBSCRIPTION"
    reference_id   BIGINT       NULL,
    read_at        DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    INDEX idx_notifications_recipient (recipient_type, recipient_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- "سجل تدقيق كامل لكل إجراء في المنصة: من نفّذه، ومتى، وعلى أي جهة". Never updated, never deleted.
CREATE TABLE activity_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_type  VARCHAR(20)  NOT NULL,
    actor_id    BIGINT       NULL,
    actor_name  VARCHAR(150) NOT NULL, -- copied at the time, so the log stays readable
    partner_id  BIGINT       NULL,     -- the partner the actor belongs to (for the partner portal's own log)
    action      VARCHAR(60)  NOT NULL, -- e.g. PERMIT_REVOKED
    target_type VARCHAR(40)  NULL,     -- e.g. PERMIT
    target_id   BIGINT       NULL,
    details     VARCHAR(500) NULL,
    ip_address  VARCHAR(45)  NULL,     -- 45 chars fits IPv6
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    INDEX idx_activity_time (created_at),
    INDEX idx_activity_partner_time (partner_id, created_at),
    INDEX idx_activity_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- So the reminder job sends each reminder only once.
ALTER TABLE event_tickets ADD COLUMN reminder_sent_at DATETIME(6) NULL AFTER reminder;
