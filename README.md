# 订单管理系统（Order Management System）

一个基于 Spring Boot 的订单管理后端服务，提供订单的创建、查询、支付、取消与统计能力，
使用分层架构设计，并通过单元测试保证业务规则的正确性。

## 功能特性

- **创建订单**：自动校验订单号非空、不重复，金额必须大于 0
- **查询订单**：支持按订单号查询单笔、查询全部订单
- **支付订单**：仅允许「待支付」状态的订单支付
- **取消订单**：仅允许「待支付」状态的订单取消
- **金额统计**：按订单状态汇总总金额
- **数据持久化**：使用 H2 文件数据库，服务重启后数据不丢失
- **并发安全**：通过数据库原子条件更新保证同一笔订单不会被重复支付
- **统一异常处理**：所有错误响应统一为 `{"error": "..."}` 格式，并映射为语义化的 HTTP 状态码
  （400 参数不合法 / 404 资源不存在 / 409 状态冲突），避免暴露 Spring 默认错误页的内部信息
- **接口文档**：集成 Swagger UI，可在浏览器中直接调试所有接口

## 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 21 | 语言与运行时 |
| Maven | 3.9+ | 构建与依赖管理 |
| Spring Boot | 3.5.16 | 应用框架、依赖注入、Web 容器 |
| Spring JDBC | JdbcTemplate | 数据库访问 |
| H2 Database | 2.3 | 内嵌式关系数据库（文件模式） |
| JUnit 5 | 5.11+ | 单元测试 |
| springdoc-openapi | 2.8.17 | Swagger UI 接口文档 |

## 架构设计

项目采用四层架构，**依赖方向严格单向**（上层依赖下层，下层不感知上层）：

```
        HTTP 请求
            │
            ▼
┌───────────────────────────┐
│  接口层 OrderController    │  只负责：解析 HTTP 请求、返回响应
│                           │  不做任何业务判断
└───────────────────────────┘
            │
            ▼
┌───────────────────────────┐
│  业务层 OrderService       │  只负责：业务规则校验与状态流转
│                           │  订单号唯一、金额 > 0、状态机约束
└───────────────────────────┘
            │
            ▼
┌───────────────────────────┐
│  数据层 OrderRepository    │  只负责：订单的存取（SQL）
│                           │  是唯一知道「数据存在哪」的一层
└───────────────────────────┘
            │
            ▼
┌───────────────────────────┐
│  实体 Order / OrderStatus  │  数据载体
└───────────────────────────┘
```

**设计要点**：业务规则全部集中在 `OrderService`，`OrderController` 中没有任何业务判断逻辑。
因此更换数据存储方式时，只需修改 `OrderRepository` 一层 —— 本项目从内存 `Map` 迁移到 H2 数据库时，
`OrderController`、`Order`、`OrderStatus` 均无需修改。

**对外契约与内部模型分离**：接口层不直接暴露 `Order` 实体，而是使用 `dto` 包中的
`CreateOrderRequest`（入参）与 `OrderResponse`（出参）。这样实体的内部结构可以自由演化，
而不会破坏对外的 API 契约，同时也明确了「哪些字段对外可见」。

## 快速开始

### 前置要求

- JDK 21 或更高版本
- Maven 3.9 或更高版本

### 启动服务

```bash
mvn spring-boot:run
```

启动成功后，服务监听 `http://localhost:8080`。

### 运行测试

```bash
mvn test
```

### 访问入口

| 地址 | 说明 |
|---|---|
| http://localhost:8080/swagger-ui.html | Swagger UI 接口文档（可在浏览器中直接调用接口） |
| http://localhost:8080/h2-console | H2 数据库控制台（连接信息见下方） |

H2 控制台登录信息：

| 字段 | 值 |
|---|---|
| JDBC URL | `jdbc:h2:file:./data/orderdb` |
| User Name | `sa` |
| Password | （留空） |

## API 接口

| 方法 | 路径 | 说明 | 成功 | 失败 |
|---|---|---|---|---|
| GET | `/orders` | 查询全部订单 | 200 | — |
| POST | `/orders` | 创建订单 | 201 | 400 参数不合法 |
| GET | `/orders/{id}` | 按订单号查询 | 200 | 404 订单不存在 |
| POST | `/orders/{id}/pay` | 支付订单 | 200 | 409 状态冲突 |
| POST | `/orders/{id}/cancel` | 取消订单 | 200 | 409 状态冲突 |
| GET | `/orders/stats` | 按状态统计金额 | 200 | — |

### 请求示例

创建订单：

```bash
curl -X POST http://localhost:8080/orders \
     -H "Content-Type: application/json" \
     -d '{"id":"A001","amount":100}'
```

响应：

```json
{
  "id": "A001",
  "amount": 100,
  "status": "UNPAID"
}
```

订单号重复时的响应（HTTP 400）：

```json
{
  "error": "订单号已存在：A001"
}
```

### 错误响应

所有错误响应使用**统一格式**，客户端只需处理一种结构：

```json
{
  "error": "错误原因"
}
```

| 场景 | 状态码 | 响应示例 |
|---|---|---|
| 订单号为空 | 400 | `{"error": "订单号不能为空"}` |
| 金额 ≤ 0 | 400 | `{"error": "金额必须大于 0"}` |
| 订单号重复 | 400 | `{"error": "订单号已存在：A001"}` |
| 订单不存在 | 404 | `{"error": "订单不存在：A001"}` |
| 状态冲突（重复支付/取消已支付订单） | 409 | `{"error": "支付失败：订单不存在或不是待支付状态"}` |

由 `ApiExceptionHandler` 统一处理，避免使用 Spring 默认错误页
（默认错误页会返回 `timestamp`、`path` 等内部信息，且格式与业务错误不一致）。

支付订单：

```bash
curl -X POST http://localhost:8080/orders/A001/pay
```

统计各状态金额：

```bash
curl http://localhost:8080/orders/stats
```

```json
{
  "unpaid": 200,
  "paid": 100,
  "cancelled": 0
}
```

## 状态说明

订单状态由 `OrderStatus` 枚举定义：

| 状态 | 含义 | 可流转到 |
|---|---|---|
| `UNPAID` | 待支付 | `PAID`、`CANCELLED` |
| `PAID` | 已支付 | 终态 |
| `CANCELLED` | 已取消 | 终态 |

## 项目结构

```
order-app/
├── pom.xml                                  Maven 配置
├── README.md
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/example/order/
    │   │   ├── OrderAppApplication.java     Spring Boot 启动类
    │   │   ├── OrderController.java         REST 接口层
    │   │   ├── OrderService.java            业务层（业务规则）
    │   │   ├── OrderRepository.java         数据层（数据库访问）
    │   │   ├── Order.java                   订单实体（内部模型）
    │   │   ├── OrderStatus.java             订单状态枚举
    │   │   ├── ApiExceptionHandler.java     全局异常处理
    │   │   └── dto/
    │   │       ├── CreateOrderRequest.java   入参 DTO
    │   │       └── OrderResponse.java        出参 DTO
    │   └── resources/
    │       ├── application.properties       数据库与应用配置
    │       └── schema.sql                   建表脚本
    └── test/
        ├── java/com/example/order/
        │   └── OrderServiceTest.java        单元测试
        └── resources/
            └── application.properties       测试专用配置（内存数据库）
```

## 数据库设计

```sql
CREATE TABLE orders (
    seq    BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 自增序号，保证查询顺序
    id     VARCHAR(64) NOT NULL UNIQUE,        -- 订单号
    amount INT         NOT NULL,               -- 金额
    status VARCHAR(16) NOT NULL                -- 状态
);
```

设计说明：

- `seq` 自增主键用于保证「查询全部」时的插入顺序（数据库的查询结果默认不保证顺序）
- `id` 上的 `UNIQUE` 约束与业务层的重复校验形成**双重保障**：业务层负责给出友好的错误提示，数据库层负责保证数据的绝对正确

## 测试

共 8 个单元测试，覆盖全部业务规则与并发场景：

| 测试 | 验证内容 |
|---|---|
| `createOrder_success` | 正常创建订单，字段与初始状态正确 |
| `createOrder_amountNotPositive_throws` | 金额 ≤ 0 时抛出异常 |
| `createOrder_idDuplicate_throws` | 订单号重复时抛出异常 |
| `createOrder_idEmpty_throws` | 订单号为空时抛出异常 |
| `pay_unpaidOrder_success` | 支付待支付订单成功，状态变更为已支付 |
| `pay_orderNotFound_returnsFalse` | 支付不存在的订单返回 false |
| `pay_concurrentOnlyOneSucceeds` | 20 线程并发支付同一订单，**仅 1 个成功** |
| `cancel_concurrentOnlyOneSucceeds` | 20 线程并发取消同一订单，**仅 1 个成功** |

测试使用 H2 **内存数据库**（`jdbc:h2:mem:testdb`），并在每个测试方法前清空数据表，
以保证测试之间相互独立、且不污染开发数据。

## 技术实现要点

- **依赖注入**：`OrderService` 与 `OrderRepository` 的依赖均由 Spring 容器注入，而非自行创建，
  从而实现层与层之间的解耦，并便于在测试中替换依赖
- **不可变设计**：订单号与金额使用 `final` 修饰，创建后不可修改；订单号作为数据主键，不可变性尤为重要
- **SQL 注入防护**：全部 SQL 使用 `?` 占位符传参，不进行字符串拼接
- **异常语义化**：参数非法抛 `IllegalArgumentException`（由全局处理器转为 HTTP 400），
  业务结果失败返回 `false`（由接口层转为对应的状态码），二者语义区分明确
- **并发安全的订单状态流转**：订单状态变更不使用「先查询状态、再判断、再更新」的写法
  （该写法在查询与更新之间存在时间窗口，并发时可能导致同一订单被重复支付）。
  而是将状态条件写入 `UPDATE` 语句的 `WHERE` 子句，由数据库在一次原子操作内完成
  「校验 + 修改」，并通过受影响行数判断是否成功：

  ```sql
  UPDATE orders SET status = ? WHERE id = ? AND status = 'UNPAID'
  ```

  该模式同样适用于扣库存（`WHERE stock > 0`）、转账（`WHERE balance >= ?`）等并发场景。
  测试中已通过 20 线程并发压测验证：仅 1 个请求成功，其余返回 409。
