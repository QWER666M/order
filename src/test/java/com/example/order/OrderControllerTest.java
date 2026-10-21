package com.example.order;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口层（Controller）测试。
 *
 * 和 OrderServiceTest 的区别：
 *   OrderServiceTest  只测业务逻辑（直接调 Java 方法）
 *   本测试           测 HTTP 这一层 —— 状态码、JSON 字段名、错误格式、DTO 白名单
 *
 * 用 MockMvc：不需要真的启动 Tomcat 监听端口，就能模拟"发一个 HTTP 请求"。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // ★ 顺序不能反：order_items 有外键指向 orders，必须先删明细，再删订单
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM users");
    }

    /** 简写，让测试里的金额更像钱：money("100") */
    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /**
     * 关掉鉴权时，"我是谁"要由测试自己写进 request 属性（拦截器不会跑）。
     *
     * ★ 为什么必须这样做：
     *   OrderService.isOwnedBy 在 ownerId 为 null 时返回 false（默认拒绝）。
     *   所以"能读到自己的订单"这个测试，必须先明确身份、并让订单属于这个人。
     *   之前这两个前提都由一个宽松默认值（null -> 放行）兜着，
     *   测试能过，但测的其实是"没有身份也能读任何订单" —— 一个错误的行为。
     */
    private static final String OWNER = "alice";

    // ================= 创建订单 POST /orders =================

    @Test
    void create_returns201AndBody() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"A001\",\"amount\":100}"))
                .andExpect(status().isCreated())               // 201
                .andExpect(jsonPath("$.id").value("A001"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("UNPAID"));
    }

    @Test
    void create_idBlank_returns400() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"\",\"amount\":100}"))
                .andExpect(status().isBadRequest())            // 400
                .andExpect(jsonPath("$.error").value("订单号不能为空"));
    }

    @Test
    void create_amountNotPositive_returns400() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"A001\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("金额必须大于 0"));
    }

    @Test
    void create_idDuplicate_returns400() throws Exception {
        service.createOrder("A001", money("100"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"A001\",\"amount\":200}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("订单号已存在：A001"));
    }

    /**
     * DTO 白名单验证：客户端多传 status / seq，必须被忽略。
     * 若接口直接用实体接收请求体，status 就会被设成 PAID —— 等于绕过支付流程。
     */
    @Test
    void create_extraFieldsAreIgnored() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"A001\",\"amount\":100,\"status\":\"PAID\",\"seq\":9999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNPAID"));   // 仍是 UNPAID，没被篡改
    }

    // ================= 查询 GET /orders/{id} =================

    @Test
    void getOne_found_returns200() throws Exception {
        service.createOrder("A001", money("100"), List.of(), OWNER);

        mockMvc.perform(get("/orders/A001").requestAttr(AuthInterceptor.ATTR_USERNAME, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("A001"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("UNPAID"));
    }

    @Test
    void getOne_notFound_returns404WithUnifiedError() throws Exception {
        mockMvc.perform(get("/orders/NOT-EXIST"))
                .andExpect(status().isNotFound())              // 404
                .andExpect(jsonPath("$.error").value("订单不存在：NOT-EXIST"));   // 统一错误格式
    }

    // ================= 身份缺失时必须拒绝（fail-closed） =================

    /**
     * ★ 回归测试：没有身份时，绝不能读到任何订单。
     *
     * 这条用例在"isOwnedBy 遇到 null 就放行"的旧实现下会返回 200 —— 也就是说，
     * 只要鉴权被关掉（或将来某个配置失误），任何人不带身份就能读任意订单。
     * 现在 isOwnedBy 对 null 返回 false，这里必须是 404。
     */
    @Test
    void getOne_withoutIdentity_returns404() throws Exception {
        service.createOrder("A001", money("100"), List.of(), OWNER);

        // 故意不设置 currentUsername
        mockMvc.perform(get("/orders/A001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void items_withoutIdentity_returns404() throws Exception {
        service.createOrder("A001", money("20"),
                List.of(new OrderItem("apple", money("10"), 2)), OWNER);

        mockMvc.perform(get("/orders/A001/items"))
                .andExpect(status().isNotFound());
    }

    // ================= 分页查询 GET /orders?page=&size= =================

    @Test
    void list_returnsPageWithMetadata() throws Exception {
        service.createOrder("A001", money("100"));
        service.createOrder("A002", money("200"));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value("A001"))   // 按插入顺序
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))              // 默认每页 10 条
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void list_empty_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /** 分页真的分开了：3 条数据、每页 2 条，第 2 页只应返回 1 条。 */
    @Test
    void list_secondPage_returnsRemainingItems() throws Exception {
        service.createOrder("A001", money("100"));
        service.createOrder("A002", money("200"));
        service.createOrder("A003", money("300"));

        mockMvc.perform(get("/orders").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("A003"))   // 第 2 页只剩 A003
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));        // 3 条 / 每页 2 条 = 2 页
    }

    @Test
    void list_pageLessThanOne_returns400() throws Exception {
        mockMvc.perform(get("/orders").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("页码必须大于等于 1"));
    }

    /** 防止客户端用超大 size 一次拉走全表（那等于没分页，还可能撑爆内存）。 */
    @Test
    void list_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get("/orders").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("每页条数必须在 1 ~ 100 之间"));
    }

    @Test
    void list_sizeNotANumber_returns400() throws Exception {
        mockMvc.perform(get("/orders").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());   // 框架的参数类型转换失败，走统一错误格式
    }

    // ================= 支付 POST /orders/{id}/pay =================

    @Test
    void pay_success_returns200() throws Exception {
        service.createOrder("A001", money("100"), List.of(), OWNER);

        mockMvc.perform(post("/orders/A001/pay").requestAttr(AuthInterceptor.ATTR_USERNAME, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("支付成功"))
                .andExpect(jsonPath("$.order.status").value("PAID"));
    }

    @Test
    void pay_alreadyPaid_returns409() throws Exception {
        service.createOrder("A001", money("100"), List.of(), OWNER);
        service.pay("A001");

        mockMvc.perform(post("/orders/A001/pay").requestAttr(AuthInterceptor.ATTR_USERNAME, OWNER))
                .andExpect(status().isConflict())              // 409
                .andExpect(jsonPath("$.error").value("支付失败：订单不存在或不是待支付状态"));
    }

    /**
     * 支付一笔不存在的订单。
     *
     * ★ 行为变化：从前是 409（归属检查被宽松默认值放过，落到"状态冲突"），
     *   现在是 404 —— 因为订单不属于你（也根本不存在），与"查详情"的语义一致。
     *   好处是：不管订单是"不存在"还是"是别人的"，对外都是同一个 404，
     *   攻击者无法用状态码差异判断订单号是否存在。
     */
    @Test
    void pay_orderNotFound_returns404() throws Exception {
        mockMvc.perform(post("/orders/NOT-EXIST/pay").requestAttr(AuthInterceptor.ATTR_USERNAME, OWNER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("订单不存在：NOT-EXIST"));
    }

    // ================= 取消 POST /orders/{id}/cancel =================

    @Test
    void cancel_success_returns200() throws Exception {
        service.createOrder("A001", money("100"), List.of(), OWNER);

        mockMvc.perform(post("/orders/A001/cancel").requestAttr(AuthInterceptor.ATTR_USERNAME, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("取消成功"))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    // ================= 统计 GET /orders/stats =================

    @Test
    void stats_returnsTotalsByStatus() throws Exception {
        service.createOrder("A001", money("100"));
        service.createOrder("A002", money("200"));
        service.pay("A001");
        service.createOrder("A003", money("50"));
        service.cancel("A003");

        mockMvc.perform(get("/orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(100.00))
                .andExpect(jsonPath("$.unpaid").value(200.00))
                .andExpect(jsonPath("$.cancelled").value(50.00));
    }

    // ================= 订单明细（1:N）=================

    /** 带明细创建：返回的是服务端算出的总价 23.50，而不是客户端传的任何值 */
    @Test
    void create_withItems_returns201AndComputedAmount() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"A001","amount":1.00,"items":[
                                  {"productName":"苹果","price":10.00,"quantity":2},
                                  {"productName":"香蕉","price":3.50,"quantity":1}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("A001"))
                .andExpect(jsonPath("$.amount").value(23.50));
    }

    /** 详情接口返回明细（含服务端计算的小计） */
    @Test
    void getOne_returnsItems() throws Exception {
        service.createOrder("A001", null, List.of(
                new OrderItem("苹果", new BigDecimal("10.00"), 2),
                new OrderItem("香蕉", new BigDecimal("3.50"), 1)), OWNER);

        mockMvc.perform(get("/orders/A001").requestAttr(AuthInterceptor.ATTR_USERNAME, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(23.50))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value("苹果"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(20.00))
                .andExpect(jsonPath("$.items[1].subtotal").value(3.50));
    }

    /** 列表接口【不】返回明细 —— 避免 N+1 查询 */
    @Test
    void list_doesNotIncludeItems() throws Exception {
        service.createOrder("A001", null, List.of(
                new OrderItem("苹果", new BigDecimal("10.00"), 2)));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("A001"))
                .andExpect(jsonPath("$.items[0].items").doesNotExist());
    }
       @Test
    void items_found_returns200AndList() throws Exception {
        service.createOrder("A001", money("20"),
                List.of(new OrderItem("apple", money("10"), 2)), OWNER);

        mockMvc.perform(get("/orders/A001/items").requestAttr(AuthInterceptor.ATTR_USERNAME, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("apple"));
    }

    @Test
    void items_orderNotFound_returns404() throws Exception {
        mockMvc.perform(get("/orders/NOT-EXIST/items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("订单不存在：NOT-EXIST"));
    }
    @Test
    void items_ofOthersOrder_returns404() throws Exception {
        // 订单的主人是 alice（第 4 个参数）
        service.createOrder("A001", money("20"),
                List.of(new OrderItem("apple", money("10"), 2)), "alice");   // ①

        mockMvc.perform(get("/orders/A001/items")
                        .requestAttr("currentUsername", "bob"))                      // ② 装"我是谁"的属性名
                .andExpect(status().isNotFound());
    }

}
