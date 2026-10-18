# 订单管理系统（Order Management System）

![CI](https://github.com/QWER666M/order/actions/workflows/ci.yml/badge.svg)

一个基于 Spring Boot 的订单管理后端服务，提供订单的创建、查询、支付、取消与统计能力，
使用分层架构设计，并通过单元测试保证业务规则的正确性。

## 功能特性

- **创建订单**：自动校验订单号非空、不重复，金额必须大于 0
- **用户体系与 JWT 鉴权**：注册 / 登录签发 JWT；`/orders/**` 需携带 `Authorization: Bearer <token>`，
  未登录返回 401；密码使用 **BCrypt** 哈希存储
- **数据隔离**：每个用户只能查询、支付、取消**自己**的订单；访问他人订单统一返回 404（不泄露订单是否存在）
- **角色与权限模型**：`USER` / `ADMIN` 两种角色。管理员通过 `/admin/**` 可查看全部订单、
  取消任意订单、查看用户列表与全局统计；普通用户访问管理接口返回 403。
  管理员**不能代付**他人订单 —— 资金操作只归所属人。角色以数据库为准，降权后旧 token 立即失效
- **订单明细**：一个订单可包含多条商品明细，总价由**服务端**按「单价 × 数量」计算
- **查询订单**：支持按订单号查询单笔、分页查询订单列表
- **支付订单**：仅允许「待支付」状态的订单支付
- **取消订单**：仅允许「待支付」状态的订单取消
- **金额统计**：按订单状态汇总总金额
- **数据持久化**：使用 MySQL 数据库，服务重启后数据不丢失
- **并发安全**：通过数据库原子条件更新保证同一笔订单不会被重复支付
- **统一异常处理**：所有错误响应统一为 `{"error": "..."}` 格式，并保留语义化的 HTTP 状态码
  （400 参数不合法 / 404 资源不存在 / 405 方法不支持 / 409 状态冲突 / 415 媒体类型不支持 / 500 服务器内部错误）。
  实现上继承 `ResponseEntityExceptionHandler`，因此框架自身抛出的标准异常（JSON 格式错误等）
  也会走同一套格式，不会破坏框架判定的状态码
- **接口文档**：集成 Swagger UI，可在浏览器中直接调试所有接口

## 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 21 | 语言与运行时 |
| Maven | 3.9+ | 构建与依赖管理 |
| Spring Boot | 3.5.16 | 应用框架、依赖注入、Web 容器 |
| Spring JDBC | JdbcTemplate | 数据库访问 |
| MySQL | 8.x | 主数据库（生产形态） |
| HikariCP | Spring Boot 自带 | 数据库连接池 |
| H2 Database | 2.3 | 测试数据库 + 免 Docker 的备用运行方式 |
| JUnit 5 | 5.11+ | 单元测试 |
| springdoc-openapi | 2.8.17 | Swagger UI 接口文档 |
| Docker Compose | — | 一键启动 MySQL 开发环境 |
| Spring Security Crypto | — | 仅使用 BCrypt 密码哈希 |
| jjwt | 0.12.7 | JWT 签发与校验 |

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
因此更换数据存储方式时，只需修改 `OrderRepository` 一层 —— 本项目从内存 `Map` 迁移到 H2、
再从 H2 迁移到 MySQL 时，`OrderController`、`Order`、`OrderStatus` 均无需修改。

**对外契约与内部模型分离**：接口层不直接暴露 `Order` 实体，而是使用 `dto` 包中的
`CreateOrderRequest`（入参）与 `OrderResponse`（出参）。这样实体的内部结构可以自由演化，
而不会破坏对外的 API 契约，同时也明确了「哪些字段对外可见」。

## 快速开始

### 前置要求

- **方式一（容器化，只需 Docker）**：Docker Desktop（含 Compose V2）
- **方式二（本地运行，改代码更快）**：JDK 21+、Maven 3.9+，以及一个可用的 MySQL
  （也可用 `h2` profile 完全免数据库）

## 方式一：一条命令起全套（Docker）

```bash
docker compose up -d --build
```

这条命令做了三件事：

1. 按 [`Dockerfile`](Dockerfile) 把 Spring Boot 打成镜像（多阶段构建，最终镜像只含 JRE + jar）
2. 启动 MySQL 8.4 容器，等它通过健康检查
3. 启动应用容器，通过服务名 `mysql:3306` 连库，端口映射到宿主机 `8080`

确认状态：

```bash
docker compose ps            # mysql 应为 healthy，app 应为 running
docker compose logs -f app   # 看应用日志
```

然后访问 http://localhost:8080/swagger-ui.html 。

> **两个关键点**
>
> - **容器里的 `localhost` 指的是容器自己**。应用配置里写的 `localhost:3306` 在容器内指向 app 容器自身，
>   那里根本没有 MySQL。`docker-compose.yml` 用 `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/...`
>   覆盖成**服务名** —— Docker 内置 DNS 会把 `mysql` 解析到数据库容器。
>   这也是为什么**没有改一行代码**：环境变量优先级高于配置文件。
> - **`depends_on` 必须写 `condition: service_healthy`**。简写的 `depends_on: [mysql]`
>   只等"容器已启动"，不等"数据库可连接"，应用会在 MySQL 初始化那几十秒里连接失败退出。

改了代码后重新构建（`--build` 强制重建镜像）：

```bash
docker compose up -d --build
```

## 方式二：本地运行

### 第 1 步：启动数据库

只启动 MySQL 容器（注意带上服务名 `mysql`，否则会把 app 容器也一起拉起来，占用 8080）：

```bash
docker compose up -d mysql
```

确认状态为 `healthy`（首次启动 MySQL 初始化需要几十秒）：

```bash
docker compose ps
```

数据库连接信息（已在 `application.properties` 中配置好）：

| 项 | 值 |
|---|---|
| host / port | `localhost:3306` |
| database | `orderdb` |
| 应用账号 | `order` / `order123` |
| root 账号 | `root` / `root123`（仅用于管理，应用不使用） |

> 这些是**开发环境的明文口令**，仅用于本地/演示。生产环境应通过环境变量或配置中心注入，
> 不能写死在代码库里。

#### 使用本机已安装的 MySQL（不用 Docker）

先把库和账号建好 —— 用 root 执行仓库里的初始化脚本：

```bash
mysql -u root -p < db/init-mysql.sql
```

或者在 **MySQL Workbench** 中打开 `db/init-mysql.sql`，用 root 连接后点 ⚡ 执行。

脚本会创建 `orderdb` 库、创建应用账号 `order`，并只授予该库的权限。
**表结构不用手动建** —— 应用启动时 `schema.sql` 会自动执行。

#### 数据库结构变更后如何重建

`schema.sql` 用的是 `CREATE TABLE IF NOT EXISTS`，**它不会修改已存在的表**。
所以当表结构变化（例如新增 `owner_id`、新增 `users` / `order_items` 表）时，需要重建库：

| 环境 | 做法 |
|---|---|
| **H2（`h2` profile）** | 停止应用 → 删除 `data/` 目录 → 重新启动即可 |
| **MySQL** | 执行 `db/init-mysql.sql`（**会清空数据**） |
| **MySQL（保留数据）** | 手动迁移，例如 `ALTER TABLE orders ADD COLUMN owner_id VARCHAR(64) NULL;` |

典型症状：启动或调用接口时报 `Column "OWNER_ID" not found` / `Table "USERS" not found` —— 说明库还是旧结构。

> **用 PowerShell 测试中文时注意**：Windows PowerShell 5.1 的 `Invoke-RestMethod`
> 不按 UTF-8 处理请求体，中文会先变成 `?` 再发出去（数据库里看到 `3F3F`）。
> 这不是服务端问题 —— 用 `curl --data-binary "@utf8.json"` 上传即可正常。
> 排查方法：`SELECT HEX(product_name)`，若为 `E88BB9E69E9C`（"苹果"）说明存储正确。

### 第 2 步：启动服务

```bash
mvn spring-boot:run
```

启动成功后，服务监听 `http://localhost:8080`。

**不使用 Docker 时**，可用 H2 备用配置运行（数据存在本地 `data/` 目录）：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

> 启动应用不需要预先建表：`spring.sql.init.mode=always` 会在每次启动时执行
> `schema.sql`（脚本用 `CREATE TABLE IF NOT EXISTS`，可重复执行）。

### 运行测试

```bash
mvn test
```

测试使用 H2 **内存数据库**（`jdbc:h2:mem:testdb`），**不需要 MySQL、也不需要 Docker**，
几秒钟即可跑完，且不会污染开发数据。

### 构建可执行 JAR

```bash
mvn clean verify
java -jar target/order-app-1.0.0.jar
```

配合环境变量可以不改配置就指向别的数据库（与容器里的启动方式完全一致）：

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/orderdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:SPRING_DATASOURCE_USERNAME = "order"
$env:SPRING_DATASOURCE_PASSWORD = "order123"
java -jar target/order-app-1.0.0.jar
```

### 访问入口

| 地址 | 说明 |
|---|---|
| http://localhost:8080/swagger-ui.html | Swagger UI 接口文档（可在浏览器中直接调用接口） |
| http://localhost:8080/h2-console | H2 数据库控制台（**仅 `h2` profile 下可用**） |

H2 控制台登录信息（使用 `h2` profile 时）：

| 字段 | 值 |
|---|---|
| JDBC URL | `jdbc:h2:file:./data/orderdb` |
| User Name | `sa` |
| Password | （留空） |

### 环境配置（Profile）

配置分为公共部分与 profile 专属部分，**profile 专属文件中的同名 key 优先生效**：

| 文件 | 作用 |
|---|---|
| `application.properties` | 公共配置 + 默认数据源（MySQL） |
| `application-h2.properties` | `h2` profile：改用 H2 文件数据库，无需 Docker |
| `src/test/resources/application.properties` | 测试专用：H2 内存库（在 classpath 上优先于主配置，因此测试完全不受 MySQL 影响） |

## API 接口

| 方法 | 路径 | 说明 | 成功 | 失败 |
|---|---|---|---|---|
| GET | `/orders` | 分页查询**当前用户**的订单 | 200 | 400 参数不合法 / 401 未登录 |
| POST | `/orders` | 创建订单（可带明细） | 201 | 400 参数不合法 / 401 未登录 |
| GET | `/orders/{id}` | 查询订单详情（含明细） | 200 | 401 未登录 / 404 订单不存在或无权限 |
| POST | `/orders/{id}/pay` | 支付订单 | 200 | 401 未登录 / 404 无权限 / 409 状态冲突 |
| POST | `/orders/{id}/cancel` | 取消订单 | 200 | 401 未登录 / 404 无权限 / 409 状态冲突 |
| GET | `/orders/stats` | 按状态统计**当前用户**金额 | 200 | 401 未登录 |
| POST | `/auth/register` | 注册并返回 token | 201 | 400 用户名/密码不合法 |
| POST | `/auth/login` | 登录并返回 token | 200 | 401 用户名或密码错误 |

### 管理端接口（仅 `ADMIN`）

| 方法 | 路径 | 说明 | 成功 | 失败 |
|---|---|---|---|---|
| GET | `/admin/orders` | 分页查询**全部用户**的订单，可用 `?ownerId=` 筛选 | 200 | 401 未登录 / 403 非管理员 |
| GET | `/admin/orders/{id}` | 查看任意订单详情（含明细） | 200 | 401 / 403 / 404 不存在 |
| POST | `/admin/orders/{id}/cancel` | 取消任意订单（运营场景） | 200 | 401 / 403 / 404 / 409 状态冲突 |
| GET | `/admin/users` | 用户列表（用户名 / 角色 / 创建时间） | 200 | 401 / 403 |
| GET | `/admin/stats` | **全部用户**的金额统计 | 200 | 401 / 403 |

### 认证

除 `/auth/**`、Swagger、H2 控制台外，所有接口都需要 JWT。

**1) 注册（或登录）拿到 token**

```bash
curl -X POST http://localhost:8080/auth/register \
     -H "Content-Type: application/json" \
     -d '{"username":"alice","password":"secret123"}'
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSIsInJvbGUiOiJVU0VSIn0.xxxxx",
  "username": "alice",
  "role": "USER"
}
```

**2) 后续请求带上 token**

```bash
curl http://localhost:8080/orders \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

| 情况 | 响应 |
|---|---|
| 未带 `Authorization` 头 | `401 {"error":"未登录：请在 Authorization 头中提供 Bearer token"}` |
| token 无效 / 被篡改 / 过期 | `401 {"error":"登录已失效，请重新登录"}` |
| 访问他人的订单 | `404 {"error":"订单不存在：xxx"}`（**故意不用 403**，避免泄露订单是否存在） |

> ⚠️ **JWT 是签名，不是加密**：payload 只是 Base64 编码，任何人都能解开。
> 因此 token 中**只放用户名和角色**，绝不放密码等敏感信息。

### 角色与权限模型

系统有两个角色：`USER`（注册默认）与 `ADMIN`。

**权限矩阵**

| 操作 | USER | ADMIN |
|---|---|---|
| 查看 / 支付 / 取消**自己的**订单 | ✅ | ✅ |
| 查看**他人的**订单 | ❌ 404 | ✅ 走 `/admin/**` |
| 取消**他人的**订单 | ❌ 404 | ✅ 走 `/admin/orders/{id}/cancel` |
| 支付**他人的**订单 | ❌ 404 | **❌ 不提供**（资金操作只能由所属人发起） |
| 查看全部用户 | ❌ 403 | ✅ |
| 全局金额统计 | ❌ 403 | ✅ |

即：**读权限可以放宽，写权限按业务性质分级** —— 管理员能审计、能止损，但不能代替用户花钱。

**三种失败状态码的语义**

| 状态码 | 含义 | 出现在 |
|---|---|---|
| **401** | 不知道你是谁 | 无 token / token 无效或过期 / 用户已被删除 |
| **403** | 知道你是谁，但这条路径你不该进 | 普通用户访问 `/admin/**` |
| **404** | 知道你是谁，但不告诉你资源存不存在 | 访问他人订单（**故意不用 403**，避免被用来枚举订单号） |

**如何创建管理员**

注册一律是 `USER` —— 服务端不接受客户端在注册时指定角色（否则人人都能注册成管理员）。提升角色直接改数据库：

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'alice';
```

改完**立即生效**，无需重新登录。

**为什么角色要回数据库查，而不是直接读 token 里的 `role` 声明？**

token 一旦签发就无法收回。如果用户被降权、甚至被删除，旧 token 里仍然写着 `ADMIN`，据此授权等于留了个后门。
所以 `AuthInterceptor` 每个请求都会用 token 里的用户名回库取**当前角色**：

- 好处：权限变更立即生效，不需要"强制下线"机制
- 代价：每请求多一次按唯一索引的查询（后续可用缓存优化）

一句话：**token 只证明「你是谁」，不决定「你能做什么」。**

**权限要求写在哪**

`@RequireRole` 注解标在 `AdminController` 类上，由 `RoleInterceptor` 统一拦截：

```java
@RestController
@RequestMapping("/admin")
@RequireRole(Role.ADMIN)     // 整个 Controller 都需要 ADMIN
public class AdminController { ... }
```

新增管理接口时**不需要改拦截器**，注解即契约。这与 Spring Security 的 `@PreAuthorize` 是同一思路。

### 请求示例

**方式一：带明细创建（推荐）**

```bash
curl -X POST http://localhost:8080/orders \
     -H "Content-Type: application/json" \
     -d '{
           "id": "A001",
           "items": [
             { "productName": "苹果", "price": 10.00, "quantity": 2 },
             { "productName": "香蕉", "price": 3.50,  "quantity": 1 }
           ]
         }'
```

响应（总价由**服务端**计算：10.00×2 + 3.50×1 = 23.50）：

```json
{
  "id": "A001",
  "amount": 23.50,
  "status": "UNPAID"
}
```

> 即使请求体里同时传了 `"amount": 1.00`，也会被忽略 —— **金额不能由客户端决定**。

**方式二：只给总额（兼容旧用法）**

```bash
curl -X POST http://localhost:8080/orders \
     -H "Content-Type: application/json" \
     -d '{"id":"A002","amount":100.00}'
```

**查询订单详情**（含明细与小计）：

```bash
curl http://localhost:8080/orders/A001
```

```json
{
  "id": "A001",
  "amount": 23.50,
  "status": "UNPAID",
  "items": [
    { "productName": "苹果", "price": 10.00, "quantity": 2, "subtotal": 20.00 },
    { "productName": "香蕉", "price": 3.50,  "quantity": 1, "subtotal": 3.50 }
  ]
}
```

> 列表接口 `GET /orders` **不返回明细** —— 否则一页 10 条订单就要额外查 10 次明细（N+1 查询问题）。

订单号重复时的响应（HTTP 400）：

```json
{
  "error": "订单号已存在：A001"
}
```

### 分页查询

```bash
curl "http://localhost:8080/orders?page=1&size=10"
```

| 参数 | 默认值 | 取值范围 | 说明 |
|---|---|---|---|
| `page` | 1 | ≥ 1 | 页码，**从 1 开始** |
| `size` | 10 | 1 ~ 100 | 每页条数（设上限防止一次拉走全表） |

响应：

```json
{
  "items": [
    { "id": "A001", "amount": 100.00, "status": "UNPAID" }
  ],
  "total": 25,
  "page": 1,
  "size": 10,
  "totalPages": 3
}
```

分页由数据库完成（SQL `LIMIT ? OFFSET ?`），**不会把全表读进内存**。

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
| 金额超过两位小数 | 400 | `{"error": "金额最多支持两位小数"}` |
| 页码小于 1 | 400 | `{"error": "页码必须大于等于 1"}` |
| 每页条数超出 1~100 | 400 | `{"error": "每页条数必须在 1 ~ 100 之间"}` |
| 订单号重复 | 400 | `{"error": "订单号已存在：A001"}` |
| 订单不存在 | 404 | `{"error": "订单不存在：A001"}` |
| 状态冲突（重复支付/取消已支付订单） | 409 | `{"error": "支付失败：订单不存在或不是待支付状态"}` |
| 未登录 / token 无效 | 401 | `{"error": "登录已失效，请重新登录"}` |
| 登录密码错误 | 401 | `{"error": "用户名或密码错误"}` |
| 访问他人订单 | 404 | `{"error": "订单不存在：A001"}` |
| 请求体 JSON 格式错误 | 400 | `{"error": "Bad Request"}` |
| 请求方法不支持 | 405 | `{"error": "Method Not Allowed"}` |
| 请求 Content-Type 不支持 | 415 | `{"error": "Unsupported Media Type"}` |
| 未预期的服务端异常 | 500 | `{"error": "服务器内部错误"}` |

由 `ApiExceptionHandler` 统一处理。该处理器继承 Spring 的 `ResponseEntityExceptionHandler`，
因此**框架自身抛出的标准异常**（如 JSON 解析失败）也会返回统一格式，且保留框架判定的状态码；
对未预期的异常（500）则**记录完整堆栈到服务端日志**，对外只返回一句不含内部细节的提示。

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
  "unpaid": 200.00,
  "paid": 100.00,
  "cancelled": 0.00
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
├── docker-compose.yml                       MySQL + 应用 一键启动
├── Dockerfile                               多阶段构建（Maven 构建 → 仅 JRE 运行）
├── .dockerignore                            构建上下文排除项（target/、data/、.git/…）
├── db/init-mysql.sql                        重建 MySQL 库与账号（root 执行）
├── .github/workflows/ci.yml                 CI：H2 快速验证 + MySQL 真库验证
└── src/
    ├── main/
    │   ├── java/com/example/order/
    │   │   ├── OrderAppApplication.java     Spring Boot 启动类
    │   │   ├── OrderController.java         REST 接口层
    │   │   ├── OrderService.java            业务层（业务规则）
    │   │   ├── OrderRepository.java         数据层（数据库访问）
    │   │   ├── Order.java                   订单实体（内部模型）
    │   │   ├── OrderItem.java               订单明细实体（1:N）
    │   │   ├── User.java                    用户实体
    │   │   ├── OrderStatus.java             订单状态枚举
    │   │   ├── UserService.java             注册 / 登录（BCrypt 校验）
    │   │   ├── UserRepository.java          用户数据访问
    │   │   ├── JwtService.java              JWT 签发与验签
    │   │   ├── AuthController.java          /auth/register、/auth/login
    │   │   ├── AdminController.java         管理端接口（仅 ADMIN）
    │   │   ├── AuthInterceptor.java         校验 Bearer token
    │   │   ├── Role.java                    角色枚举 USER / ADMIN
    │   │   ├── RequireRole.java             声明接口所需角色的注解
    │   │   ├── RoleInterceptor.java         按 @RequireRole 做授权（403）
    │   │   ├── WebConfig.java               注册拦截器与放行路径
    │   │   ├── PasswordEncoderConfig.java   BCrypt Bean
    │   │   ├── LoginFailedException.java    登录失败（映射为 401）
    │   │   ├── ApiExceptionHandler.java     全局异常处理
    │   │   └── dto/
    │   │       ├── RegisterRequest.java      注册入参
    │   │       ├── LoginRequest.java         登录入参
    │   │       ├── AuthResponse.java         登录/注册出参（token）
    │   │       ├── CreateOrderRequest.java   创建订单入参（可含 items）
    │   │       ├── CreateOrderItemRequest.java  单条明细入参
    │   │       ├── OrderResponse.java        列表用出参（不含明细）
    │   │       ├── OrderDetailResponse.java  详情用出参（含明细）
    │   │       ├── OrderItemResponse.java    明细出参（含小计）
    │   │       ├── AdminUserResponse.java    管理端用户列表项（不含密码）
    │   │       └── PageResponse.java         通用分页结构
    │   └── resources/
    │       ├── application.properties       公共配置 + 默认数据源（MySQL）
    │       ├── application-h2.properties    h2 profile：免 Docker 的备用数据源
    │       └── schema.sql                   建表脚本（MySQL / H2 通用）
    └── test/
        ├── java/com/example/order/
        │   ├── OrderServiceTest.java        业务层测试（含并发测试）
        │   ├── OrderControllerTest.java     接口层测试（MockMvc）
        │   ├── AuthApiTest.java             认证与授权测试（开启鉴权）
        │   └── AdminApiTest.java            角色与权限模型测试（开启鉴权）
        └── resources/
            └── application.properties       测试专用配置（H2 内存库）
```

## 数据库设计

```sql
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt 哈希（60 字符）
    role          VARCHAR(16)  NOT NULL,   -- USER / ADMIN
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    seq    BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 自增序号，保证查询顺序
    id     VARCHAR(64) NOT NULL UNIQUE,        -- 订单号
    amount DECIMAL(12,2) NOT NULL,              -- 金额：DECIMAL(总位数, 小数位数)，精确十进制
    status VARCHAR(16) NOT NULL,                -- 状态
    owner_id VARCHAR(64) NULL                   -- 下单用户；NULL = 历史数据/未鉴权
);

CREATE TABLE order_items (
    seq          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     VARCHAR(64)   NOT NULL,
    product_name VARCHAR(128)  NOT NULL,
    price        DECIMAL(12,2) NOT NULL,
    quantity     INT           NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
);
```

设计说明：

- `seq` 自增主键用于保证「查询全部」时的插入顺序（数据库的查询结果默认不保证顺序）
- `id` 上的 `UNIQUE` 约束与业务层的重复校验形成**双重保障**：业务层负责给出友好的错误提示，数据库层负责保证数据的绝对正确
- `order_items.order_id` 是**外键**，保证"明细一定属于一张存在的订单"；
  外键列由 MySQL / H2 **自动建立索引**，按订单号查明细不会全表扫描
- 建表脚本刻意避开了 MySQL 的反引号与保留字，写成 **MySQL 与 H2 都合法**的 SQL，
  因此两种数据源可共用同一个 `schema.sql`
- 由 Spring 的 `spring.sql.init.mode=always` 在每次启动时执行（`IF NOT EXISTS` 保证可重复执行）。
  后续若 schema 需要频繁演进，可引入 **Flyway / Liquibase** 做版本化迁移
- 测试清库时**必须先删 `order_items` 再删 `orders`**，否则外键约束会阻止删除

## 测试

共 **65 个测试**，分三层：

### 业务层测试 `OrderServiceTest`（18 个）

直接调用 `OrderService` 的 Java 方法，验证业务规则与并发安全：

| 测试 | 验证内容 |
|---|---|
| `createOrder_success` | 正常创建订单，字段与初始状态正确 |
| `createOrder_decimalAmount_success` | 小数金额（99.5）创建成功，精度归一为两位小数（99.50） |
| `createOrder_amountNotPositive_throws` | 金额 ≤ 0 时抛出异常 |
| `createOrder_amountTooManyDecimals_throws` | 金额超过两位小数时抛出异常 |
| `createOrder_idDuplicate_throws` | 订单号重复时抛出异常 |
| `createOrder_idEmpty_throws` | 订单号为空时抛出异常 |
| `pay_unpaidOrder_success` | 支付待支付订单成功，状态变更为已支付 |
| `pay_orderNotFound_returnsFalse` | 支付不存在的订单返回 false |
| `sumAmount_sumsByStatus` | 按状态汇总金额，小数加法精确（10.10 + 0.70 = 10.80） |
| `listOrders_pagination` | 分页正确：页码偏移、总数、总页数、超范围返回空页 |
| `listOrders_invalidPagingParams_throws` | 页码 < 1 或每页条数越界时抛出异常 |
| `createOrder_withItems_computesTotalOnServer` | 带明细创建，总价由服务端按「单价×数量」计算 |
| `createOrder_itemsTakePrecedenceOverClientAmount` | 客户端传的 `amount` 不能覆盖服务端计算的金额 |
| `createOrder_invalidItem_throws` | 商品名为空 / 单价 ≤ 0 / 数量 ≤ 0 均被拒绝 |
| **`createOrder_itemInsertFails_rollsBackOrder`** | **事务回滚**：明细写入失败时，订单本身也被撤销 |
| `pay_concurrentOnlyOneSucceeds` | 20 线程并发支付同一订单，**仅 1 个成功** |
| `cancel_concurrentOnlyOneSucceeds` | 20 线程并发取消同一订单，**仅 1 个成功** |
| `createOrder_concurrentSameId_onlyOneSucceeds` | 20 线程并发创建同一订单号，**仅 1 个成功，其余为友好业务错误（不产生 500）** |

### 接口层测试 `OrderControllerTest`（21 个）

使用 `MockMvc` 模拟 HTTP 请求，验证**状态码、JSON 字段名、错误格式与 DTO 白名单**：

| 测试 | 验证内容 |
|---|---|
| `create_returns201AndBody` | 创建成功返回 201 与正确的 JSON 字段 |
| `create_idBlank_returns400` | 订单号为空返回 400 与统一错误格式 |
| `create_amountNotPositive_returns400` | 金额 ≤ 0 返回 400 |
| `create_idDuplicate_returns400` | 订单号重复返回 400 |
| `create_extraFieldsAreIgnored` | **客户端多传 `status` 被忽略**，不会绕过支付流程 |
| `getOne_found_returns200` | 查询存在的订单返回 200 |
| `getOne_notFound_returns404WithUnifiedError` | 查询不存在返回 404 与 `{"error": ...}` |
| `list_returnsPageWithMetadata` | 列表返回分页结构（items / total / page / size / totalPages），且按插入顺序 |
| `list_empty_returnsEmptyPage` | 空表返回空页 |
| `list_secondPage_returnsRemainingItems` | 第 2 页返回剩余数据（验证分页偏移正确） |
| `list_pageLessThanOne_returns400` / `list_sizeTooLarge_returns400` | 分页参数越界返回 400 |
| `list_sizeNotANumber_returns400` | 非数字参数由框架拦截，同样返回统一错误格式 |
| `create_withItems_returns201AndComputedAmount` | 带明细创建返回 201，金额为服务端算出的 23.50 |
| `getOne_returnsItems` | 详情接口返回明细及服务端计算的小计 |
| `list_doesNotIncludeItems` | 列表接口不返回明细（避免 N+1 查询） |

### 认证与授权测试 `AuthApiTest`（11 个）

单独用 `@SpringBootTest(properties = "app.auth.enabled=true")` 打开鉴权，验证真实的安全行为：

| 测试 | 验证内容 |
|---|---|
| `register_thenLogin_success` | 注册后可登录，返回 token，且响应不含 `passwordHash` |
| `register_duplicateUsername_returns400` | 用户名重复返回 400 |
| `login_wrongPassword_returns401` | 密码错误返回 401 |
| `login_unknownUser_returns401WithSameMessage` | 用户不存在与密码错误返回**同一提示**（防用户枚举） |
| `orders_withoutToken_returns401` | 未带 token 访问业务接口返回 401 |
| `orders_withInvalidToken_returns401` | 无效 token 返回 401 |
| `orders_withTamperedToken_returns401` | **篡改 token 后验签失败**，返回 401 |
| `orders_withToken_returns200` | 带合法 token 正常访问 |
| **`userCannotSeeOrTouchAnotherUsersOrder`** | **越权隔离**：B 看不到、查不到、也支付/取消不了 A 的订单 |
| `stats_onlyCountsCurrentUsersOrders` | 统计接口只统计自己的订单 |
| `authEndpoints_arePublic` | `/auth/**` 无需 token（否则无法登录） |
| `pay_success_returns200` | 支付成功返回 200 |
| `pay_alreadyPaid_returns409` / `pay_orderNotFound_returns409` | 状态冲突返回 409 |
| `cancel_success_returns200` | 取消成功返回 200 |
| `stats_returnsTotalsByStatus` | 统计接口返回各状态金额 |

两层测试的分工：**业务层测"逻辑对不对"，接口层测"HTTP 契约对不对"**。

### 角色与权限测试 `AdminApiTest`（14 个）

同样开启鉴权，专门验证"谁能做什么"：

| 测试 | 验证内容 |
|---|---|
| `adminEndpoints_withoutToken_returns401` | 未登录访问 `/admin/**` 返回 401 |
| `adminEndpoints_asNormalUser_returns403` | 普通用户访问 `/admin/**` 返回 **403** |
| `adminEndpoints_withInvalidToken_returns401` | 伪造 token 返回 401 |
| `admin_seesAllUsersOrders` | 管理员能看到**所有用户**的订单 |
| `admin_canFilterByOwnerId` | `?ownerId=` 可按归属人筛选 |
| `admin_canReadOthersOrderWithItems` | 管理员能读任意订单详情（含明细） |
| `admin_canCancelOthersOrder` | 管理员能取消他人订单 |
| `admin_cancelPaidOrder_stillReturns409` | **管理员不改变业务规则**：已支付订单仍不能取消 |
| `admin_cannotPayOthersOrder_returns404` | **管理员不能代付**：资金操作只归所属人 |
| `normalUser_stillCannotReadOthersOrder_returns404` | 回归：普通用户的数据隔离没有被破坏 |
| `admin_users_neverExposePasswordHash` | 用户列表响应中**不含任何密码字段** |
| `admin_stats_coversAllUsers` | 全局统计覆盖所有用户，而非只统计自己 |
| **`promotedAndDemotedAdmin_takesEffectImmediately`** | **权限以数据库为准**：同一个旧 token，提升后立刻 200，撤权后立刻 403 |
| `deletedUser_tokenBecomesInvalid` | 用户被删除后，未过期的 token 立刻失效（401） |

### 数据源守卫 `DataSourceBindingTest`（1 个）

只做一件事：断言"当前测试到底连的是哪个数据库"，由环境变量 `EXPECT_DB` 驱动。

- 本地不设 `EXPECT_DB` → 自动 `assumeTrue` **跳过**（默认跑 H2，不需要装数据库）
- CI 设置 `EXPECT_DB=mysql` → 连接串里必须出现 `mysql`，否则测试失败

它防的是一种很隐蔽的事故：CI 里环境变量名字拼错（例如写成 `SPRING_DATASOURCE_URI`），
Spring 会**静默忽略**这个变量，测试于是在 H2 上全绿，流水线却报告"MySQL 通过"——
从头到尾根本没碰过 MySQL。有了这个守卫，流水线就不可能说谎。

### 默认 H2 / 可选 MySQL

测试默认使用 H2 **内存数据库**（`jdbc:h2:mem:testdb`），并在每个测试方法前清空数据表，
以保证测试之间相互独立、且不污染开发数据。

```bash
mvn test                                    # 跑全部测试
mvn test -Dtest=OrderControllerTest         # 只跑接口层测试
mvn test -Dtest=OrderServiceTest#pay_concurrentOnlyOneSucceeds   # 只跑某一个测试
```

同一套测试也可以直接跑在**真 MySQL** 上，无需改动任何代码或配置文件 ——
Spring Boot 的配置优先级是 `命令行参数 > 环境变量 > application.properties`，
而环境变量走宽松绑定（`SPRING_DATASOURCE_URL` → `spring.datasource.url`），
所以只要注入下面几个变量，就会覆盖测试配置里的 H2：

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/orderdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver"
$env:SPRING_DATASOURCE_USERNAME = "order"
$env:SPRING_DATASOURCE_PASSWORD = "order123"
$env:EXPECT_DB = "mysql"      # 打开数据源守卫
mvn clean verify
```

## 持续集成

仓库配置了 GitHub Actions（[`.github/workflows/ci.yml`](.github/workflows/ci.yml)），
每次推送到 `main` 或提交 Pull Request 时并行跑两个任务：

| 任务 | 数据库 | 作用 |
|---|---|---|
| `build`（H2） | H2 内存库 | 快速反馈：几十秒出结果，适合每次 push 都跑 |
| `test-mysql`（MySQL 8.4） | MySQL 8.4 容器 | 真库验证：字符集、严格模式、自增、外键等真实行为 |

`test-mysql` 通过 `services:` 启动一个 `mysql:8.4` 容器（等价于本地 `docker compose up -d`），
并配置健康检查，等数据库真正可连接后才开始执行 `mvn -B clean verify`；
随后用环境变量把测试数据源指向它，同时设置 `EXPECT_DB=mysql` 让守卫测试确认连接确实生效。

两个任务都执行完整的 `clean verify`（编译 → 全部测试 → 打包），任一个失败即表示不能合并。
构建状态见页面顶部的 CI 徽章；失败时可在 Actions 页面下载 `surefire-reports` 构件查看具体用例。

## 技术实现要点

- **依赖注入**：`OrderService` 与 `OrderRepository` 的依赖均由 Spring 容器注入，而非自行创建，
  从而实现层与层之间的解耦，并便于在测试中替换依赖
- **多环境配置（Profile）**：默认使用 MySQL，`h2` profile 提供免 Docker 的备用数据源；
  测试通过 `src/test/resources/application.properties` 覆盖为 H2 内存库 ——
  由于测试类路径优先，测试**完全不受 MySQL 配置影响**，无需任何外部依赖即可运行
- **配置外置（12-Factor）**：Spring Boot 的配置优先级为
  命令行参数 > 环境变量 > `application.properties`，且环境变量走宽松绑定
  （`SPRING_DATASOURCE_URL` 自动对应 `spring.datasource.url`）。
  因此 CI 无需改动任何代码或配置文件，仅靠注入环境变量就能把同一套测试切到真 MySQL 上运行；
  真实部署时数据库口令也应通过这种方式注入，而不是写死在仓库里
- **认证与授权分离**：`AuthInterceptor` 只负责"你是谁"（验签，失败 401）；
  `RoleInterceptor` 读 `@RequireRole` 注解负责"你能做什么"（失败 403）。
  角色不信任 token 里的声明，每个请求回库取当前角色 —— 换来权限变更立即生效
- **权限按路径分层**：`/orders/**` 是"我的订单"（任何角色都只操作自己的），
  `/admin/**` 是"管理视图"（仅 ADMIN）。契约不随角色变化，权限要求用注解声明，
  新增管理接口无需改动拦截器
- **授权按业务性质分级**：管理员能读任意订单、能取消（运营止损），
  但**不能代付** —— 资金操作必须由订单所属人发起。权限是"能对谁做"，不是"能跳过业务规则"
- **容器化（多阶段构建）**：`Dockerfile` 分两个阶段 —— 用 `maven` 镜像编译打包，
  再把**一个 jar** 复制进 `eclipse-temurin:21-jre` 运行。最终镜像不含 Maven、依赖缓存与源码，
  也缩小了攻击面 —— CI 实测镜像 **352MB**（若直接用 maven 镜像做运行环境则在 700MB 以上）
- **构建缓存分层**：先 `COPY pom.xml` + `dependency:go-offline`，再 `COPY src`。
  Docker 每一层都会被缓存，而"输入变了之后的层全部失效"；这样改代码不会重新下载依赖
- **容器安全与优雅停机**：用非 root 用户（`USER appuser`）运行；
  `ENTRYPOINT` 使用 exec 形式（JSON 数组），保证 `java` 是 1 号进程能收到 `SIGTERM`，
  从而触发 Spring 的优雅停机
- **容器间网络**：容器里的 `localhost` 是容器自身，服务之间必须用 compose 的**服务名**
  （本项目的 `mysql:3306`）；配置通过环境变量注入，应用代码与镜像完全环境无关
- **启动顺序**：`depends_on` 配 `condition: service_healthy`，等 MySQL 健康检查通过再启动应用，
  避免"数据库还没初始化完，应用已连接失败退出"
- **连接池（HikariCP）**：Spring Boot 默认自带。连接池复用连接，
  避免每次请求都做 TCP 握手与认证；配置了最大连接数、最小空闲连接与获取超时
- **MySQL 连接参数**：`characterEncoding=utf8` 保证中文不乱码，`serverTimezone` 明确时区，
  `allowPublicKeyRetrieval=true` 用于 MySQL 8 默认的 `caching_sha2_password` 插件在非 SSL 连接下的握手
- **容器化数据库**：`docker-compose.yml` 声明 MySQL 服务，含**健康检查**（容器"起来"≠数据库"可用"）、
  具名数据卷（容器删除后数据仍在）与 utf8mb4 字符集
- **JWT 无状态认证**：登录签发 HS256 签名的 token，拦截器验签后把用户名放进 request 属性。
  身份信息**只从 token 取**，绝不接受客户端传入的用户名 —— 否则可以冒充他人。
  密钥通过配置注入（生产应使用环境变量/配置中心），且长度不足 32 字符会在启动时直接失败
- **密码安全**：使用 **BCrypt** 存储哈希 —— 它刻意设计得慢（抵抗暴力破解）且自带随机盐（抵抗彩虹表）。
  登录失败时对「用户不存在」与「密码错误」返回**同一提示**，避免用户枚举
- **数据隔离**：订单查询把 `WHERE owner_id = ?` 写在 SQL 里（不是查出来再内存过滤）；
  访问他人订单统一返回 **404 而非 403**，避免泄露订单是否存在
- **鉴权开关**：`app.auth.enabled` 控制拦截器是否注册。
  业务测试保持关闭（专注业务逻辑），`AuthApiTest` 通过 `@SpringBootTest(properties=...)` 单独开启
- **不可变设计**：订单号与金额使用 `final` 修饰，创建后不可修改；订单号作为数据主键，不可变性尤为重要
- **分页查询**：列表接口支持 `page` / `size` 参数，由数据库执行 `LIMIT ? OFFSET ?`，
  只取需要的那一页，避免把全表读进内存。分页参数在业务层校验（页码 ≥ 1、每页 1~100），
  并对 `size` 设上限，防止客户端用超大 size 变相拉走全表。
  返回结构为通用的 `PageResponse<T>`（items / total / page / size / totalPages），
  并通过 `map()` 把业务对象整体转换为对外 DTO
- **订单明细（1:N）与事务**：订单与明细分两张表。创建订单需要写两张表，
  用 `@Transactional` 保证原子性 —— 明细写入失败时，已插入的订单会一起回滚，
  不会留下"没有明细的半截订单"。实测：去掉注解后该测试立刻失败，
  订单 `A001 | 金额 15.00` 残留但明细为空
- **金额由服务端计算**：客户端只能传「商品名 / 单价 / 数量」，小计与总价一律由服务端算，
  请求体里即使带了 `amount` 也会被忽略 —— 否则客户端可以把 1000 元改成 1 元
- **避免 N+1 查询**：列表接口返回 `OrderResponse`（不含明细），详情接口才返回
  `OrderDetailResponse`（含明细）。若列表也带明细，一页 10 条就要多查 10 次库
- **批量插入**：明细使用 `JdbcTemplate.batchUpdate` 一次提交，而不是循环单条插入
- **金额使用 `BigDecimal`**：货币不能用 `int`（存不了小数）或 `double`（二进制浮点无法精确表示十进制小数，
  `0.1 + 0.2` 会得到 `0.30000000000000004`）。数据库列使用 `DECIMAL(12,2)`，Java 侧全程 `BigDecimal`，
  并在创建时统一归一为两位小数（`setScale(2)`），保证存储与接口返回的金额格式一致。
  注意 `BigDecimal` 不可变，`add` 会返回新对象，必须接收返回值
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

- **创建订单的并发安全**：「先查询是否存在、再插入」同样存在窗口期，两个请求可能都通过检查，
  后到的那个会撞上数据库 `UNIQUE` 约束。因此捕获该异常并翻译为相同的业务错误，
  避免可预期的"订单号重复"变成 500 服务器错误：

  ```java
  try {
      repository.add(order);
  } catch (DuplicateKeyException e) {
      throw new IllegalArgumentException("订单号已存在：" + id);
  }
  ```

  实测（20 个并发请求创建同一订单号）：修复前 19 个返回 **500**，修复后 19 个返回 **400**。
