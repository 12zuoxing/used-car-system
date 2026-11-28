-- 作者 pangzhihao。可选字典数据，在 schema.sql 之后显式导入。
-- 演示账号和业务单据通过 tests/demo.mjs 创建，复用服务端认证和表单校验。
INSERT INTO brand (name, active) VALUES ('丰田', TRUE), ('大众', TRUE), ('本田', TRUE)
ON DUPLICATE KEY UPDATE name = brand.name;
-- 保留既有条目的启停状态。
INSERT INTO city (name, active) VALUES ('杭州', TRUE), ('南京', TRUE), ('上海', TRUE)
ON DUPLICATE KEY UPDATE name = city.name;
