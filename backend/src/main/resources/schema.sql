-- 二手车交易管理系统
-- MySQL 8.0.16+。请先连接目标数据库，再执行本文件。
-- Spring Boot 使用同一份脚本初始化空数据库；已有结构须使用迁移脚本升级。

CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_role (
    code VARCHAR(12) PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    CHECK (code IN ('USER', 'STAFF', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_code VARCHAR(12) NOT NULL,
    PRIMARY KEY (user_id, role_code),
    FOREIGN KEY (user_id) REFERENCES app_user (id),
    FOREIGN KEY (role_code) REFERENCES app_role (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(40) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS city (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(40) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vehicle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    city_id BIGINT NOT NULL,
    model VARCHAR(80) NOT NULL,
    registration_year SMALLINT NOT NULL CHECK (registration_year >= 1980),
    mileage INT NOT NULL CHECK (mileage >= 0),
    price DECIMAL(12,2) NOT NULL CHECK (price > 0),
    transmission VARCHAR(12) NOT NULL CHECK (transmission IN ('AUTO', 'MANUAL')),
    fuel VARCHAR(12) NOT NULL CHECK (fuel IN ('PETROL', 'HYBRID', 'ELECTRIC', 'DIESEL')),
    sale_mode VARCHAR(12) NOT NULL CHECK (sale_mode IN ('DIRECT', 'CONSIGN')),
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'REJECTED', 'LISTED', 'LOCKED', 'SOLD', 'OFFLINE')),
    review_note VARCHAR(500),
    reviewer_id BIGINT,
    reviewed_at DATETIME,
    listed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, owner_id),
    INDEX idx_vehicle_brand_price (status, brand_id, price),
    INDEX idx_vehicle_city_year (status, city_id, registration_year),
    INDEX idx_vehicle_mileage (status, mileage),
    INDEX idx_vehicle_listing (status, listed_at, id),
    INDEX idx_vehicle_owner (owner_id, status),
    FOREIGN KEY (owner_id) REFERENCES app_user (id),
    FOREIGN KEY (brand_id) REFERENCES brand (id),
    FOREIGN KEY (city_id) REFERENCES city (id),
    FOREIGN KEY (reviewer_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    starts_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    fee DECIMAL(12,2) NOT NULL CHECK (fee >= 0),
    terms VARCHAR(600),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED', 'COMPLETED')),
    reviewer_id BIGINT,
    review_note VARCHAR(500),
    reviewed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (expires_at > starts_at),
    UNIQUE (vehicle_id, id),
    INDEX idx_consignment_expiry (status, expires_at),
    FOREIGN KEY (vehicle_id, owner_id) REFERENCES vehicle (id, owner_id),
    FOREIGN KEY (reviewer_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vehicle_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    source VARCHAR(100) NOT NULL,
    report_date DATE NOT NULL,
    summary VARCHAR(1200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note VARCHAR(500),
    reviewer_id BIGINT,
    reviewed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (vehicle_id, id),
    INDEX idx_report_vehicle (vehicle_id, status),
    FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    FOREIGN KEY (author_id) REFERENCES app_user (id),
    FOREIGN KEY (reviewer_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    report_id BIGINT,
    uploader_id BIGINT NOT NULL,
    kind VARCHAR(12) NOT NULL,
    storage_key VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    mime VARCHAR(80) NOT NULL,
    bytes BIGINT NOT NULL CHECK (bytes > 0),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((kind = 'PHOTO' AND report_id IS NULL) OR (kind = 'REPORT' AND report_id IS NOT NULL)),
    INDEX idx_attachment_vehicle (vehicle_id, kind, id),
    FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    FOREIGN KEY (vehicle_id, report_id) REFERENCES vehicle_report (vehicle_id, id),
    FOREIGN KEY (uploader_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    handler_id BIGINT,
    visit_at DATETIME NOT NULL,
    note VARCHAR(400),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    handling_note VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_appointment_vehicle (vehicle_id, status),
    INDEX idx_appointment_buyer (buyer_id, status),
    FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    FOREIGN KEY (buyer_id) REFERENCES app_user (id),
    FOREIGN KEY (handler_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trade_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    consignment_id BIGINT,
    request_key VARCHAR(64) NOT NULL,
    amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'LOCKED'
        CHECK (status IN ('LOCKED', 'PAID', 'COMPLETED', 'CANCELLED')),
    handling_note VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME,
    completed_at DATETIME,
    CHECK (buyer_id <> seller_id),
    CHECK (status NOT IN ('PAID', 'COMPLETED') OR paid_at IS NOT NULL),
    CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)),
    UNIQUE (buyer_id, request_key),
    UNIQUE (vehicle_id, id),
    UNIQUE (id, buyer_id),
    INDEX idx_trade_buyer (buyer_id, status),
    INDEX idx_trade_seller (seller_id, status),
    INDEX idx_trade_vehicle (vehicle_id, status),
    FOREIGN KEY (vehicle_id, seller_id) REFERENCES vehicle (id, owner_id),
    FOREIGN KEY (buyer_id) REFERENCES app_user (id),
    FOREIGN KEY (vehicle_id, consignment_id) REFERENCES consignment (vehicle_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vehicle_lock (
    vehicle_id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    locked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (vehicle_id, order_id) REFERENCES trade_order (vehicle_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS loan_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    applicant_id BIGINT NOT NULL,
    handler_id BIGINT,
    amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    months SMALLINT NOT NULL CHECK (months BETWEEN 1 AND 84),
    note VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUPPLEMENT', 'PROCESSING', 'FINISHED', 'CANCELLED')),
    result_note VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_loan_applicant (applicant_id, status),
    FOREIGN KEY (order_id, applicant_id) REFERENCES trade_order (id, buyer_id),
    FOREIGN KEY (handler_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS repair_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    trade_order_id BIGINT,
    applicant_id BIGINT NOT NULL,
    handler_id BIGINT,
    stage VARCHAR(12) NOT NULL,
    visit_at DATETIME NOT NULL,
    description VARCHAR(800) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'CANCELLED')),
    result_note VARCHAR(500),
    completed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((stage = 'PRE_SALE' AND trade_order_id IS NULL)
        OR (stage = 'AFTER_SALE' AND trade_order_id IS NOT NULL)),
    INDEX idx_repair_applicant (applicant_id, status),
    INDEX idx_repair_vehicle (vehicle_id, status),
    FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    FOREIGN KEY (vehicle_id, trade_order_id) REFERENCES trade_order (vehicle_id, id),
    FOREIGN KEY (trade_order_id, applicant_id) REFERENCES trade_order (id, buyer_id),
    FOREIGN KEY (applicant_id) REFERENCES app_user (id),
    FOREIGN KEY (handler_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiver_id BIGINT NOT NULL,
    vehicle_id BIGINT,
    event_key VARCHAR(120) NOT NULL,
    subject_type VARCHAR(24) NOT NULL,
    subject_id BIGINT NOT NULL,
    message VARCHAR(300) NOT NULL,
    read_at DATETIME,
    pushed_at DATETIME,
    push_attempts INT NOT NULL DEFAULT 0 CHECK (push_attempts >= 0),
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (receiver_id, event_key),
    INDEX idx_notification_inbox (receiver_id, read_at, id),
    INDEX idx_notification_delivery (pushed_at, next_attempt_at, id),
    FOREIGN KEY (receiver_id) REFERENCES app_user (id),
    FOREIGN KEY (vehicle_id) REFERENCES vehicle (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT,
    action VARCHAR(40) NOT NULL,
    subject_type VARCHAR(24) NOT NULL,
    subject_id BIGINT NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_subject (subject_type, subject_id, created_at),
    INDEX idx_audit_actor (actor_id, created_at),
    FOREIGN KEY (actor_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_role (code, name) VALUES ('USER', '普通用户')
ON DUPLICATE KEY UPDATE code = app_role.code;

INSERT INTO app_role (code, name) VALUES ('STAFF', '工作人员')
ON DUPLICATE KEY UPDATE code = app_role.code;

INSERT INTO app_role (code, name) VALUES ('ADMIN', '管理员')
ON DUPLICATE KEY UPDATE code = app_role.code;
