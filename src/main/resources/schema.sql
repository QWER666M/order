-- 订单表。服务每次启动都会执行这个脚本，用 IF NOT EXISTS 保证可重复执行
CREATE TABLE IF NOT EXISTS orders (
    seq    BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 自增序号：用来保持"插入顺序"，等价于原来 LinkedHashMap 的作用
    id     VARCHAR(64) NOT NULL UNIQUE,        -- 订单号：唯一约束，数据库层面再兜一层底
    amount DECIMAL(12,2) NOT NULL,             -- 金额：DECIMAL(总位数, 小数位数)，精确十进制，绝不用 FLOAT/DOUBLE
    status VARCHAR(16) NOT NULL                -- 状态：存枚举的 name()，如 UNPAID / PAID / CANCELLED
);
