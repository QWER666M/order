-- ============================================================
--  重建 MySQL 开发数据库
--
--  用法（二选一）：
--    A. MySQL Workbench：打开本文件 → 用 root 连接 → 点 ⚡ 执行
--    B. 命令行：
--         "C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe" -u root -p < db/init-mysql.sql
--
--  执行后直接 mvn spring-boot:run 即可：
--  表结构由应用启动时的 schema.sql 自动创建（spring.sql.init.mode=always）。
--
--  ⚠️ 警告：第 2 步会【删除 orderdb 里的全部数据】。
--     只想补结构、不想丢数据，请跳过 DROP，只执行第 3 步，
--     并手动执行：ALTER TABLE orders ADD COLUMN owner_id VARCHAR(64) NULL;
-- ============================================================

-- 1. 应用专用账号（不要让应用用 root 连库 —— 最小权限原则）
--    统一先建账号，避免下面 GRANT 时账号不存在
CREATE USER IF NOT EXISTS 'order'@'localhost' IDENTIFIED BY 'order123';
CREATE USER IF NOT EXISTS 'order'@'%'         IDENTIFIED BY 'order123';

-- 如果账号已存在但密码不同，重置为 application.properties 里配置的密码
ALTER USER 'order'@'localhost' IDENTIFIED BY 'order123';
ALTER USER 'order'@'%'         IDENTIFIED BY 'order123';

-- 2. 重建库（会清空数据！）
DROP DATABASE IF EXISTS orderdb;
CREATE DATABASE orderdb
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 3. 授权：只给这个库的权限，不是全局权限
GRANT ALL PRIVILEGES ON orderdb.* TO 'order'@'localhost';
GRANT ALL PRIVILEGES ON orderdb.* TO 'order'@'%';
FLUSH PRIVILEGES;

-- 4. 确认结果
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'orderdb';

SELECT user, host FROM mysql.user WHERE user = 'order';
