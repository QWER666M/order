-- ============================================================
-- 用户表
--   password_hash 存的是【BCrypt 哈希】，不是明文密码。
--   BCrypt 输出固定 60 字符；同一密码每次哈希结果都不同（自带随机盐），
--   校验时用 matches(raw, hash)，不能反推原文。
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,   -- 用户名唯一，登录用
    password_hash VARCHAR(100) NOT NULL,          -- BCrypt 哈希（60 字符，留余量）
    role          VARCHAR(16)  NOT NULL,          -- USER / ADMIN
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 订单表。服务每次启动都会执行这个脚本，用 IF NOT EXISTS 保证可重复执行
CREATE TABLE IF NOT EXISTS orders (
    seq      BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 自增序号：用来保持"插入顺序"，等价于原来 LinkedHashMap 的作用
    id       VARCHAR(64) NOT NULL UNIQUE,        -- 订单号：唯一约束，数据库层面再兜一层底
    amount   DECIMAL(12,2) NOT NULL,             -- 金额：DECIMAL(总位数, 小数位数)，精确十进制，绝不用 FLOAT/DOUBLE
    status   VARCHAR(16) NOT NULL,               -- 状态：存枚举的 name()，如 UNPAID / PAID / CANCELLED
    owner_id VARCHAR(64) NULL                    -- 下单用户（用户名）。NULL = 历史数据/匿名订单
);

-- 订单明细表：一个订单有多条明细（1:N）
--
-- 三个设计要点：
--   1) order_id 是外键 -> 指向 orders.id，保证"明细一定属于一张存在的订单"。
--      （orders.id 有 UNIQUE 约束，才能被外键引用。）
--   2) 外键自带索引：MySQL 和 H2 都会为外键列自动建索引，
--      所以"按订单号查明细"不会全表扫描，不需要再手写 CREATE INDEX
--      （MySQL 8 并不支持 CREATE INDEX IF NOT EXISTS，写了反而两边不通用）。
--   3) 单价用 DECIMAL(12,2)，与订单金额一致（钱不用 FLOAT/DOUBLE）。
CREATE TABLE IF NOT EXISTS order_items (
    seq          BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 自增序号，保证明细的显示顺序
    order_id     VARCHAR(64)   NOT NULL,             -- 属于哪个订单
    product_name VARCHAR(128)  NOT NULL,             -- 商品名
    price        DECIMAL(12,2) NOT NULL,             -- 单价
    quantity     INT           NOT NULL,             -- 数量
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
);
