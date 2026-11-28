-- 二手车交易管理系统 · 批量测试数据
-- 作者 pangzhihao。适用于 MySQL 8.0.16+，在已导入 seed.sql 的演示库中运行一次。
-- 使用同一批车源编号推导业务单据，保持车源、委托、订单、车锁和附件一致。
SET NAMES utf8mb4;
START TRANSACTION;

-- 临时校验表只在本连接有效；缺少基础数据或重复导入时让 MySQL 报错。
CREATE TEMPORARY TABLE bulk_seed_guard (
    base_ready TINYINT NOT NULL CONSTRAINT bulk_base_seed_required CHECK (base_ready = 1),
    first_import TINYINT NOT NULL CONSTRAINT bulk_already_imported CHECK (first_import = 1)
);
INSERT INTO bulk_seed_guard
SELECT (SELECT COUNT(*) FROM app_user WHERE id BETWEEN 900001 AND 900004) = 4
       AND (SELECT COUNT(*) FROM vehicle WHERE id BETWEEN 910001 AND 910012) = 12
       AND (SELECT COUNT(*) FROM attachment WHERE id BETWEEN 930001 AND 930012 AND kind = 'PHOTO') = 12
       AND (SELECT COUNT(*) FROM city WHERE name IN ('杭州','南京','上海','苏州','宁波')) = 5,
       NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'bulk_seller_01');

-- 使用导入时的北京时间，近期预约与委托可直接进入业务流程。
SET @bulk_now = UTC_TIMESTAMP() + INTERVAL 8 HOUR;
SET @bulk_history = @bulk_now - INTERVAL 18 DAY;

-- 四个数字位置构造连续编号，取前 2,000 行。
CREATE TEMPORARY TABLE bulk_seed_numbers (n INT PRIMARY KEY);
INSERT INTO bulk_seed_numbers (n)
WITH digits AS (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
    UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
),
numbered AS (
    SELECT 1 + ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 AS n
    FROM digits ones CROSS JOIN digits tens CROSS JOIN digits hundreds CROSS JOIN digits thousands
)
SELECT n FROM numbered WHERE n <= 2000;

-- 新增 20 名卖家、60 名买家。配套账号沿用基础样例的本地演示密码。
INSERT INTO app_user (id, username, password_hash, display_name, active, created_at)
SELECT 2000000 + seq.n, CONCAT('bulk_seller_', LPAD(seq.n, 2, '0')),
       sample.password_hash, CONCAT('测试卖家 ', LPAD(seq.n, 2, '0')), TRUE, @bulk_now - INTERVAL 180 DAY
FROM bulk_seed_numbers seq JOIN app_user sample ON sample.id = 900001 WHERE seq.n <= 20;

INSERT INTO app_user (id, username, password_hash, display_name, active, created_at)
SELECT 2000100 + seq.n, CONCAT('bulk_buyer_', LPAD(seq.n, 2, '0')),
       sample.password_hash, CONCAT('测试买家 ', LPAD(seq.n, 2, '0')), TRUE, @bulk_now - INTERVAL 180 DAY
FROM bulk_seed_numbers seq JOIN app_user sample ON sample.id = 900002 WHERE seq.n <= 60;

INSERT INTO user_role (user_id, role_code)
SELECT id, 'USER' FROM app_user
WHERE id BETWEEN 2000001 AND 2000020 OR id BETWEEN 2000101 AND 2000160;

-- 每 20 辆重复一组状态分布，另外轮换车型、城市、价格、年份和里程。
-- 通过每个序号保存可复用的车源属性与时间，后续各表引用同一份数据。
CREATE TEMPORARY TABLE bulk_seed_vehicles (
    seq INT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    scenario INT NOT NULL,
    owner_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    city_id BIGINT NOT NULL,
    model VARCHAR(80) NOT NULL,
    registration_year SMALLINT NOT NULL,
    mileage INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    transmission VARCHAR(12) NOT NULL,
    fuel VARCHAR(12) NOT NULL,
    sale_mode VARCHAR(12) NOT NULL,
    vehicle_status VARCHAR(16) NOT NULL,
    order_status VARCHAR(16),
    created_at DATETIME NOT NULL,
    ordered_at DATETIME NOT NULL,
    source_photo_id BIGINT NOT NULL
) DEFAULT CHARSET=utf8mb4;

INSERT INTO bulk_seed_vehicles
SELECT seq.n, 2100000 + seq.n, seq.scenario,
       IF(MOD(seq.n, 7) = 0, 900001, 2000001 + MOD(seq.n - 1, 20)),
       IF(MOD(seq.n, 7) = 0, 900002, 2000101 + MOD(seq.n + FLOOR(seq.n / 20) * 7, 60)),
       sample.brand_id, c.id, CONCAT(LEFT(sample.model, 66), ' · 测试', LPAD(seq.n, 4, '0')),
       GREATEST(2017, sample.registration_year - MOD(seq.n * 47, 4)),
       sample.mileage + MOD(seq.n * 397, 74000) - 11000,
       ROUND(sample.price * (90 + MOD(seq.n * 17, 36)) / 100, -2),
       sample.transmission, sample.fuel,
       IF(seq.scenario IN (10, 12) OR MOD(FLOOR((seq.n - 1) / 20), 4) = 0, 'CONSIGN', 'DIRECT'),
       CASE
           WHEN seq.scenario = 10 THEN 'PENDING'
           WHEN seq.scenario = 11 THEN 'REJECTED'
           WHEN seq.scenario = 12 THEN 'OFFLINE'
           WHEN seq.scenario BETWEEN 13 AND 16 THEN 'LOCKED'
           WHEN seq.scenario IN (17, 18) THEN 'SOLD'
           ELSE 'LISTED'
       END,
       CASE
           WHEN seq.scenario IN (13, 14) THEN 'LOCKED'
           WHEN seq.scenario IN (15, 16) THEN 'PAID'
           WHEN seq.scenario IN (17, 18) THEN 'COMPLETED'
           WHEN seq.scenario = 19 THEN 'CANCELLED'
       END,
       TIMESTAMPADD(DAY, -90 - MOD(seq.n * 11, 60), @bulk_now),
       TIMESTAMPADD(MINUTE, -seq.n, @bulk_history),
       930001 + MOD(seq.n - 1, 12)
FROM (SELECT n, MOD(n - 1, 20) AS scenario FROM bulk_seed_numbers) AS seq
JOIN vehicle sample ON sample.id = 910001 + MOD(seq.n - 1, 12)
JOIN city c ON c.name = ELT(1 + MOD(seq.n + FLOOR(seq.n / 12), 5), '杭州','南京','上海','苏州','宁波');

INSERT INTO vehicle (id, owner_id, brand_id, city_id, model, registration_year, mileage, price,
    transmission, fuel, sale_mode, description, status, review_note, reviewer_id, reviewed_at, listed_at, created_at)
SELECT vehicle_id, owner_id, brand_id, city_id, model, registration_year, mileage, price,
       transmission, fuel, sale_mode,
       ELT(1 + MOD(seq, 4), '家用通勤车，支持预约查看。', '可到店核对随车资料与养护记录。',
           '内饰有正常使用痕迹，欢迎预约试乘。', '已登记基础车况及使用里程。'),
       vehicle_status,
       CASE vehicle_status WHEN 'PENDING' THEN NULL WHEN 'REJECTED' THEN '请补充保养记录后重新提交。'
           ELSE '已核对模拟车源资料。' END,
       IF(vehicle_status = 'PENDING', NULL, 900003),
       IF(vehicle_status = 'PENDING', NULL, created_at + INTERVAL 2 DAY),
       IF(vehicle_status IN ('PENDING','REJECTED','OFFLINE'), NULL, created_at + INTERVAL 3 DAY), created_at
FROM bulk_seed_vehicles ORDER BY seq;

-- 代销成交结案，过期委托对应下架车源。其余已审核在办代销仍在有效期内。
INSERT INTO consignment (id, vehicle_id, owner_id, starts_at, expires_at, fee, terms, status,
    reviewer_id, review_note, reviewed_at, created_at)
SELECT vehicle_id, vehicle_id, owner_id, created_at + INTERVAL 1 DAY,
       IF(scenario = 12, @bulk_now - INTERVAL 3 DAY, @bulk_now + INTERVAL 90 DAY),
       ROUND(price * 0.015, 2), '测试委托，平台负责预约接待。',
       CASE vehicle_status WHEN 'PENDING' THEN 'PENDING' WHEN 'REJECTED' THEN 'REJECTED'
           WHEN 'OFFLINE' THEN 'EXPIRED' WHEN 'SOLD' THEN 'COMPLETED' ELSE 'APPROVED' END,
       IF(vehicle_status = 'PENDING', NULL, 900003), IF(vehicle_status = 'PENDING', NULL, '已登记委托处理结果。'),
       IF(vehicle_status = 'PENDING', NULL, created_at + INTERVAL 2 DAY), created_at
FROM bulk_seed_vehicles WHERE sale_mode = 'CONSIGN';

-- 车况摘要覆盖待审、已通过与退回。基础包内的两份 PDF 仍可从原车源下载。
INSERT INTO vehicle_report (id, vehicle_id, author_id, source, report_date, summary, status,
    review_note, reviewer_id, reviewed_at, created_at)
SELECT vehicle_id, vehicle_id, owner_id, '批量演示自检', DATE(created_at),
       ELT(1 + MOD(seq, 3), '灯组点亮正常，内饰有轻微磨损。', '轮胎纹路清楚，随车工具已登记。',
           '空调及车机可启动，漆面状态待实车核对。'),
       CASE vehicle_status WHEN 'PENDING' THEN 'PENDING' WHEN 'REJECTED' THEN 'REJECTED' ELSE 'APPROVED' END,
       CASE vehicle_status WHEN 'PENDING' THEN NULL WHEN 'REJECTED' THEN '请完善报告来源和检查结果。'
           ELSE '已核对模拟检查结果。' END,
       IF(vehicle_status = 'PENDING', NULL, 900003),
       IF(vehicle_status = 'PENDING', NULL, created_at + INTERVAL 2 DAY), created_at
FROM bulk_seed_vehicles WHERE scenario IN (0, 2, 4, 6, 8, 10, 11, 15, 17, 18);

-- 按车型轮换基础包的 12 张图片。安装脚本从原图复制出每辆车对应的独立存储键。
INSERT INTO attachment (id, vehicle_id, report_id, uploader_id, kind, storage_key, name, mime, bytes, created_at)
SELECT b.vehicle_id, b.vehicle_id, NULL, b.owner_id, 'PHOTO',
       CONCAT('62756c6b746573740000', LPAD(b.vehicle_id, 12, '0')),
       CONCAT('批量测试-', LPAD(b.seq, 4, '0'), '.jpg'), photo.mime, photo.bytes, b.created_at
FROM bulk_seed_vehicles b JOIN attachment photo ON photo.id = b.source_photo_id;

-- 待确认和已确认预约使用未来日期，历史预约覆盖拒绝、取消和完成。
INSERT INTO appointment (id, vehicle_id, buyer_id, handler_id, visit_at, note, status, handling_note, created_at)
SELECT vehicle_id, vehicle_id, buyer_id, IF(scenario IN (0, 1, 6, 7), NULL, 900003),
       CASE WHEN scenario < 4
           THEN TIMESTAMP(DATE(@bulk_now) + INTERVAL (1 + MOD(seq, 18)) DAY, '10:30:00')
           ELSE TIMESTAMP(DATE(@bulk_history) + INTERVAL MOD(seq, 8) DAY, '14:30:00') END,
       '想了解使用里程与看车安排。',
       CASE WHEN scenario < 2 THEN 'PENDING' WHEN scenario < 4 THEN 'CONFIRMED'
            WHEN scenario < 6 THEN 'REJECTED' WHEN scenario < 8 THEN 'CANCELLED' ELSE 'COMPLETED' END,
       CASE WHEN scenario IN (2, 3) THEN '已安排到店。' WHEN scenario IN (4, 5) THEN '请改约其他时间。'
            WHEN scenario IN (8, 9) THEN '已完成看车。' END,
       @bulk_history - INTERVAL 15 DAY
FROM bulk_seed_vehicles WHERE scenario < 10;

-- 在办订单均对应其车辆锁。已成交、已取消直接保留历史而不占用车源。
INSERT INTO trade_order (id, vehicle_id, buyer_id, seller_id, consignment_id, request_key,
    amount, status, handling_note, created_at, paid_at, completed_at)
SELECT vehicle_id, vehicle_id, buyer_id, owner_id, IF(sale_mode = 'CONSIGN', vehicle_id, NULL),
       CONCAT('bulk-order-', seq), price, order_status,
       CASE order_status WHEN 'COMPLETED' THEN '演示过户手续已完成。'
            WHEN 'CANCELLED' THEN '买卖双方确认本次暂缓过户。' END,
       ordered_at, IF(order_status = 'LOCKED', NULL, ordered_at + INTERVAL 1 DAY),
       IF(order_status = 'COMPLETED', ordered_at + INTERVAL 4 DAY, NULL)
FROM bulk_seed_vehicles WHERE order_status IS NOT NULL;

INSERT INTO vehicle_lock (vehicle_id, order_id, locked_at)
SELECT vehicle_id, vehicle_id, ordered_at
FROM bulk_seed_vehicles WHERE order_status IN ('LOCKED','PAID');

-- 贷款金额小于订单金额，状态和取消、成交记录对应。
INSERT INTO loan_application (id, order_id, applicant_id, handler_id, amount, months, note, status, result_note, created_at)
SELECT vehicle_id, vehicle_id, buyer_id, IF(scenario = 13, NULL, 900003),
       ROUND(price * (30 + MOD(seq, 30)) / 100, 2), ELT(1 + MOD(seq, 4), 12, 24, 36, 48),
       '希望了解分期登记流程。',
       CASE scenario WHEN 13 THEN 'PENDING' WHEN 14 THEN 'SUPPLEMENT'
            WHEN 19 THEN 'CANCELLED' ELSE IF(order_status = 'COMPLETED', 'FINISHED', 'PROCESSING') END,
       CASE scenario WHEN 13 THEN NULL WHEN 14 THEN '请补充收入材料。'
            WHEN 19 THEN '订单已取消，贷款登记关闭。' ELSE '已核对演示贷款材料。' END, ordered_at
FROM bulk_seed_vehicles WHERE order_status IS NOT NULL;

-- 售后工单只给成交买家，售前工单使用当前车源的所有人。
INSERT INTO repair_order (id, vehicle_id, trade_order_id, applicant_id, handler_id, stage,
    visit_at, description, status, result_note, completed_at, created_at)
SELECT vehicle_id, vehicle_id, IF(scenario IN (17, 18), vehicle_id, NULL),
       IF(scenario IN (17, 18), buyer_id, owner_id), IF(scenario IN (0, 17), NULL, 900003),
       IF(scenario IN (17, 18), 'AFTER_SALE', 'PRE_SALE'),
       IF(scenario IN (4, 10), @bulk_history + INTERVAL 8 DAY,
           TIMESTAMP(DATE(@bulk_now) + INTERVAL (2 + MOD(seq, 11)) DAY, '14:00:00')),
       IF(scenario IN (17, 18), '希望检查低速转弯时的异响。', '检查胎压、灯光及雨刮。'),
       CASE scenario WHEN 4 THEN 'COMPLETED' WHEN 10 THEN 'CANCELLED'
           WHEN 2 THEN 'PROCESSING' WHEN 18 THEN 'PROCESSING' ELSE 'PENDING' END,
       CASE scenario WHEN 4 THEN '已完成售前检查。' WHEN 10 THEN '本次不安排整备。'
           WHEN 2 THEN '已受理，等待车辆到场。' WHEN 18 THEN '已受理，等待送修。' END,
       IF(scenario = 4, @bulk_history + INTERVAL 8 DAY + INTERVAL 2 HOUR, NULL),
       @bulk_history + INTERVAL 6 DAY
FROM bulk_seed_vehicles WHERE scenario IN (0, 2, 4, 10, 17, 18);

-- 预置订单事件通知，留出已读与未读状态。历史消息无需重放 WebSocket 推送。
INSERT INTO notification (id, receiver_id, vehicle_id, event_key, subject_type, subject_id, message,
    read_at, pushed_at, push_attempts, next_attempt_at, created_at)
SELECT 2300000 + b.seq * 2 + recipient.side, IF(recipient.side = 0, b.owner_id, b.buyer_id), b.vehicle_id,
       CONCAT('order:', b.vehicle_id, ':', CASE b.order_status WHEN 'LOCKED' THEN 'created' WHEN 'PAID' THEN 'paid'
           WHEN 'COMPLETED' THEN 'completed' ELSE 'cancelled' END), 'ORDER', b.vehicle_id,
       CASE b.order_status WHEN 'LOCKED' THEN '你的车源已有订单，等待模拟支付。'
           WHEN 'PAID' THEN '订单已模拟支付，等待工作人员确认过户。'
           WHEN 'COMPLETED' THEN '工作人员已确认过户，交易已完成。'
           ELSE '工作人员已取消本笔订单，请在详情查看原因。' END,
       IF(MOD(b.seq, 3) = 0, b.ordered_at + INTERVAL 5 DAY, NULL),
       b.ordered_at + INTERVAL 4 DAY, 1, b.ordered_at + INTERVAL 4 DAY, b.ordered_at + INTERVAL 4 DAY
FROM bulk_seed_vehicles b CROSS JOIN (SELECT 0 AS side UNION ALL SELECT 1) recipient
WHERE b.order_status IS NOT NULL AND (recipient.side = 0 OR b.order_status <> 'LOCKED');

-- 提交日志同时作为车源审核版本。再为各订单保留当前阶段的经办审计。
INSERT INTO audit_log (id, actor_id, action, subject_type, subject_id, detail, created_at)
SELECT vehicle_id, owner_id, 'SUBMIT_VEHICLE', 'VEHICLE', vehicle_id, '提交批量演示车源。', created_at
FROM bulk_seed_vehicles;

INSERT INTO audit_log (id, actor_id, action, subject_type, subject_id, detail, created_at)
SELECT 2200000 + seq, IF(order_status IN ('COMPLETED','CANCELLED'), 900003, buyer_id),
       CASE order_status WHEN 'LOCKED' THEN 'CREATE_ORDER' WHEN 'PAID' THEN 'PAY_ORDER'
           WHEN 'COMPLETED' THEN 'COMPLETE_ORDER' ELSE 'CANCEL_PAID_ORDER' END,
       'ORDER', vehicle_id, '批量演示订单经办记录。',
       CASE order_status WHEN 'LOCKED' THEN ordered_at WHEN 'PAID' THEN ordered_at + INTERVAL 1 DAY
           ELSE ordered_at + INTERVAL 4 DAY END
FROM bulk_seed_vehicles WHERE order_status IS NOT NULL;

COMMIT;
DROP TEMPORARY TABLE bulk_seed_vehicles, bulk_seed_numbers, bulk_seed_guard;
