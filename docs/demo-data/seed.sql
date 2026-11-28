-- 二手车交易管理系统 · 毕业设计模拟数据
-- 作者 pangzhihao。MySQL 8.0.16+，在独立演示库执行 schema.sql 后导入一次。
-- 数据均为构造示例。保留外键与 CHECK 校验，附件需运行同包的 install-uploads.ps1。
SET NAMES utf8mb4;
START TRANSACTION;

-- 按系统业务时区生成相对日期，使预约和有效委托在导入当天可用于演示。
SET @demo_now = UTC_TIMESTAMP() + INTERVAL 8 HOUR;
SET @demo_created = @demo_now - INTERVAL 14 DAY;
SET @demo_reviewed = @demo_now - INTERVAL 10 DAY;
SET @demo_ordered = @demo_now - INTERVAL 6 DAY;
SET @demo_paid = @demo_now - INTERVAL 5 DAY;
SET @demo_processed = @demo_now - INTERVAL 4 DAY;

-- 账号密码为本地演示专用口令，各账号使用独立的 BCrypt 盐值。
INSERT INTO app_user (id, username, password_hash, display_name, active, created_at) VALUES
    (900001, 'sample_seller', '$2b$10$lpFwudbq5qxdyo6Kl9aIh.ruIOP5e7zz/7Z4e8ZRiBpClw4AkNJFW', '演示卖家', TRUE, @demo_created),
    (900002, 'sample_buyer', '$2b$10$vUUf6pQKrGT1.p6KCAs53uQ7U8Lcb6ND3M9OY1jGsD4M8kJSsbWdy', '演示买家', TRUE, @demo_created),
    (900003, 'sample_staff', '$2b$10$UkmnBbWwawbfcRyaOrMqaOheJ44AkxKVLCckoKzJp8Hr4Bnhc/9pi', '演示工作人员', TRUE, @demo_created),
    (900004, 'sample_admin', '$2b$10$Zb.57/jQTwmR855gPwohJOUiZ9/IIy2kIOjbvxTOJTInaIYRLnwg.', '演示管理员', TRUE, @demo_created);

INSERT INTO user_role (user_id, role_code) VALUES
    (900001, 'USER'), (900002, 'USER'), (900003, 'STAFF'), (900004, 'ADMIN');

-- 按名称复用已有字典，保持业务外键与实际字典编号一致。
INSERT INTO brand (name, active) VALUES
    ('丰田', TRUE), ('本田', TRUE), ('比亚迪', TRUE), ('大众', TRUE), ('特斯拉', TRUE),
    ('马自达', TRUE), ('日产', TRUE), ('吉利', TRUE), ('五菱', TRUE), ('沃尔沃', TRUE)
ON DUPLICATE KEY UPDATE name = brand.name;

INSERT INTO city (name, active) VALUES
    ('杭州', TRUE), ('南京', TRUE), ('上海', TRUE), ('苏州', TRUE), ('宁波', TRUE)
ON DUPLICATE KEY UPDATE name = city.name;

-- 7 辆公开在售，其余示例用于审核、待支付、待过户、售后及下架演示。
INSERT INTO vehicle (id, owner_id, brand_id, city_id, model, registration_year, mileage, price,
    transmission, fuel, sale_mode, description, status, review_note, reviewer_id, reviewed_at, listed_at, created_at)
SELECT data.id, 900001, b.id, c.id, data.model, data.year_made, data.mileage, data.price,
       data.transmission, data.fuel, data.sale_mode, data.description, data.status,
       IF(data.status = 'PENDING', NULL, '已核对演示车源资料'),
       IF(data.status = 'PENDING', NULL, 900003),
       IF(data.status = 'PENDING', NULL, @demo_reviewed),
       IF(data.status IN ('PENDING','OFFLINE'), NULL, @demo_reviewed + INTERVAL (data.id - 910000) HOUR),
       @demo_created
FROM (
    SELECT 910001 AS id, '丰田' AS brand, '杭州' AS city, '卡罗拉 1.2T' AS model, 2021 AS year_made,
           42800 AS mileage, 63800.00 AS price, 'AUTO' AS transmission, 'PETROL' AS fuel, 'DIRECT' AS sale_mode,
           '白色家用轿车，右后车门有轻微划痕。可预约到店查看。' AS description, 'LISTED' AS status
    UNION ALL SELECT 910002, '本田', '苏州', '思域 240TURBO', 2022, 35600, 89800.00, 'AUTO', 'PETROL', 'DIRECT', '灰色紧凑型轿车，日常通勤为主。原交易已取消，当前接受预约。', 'LISTED'
    UNION ALL SELECT 910003, '比亚迪', '南京', '秦 PLUS DM-i', 2023, 26300, 72800.00, 'AUTO', 'HYBRID', 'CONSIGN', '深蓝色插电混动车，由平台代销。可预约体验市区工况。', 'LISTED'
    UNION ALL SELECT 910004, '大众', '上海', '朗逸 1.5L', 2020, 57600, 49800.00, 'MANUAL', 'PETROL', 'DIRECT', '银色家用轿车，手动挡。已有待支付订单。', 'LOCKED'
    UNION ALL SELECT 910005, '特斯拉', '杭州', 'Model 3 后轮驱动版', 2023, 31200, 129800.00, 'AUTO', 'ELECTRIC', 'DIRECT', '白色电动轿车。买家已模拟支付，工作人员可办理过户。', 'LOCKED'
    UNION ALL SELECT 910006, '马自达', '宁波', 'CX-5 2.0L', 2021, 48800, 83800.00, 'AUTO', 'PETROL', 'CONSIGN', '红色紧凑型 SUV，已提交车源和代销委托以待审核。', 'PENDING'
    UNION ALL SELECT 910007, '丰田', '南京', 'RAV4 荣放 2.0L', 2022, 44100, 118800.00, 'AUTO', 'PETROL', 'CONSIGN', '浅棕色 SUV，代销交易已确认过户。有关资料供交易用户查阅。', 'SOLD'
    UNION ALL SELECT 910008, '本田', '苏州', '雅阁 260TURBO', 2020, 69800, 86800.00, 'AUTO', 'PETROL', 'CONSIGN', '黑色中型轿车，代销委托到期后已下架。', 'OFFLINE'
    UNION ALL SELECT 910009, '日产', '宁波', '轩逸 1.6L', 2022, 38900, 65800.00, 'AUTO', 'PETROL', 'DIRECT', '灰色四门轿车，后备厢平整，适合日常通勤。', 'LISTED'
    UNION ALL SELECT 910010, '吉利', '杭州', '星瑞 2.0TD', 2021, 47300, 76800.00, 'AUTO', 'PETROL', 'DIRECT', '蓝色家用轿车，具有倒车影像，可到店查验。', 'LISTED'
    UNION ALL SELECT 910011, '五菱', '上海', '宏光 MINIEV', 2023, 16900, 23800.00, 'AUTO', 'ELECTRIC', 'DIRECT', '白色微型电动车，适合短途代步。充电附件随车。', 'LISTED'
    UNION ALL SELECT 910012, '沃尔沃', '苏州', 'XC60 B5', 2022, 51700, 169800.00, 'AUTO', 'PETROL', 'DIRECT', '深绿色中型 SUV，有后备厢垫，支持预约看车。', 'LISTED'
) AS data
JOIN brand b ON b.name = data.brand
JOIN city c ON c.name = data.city
ORDER BY data.id;

-- 分别展示有效、待审、完成和到期委托。成交订单绑定下单时的委托。
INSERT INTO consignment (id, vehicle_id, owner_id, starts_at, expires_at, fee, terms, status,
    reviewer_id, review_note, reviewed_at, created_at) VALUES
    (920003, 910003, 900001, @demo_reviewed, @demo_now + INTERVAL 90 DAY, 1000.00, '平台负责接待及安排看车。', 'APPROVED', 900003, '委托材料已核对。', @demo_reviewed, @demo_created),
    (920006, 910006, 900001, @demo_now - INTERVAL 1 DAY, @demo_now + INTERVAL 45 DAY, 1380.00, '车辆留在卖家处，预约后到店。', 'PENDING', NULL, NULL, NULL, @demo_created),
    (920007, 910007, 900001, @demo_reviewed, @demo_now + INTERVAL 30 DAY, 1600.00, '办理完过户后结案。', 'COMPLETED', 900003, '委托已核对，订单成交后结案。', @demo_reviewed, @demo_created),
    (920008, 910008, 900001, @demo_reviewed, @demo_now - INTERVAL 2 DAY, 1180.00, '到期后结束委托。', 'EXPIRED', 900003, '历史委托已到期。', @demo_reviewed, @demo_created);

-- 详细报告附件只关联已审核的两个车源。另保留一项待审报告。
INSERT INTO vehicle_report (id, vehicle_id, author_id, source, report_date, summary, status,
    review_note, reviewer_id, reviewed_at, created_at) VALUES
    (921001, 910001, 900001, '教学演示检测', DATE(@demo_created), '仪表及灯光正常，右后车门轻微划痕。详细记录在附件中。', 'APPROVED', '已核对示例报告。', 900003, @demo_reviewed, @demo_created),
    (921003, 910003, 900001, '教学演示检测', DATE(@demo_created), '内饰轻微使用痕迹，充电口盖闭合顺畅。电池健康度暂未测量。', 'APPROVED', '已核对示例报告。', 900003, @demo_reviewed, @demo_created),
    (921006, 910006, 900001, '卖家演示自检', DATE(@demo_created), '外观与车灯自检情况已登记，等待工作人员审核。', 'PENDING', NULL, NULL, NULL, @demo_created);

-- 附件字节数对应 assets/ 中的真实文件。安装脚本使用相同规则生成磁盘文件名。
INSERT INTO attachment (id, vehicle_id, report_id, uploader_id, kind, storage_key, name, mime, bytes, created_at)
SELECT data.id, data.vehicle_id, data.report_id, 900001, data.kind,
       CONCAT('64656d6f736565640000', LPAD(data.id, 12, '0')),
       data.name, data.mime, data.bytes, @demo_created
FROM (
    SELECT 930001 AS id, 910001 AS vehicle_id, CAST(NULL AS SIGNED) AS report_id, 'PHOTO' AS kind,
           '卡罗拉-封面.jpg' AS name, 'image/jpeg' AS mime, 110410 AS bytes
    UNION ALL SELECT 930002, 910002, NULL, 'PHOTO', '思域-封面.jpg', 'image/jpeg', 118492
    UNION ALL SELECT 930003, 910003, NULL, 'PHOTO', '秦PLUS-封面.jpg', 'image/jpeg', 116738
    UNION ALL SELECT 930004, 910004, NULL, 'PHOTO', '朗逸-封面.jpg', 'image/jpeg', 101065
    UNION ALL SELECT 930005, 910005, NULL, 'PHOTO', 'Model3-封面.jpg', 'image/jpeg', 110406
    UNION ALL SELECT 930006, 910006, NULL, 'PHOTO', 'CX-5-封面.jpg', 'image/jpeg', 129488
    UNION ALL SELECT 930007, 910007, NULL, 'PHOTO', 'RAV4荣放-封面.jpg', 'image/jpeg', 130496
    UNION ALL SELECT 930008, 910008, NULL, 'PHOTO', '雅阁-封面.jpg', 'image/jpeg', 129251
    UNION ALL SELECT 930009, 910009, NULL, 'PHOTO', '轩逸-封面.jpg', 'image/jpeg', 106118
    UNION ALL SELECT 930010, 910010, NULL, 'PHOTO', '星瑞-封面.jpg', 'image/jpeg', 106040
    UNION ALL SELECT 930011, 910011, NULL, 'PHOTO', '宏光MINIEV-封面.jpg', 'image/jpeg', 104580
    UNION ALL SELECT 930012, 910012, NULL, 'PHOTO', 'XC60-封面.jpg', 'image/jpeg', 141815
    UNION ALL SELECT 931001, 910001, 921001, 'REPORT', '卡罗拉-车况检测报告.pdf', 'application/pdf', 88277
    UNION ALL SELECT 931003, 910003, 921003, 'REPORT', '秦PLUS-车况检测报告.pdf', 'application/pdf', 96673
) AS data;

-- 两笔未来预约和一笔历史完成记录，可验证个人列表与经办队列。
INSERT INTO appointment (id, vehicle_id, buyer_id, handler_id, visit_at, note, status, handling_note, created_at) VALUES
    (940001, 910001, 900002, NULL, TIMESTAMP(DATE(@demo_now) + INTERVAL 3 DAY, '10:00:00'), '想看实车并了解近期养护情况。', 'PENDING', NULL, @demo_created),
    (940003, 910003, 900002, 900003, TIMESTAMP(DATE(@demo_now) + INTERVAL 5 DAY, '14:30:00'), '希望查看随车充电附件。', 'CONFIRMED', '已安排演示门店接待。', @demo_created),
    (940007, 910007, 900002, 900003, TIMESTAMP(DATE(@demo_now) - INTERVAL 8 DAY, '15:00:00'), '预约查看车辆空间。', 'COMPLETED', '买家到店并完成看车。', @demo_created);

-- 模拟支付后取消保留 paid_at。只有待支付及待过户订单保留车辆锁。
INSERT INTO trade_order (id, vehicle_id, buyer_id, seller_id, consignment_id, request_key,
    amount, status, handling_note, created_at, paid_at, completed_at) VALUES
    (950002, 910002, 900002, 900001, NULL, 'sample-order-civic', 89800.00, 'CANCELLED', '双方确认本次无法办理过户。', @demo_ordered, @demo_paid, NULL),
    (950004, 910004, 900002, 900001, NULL, 'sample-order-lavida', 49800.00, 'LOCKED', NULL, @demo_now - INTERVAL 1 DAY, NULL, NULL),
    (950005, 910005, 900002, 900001, NULL, 'sample-order-model3', 129800.00, 'PAID', NULL, @demo_ordered, @demo_paid, NULL),
    (950007, 910007, 900002, 900001, 920007, 'sample-order-rav4', 118800.00, 'COMPLETED', '过户材料核对完毕，已登记成交。', @demo_ordered, @demo_paid, @demo_processed);

INSERT INTO vehicle_lock (vehicle_id, order_id, locked_at) VALUES
    (910004, 950004, @demo_now - INTERVAL 1 DAY), (910005, 950005, @demo_ordered);

-- 每笔订单最多一份贷款登记，取消订单对应的贷款已关闭。
INSERT INTO loan_application (id, order_id, applicant_id, handler_id, amount, months, note, status, result_note, created_at) VALUES
    (960002, 950002, 900002, 900003, 40000.00, 24, '计划分两年偿还。', 'CANCELLED', '关联订单已取消，贷款登记已关闭。', @demo_ordered),
    (960004, 950004, 900002, NULL, 25000.00, 24, '请登记分期咨询。', 'PENDING', NULL, @demo_now - INTERVAL 1 DAY),
    (960005, 950005, 900002, 900003, 65000.00, 36, '希望了解每月还款估算。', 'PROCESSING', '经办人员已核对演示登记信息。', @demo_ordered),
    (960007, 950007, 900002, 900003, 60000.00, 36, '咨询代销车分期。', 'FINISHED', '演示贷款登记已办结。', @demo_ordered);

-- 售前由车主提出，售后由已成交订单的买家提出。
INSERT INTO repair_order (id, vehicle_id, trade_order_id, applicant_id, handler_id, stage,
    visit_at, description, status, result_note, completed_at, created_at) VALUES
    (970001, 910001, NULL, 900001, 900003, 'PRE_SALE', TIMESTAMP(DATE(@demo_now) - INTERVAL 3 DAY, '09:00:00'), '售前检查胎压并清洁车内。', 'COMPLETED', '胎压已核对，内饰已清洁。', @demo_now - INTERVAL 2 DAY, @demo_ordered),
    (970006, 910006, NULL, 900001, NULL, 'PRE_SALE', TIMESTAMP(DATE(@demo_now) + INTERVAL 4 DAY, '13:30:00'), '检查雨刮和玻璃水。', 'PENDING', NULL, NULL, @demo_now - INTERVAL 1 DAY),
    (970007, 910007, 950007, 900002, 900003, 'AFTER_SALE', TIMESTAMP(DATE(@demo_now) + INTERVAL 2 DAY, '14:00:00'), '低速行驶时右后方偶尔有异响。', 'PROCESSING', '已受理，等待到店检查。', NULL, @demo_now - INTERVAL 1 DAY);

-- 收件箱预置已读和未读记录。已存在的历史通知不进入重新推送队列。
INSERT INTO notification (id, receiver_id, vehicle_id, event_key, subject_type, subject_id, message,
    read_at, pushed_at, push_attempts, next_attempt_at, created_at) VALUES
    (980001, 900002, 910002, 'order:950002:cancelled', 'ORDER', 950002, '工作人员已取消本笔订单，请在订单详情查看原因。', NULL, @demo_processed, 1, @demo_processed, @demo_processed),
    (980002, 900001, 910002, 'order:950002:cancelled', 'ORDER', 950002, '工作人员已取消本笔订单，请在订单详情查看原因。', NULL, @demo_processed, 1, @demo_processed, @demo_processed),
    (980003, 900002, 910005, 'order:950005:paid', 'ORDER', 950005, '模拟支付成功，等待工作人员确认过户。', NULL, @demo_paid, 1, @demo_paid, @demo_paid),
    (980004, 900001, 910005, 'order:950005:paid', 'ORDER', 950005, '买家已完成模拟支付，等待过户确认。', NULL, @demo_paid, 1, @demo_paid, @demo_paid),
    (980005, 900002, 910007, 'order:950007:completed', 'ORDER', 950007, '工作人员已确认过户，交易已完成。', @demo_processed + INTERVAL 1 HOUR, @demo_processed, 1, @demo_processed, @demo_processed),
    (980006, 900001, 910007, 'order:950007:completed', 'ORDER', 950007, '工作人员已确认过户，交易已完成。', NULL, @demo_processed, 1, @demo_processed, @demo_processed),
    (980007, 900001, 910004, 'order:950004:created', 'ORDER', 950004, '你的车源已被下单，等待买家模拟支付。', NULL, @demo_now - INTERVAL 1 DAY, 1, @demo_now - INTERVAL 1 DAY, @demo_now - INTERVAL 1 DAY);

-- 审计保留工作人员操作人和对应业务编号，支持按订单或车源筛选演示。
INSERT INTO audit_log (id, actor_id, action, subject_type, subject_id, detail, created_at) VALUES
    (990001, 900003, 'REVIEW_VEHICLE', 'VEHICLE', 910001, '车源审核通过。', @demo_reviewed),
    (990002, 900003, 'CANCEL_PAID_ORDER', 'ORDER', 950002, '工作人员取消已模拟支付订单', @demo_processed),
    (990003, 900002, 'PAY_ORDER', 'ORDER', 950005, '买家完成模拟支付', @demo_paid),
    (990004, 900003, 'COMPLETE_ORDER', 'ORDER', 950007, '工作人员确认过户成交', @demo_processed),
    (990005, 900004, 'CHANGE_USER_ROLES', 'USER', 900003, '演示工作人员角色已配置。', @demo_created);

COMMIT;
