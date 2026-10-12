package com.example.order;

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
        jdbc.update("DELETE FROM orders");
    }

    // ================= 创建订单 POST /orders =================

    @Test
    void create_returns201AndBody() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"A001\",\"amount\":100}"))
                .andExpect(status().isCreated())               // 201
                .andExpect(jsonPath("$.id").value("A001"))
                .andExpect(jsonPath("$.amount").value(100))
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
        service.createOrder("A001", 100);

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
        service.createOrder("A001", 100);

        mockMvc.perform(get("/orders/A001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("A001"))
                .andExpect(jsonPath("$.amount").value(100))
                .andExpect(jsonPath("$.status").value("UNPAID"));
    }

    @Test
    void getOne_notFound_returns404WithUnifiedError() throws Exception {
        mockMvc.perform(get("/orders/NOT-EXIST"))
                .andExpect(status().isNotFound())              // 404
                .andExpect(jsonPath("$.error").value("订单不存在：NOT-EXIST"));   // 统一错误格式
    }

    // ================= 查询全部 GET /orders =================

    @Test
    void list_returnsArray() throws Exception {
        service.createOrder("A001", 100);
        service.createOrder("A002", 200);

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("A001"));   // 按插入顺序
    }

    @Test
    void list_empty_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ================= 支付 POST /orders/{id}/pay =================

    @Test
    void pay_success_returns200() throws Exception {
        service.createOrder("A001", 100);

        mockMvc.perform(post("/orders/A001/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("支付成功"))
                .andExpect(jsonPath("$.order.status").value("PAID"));
    }

    @Test
    void pay_alreadyPaid_returns409() throws Exception {
        service.createOrder("A001", 100);
        service.pay("A001");

        mockMvc.perform(post("/orders/A001/pay"))
                .andExpect(status().isConflict())              // 409
                .andExpect(jsonPath("$.error").value("支付失败：订单不存在或不是待支付状态"));
    }

    @Test
    void pay_orderNotFound_returns409() throws Exception {
        mockMvc.perform(post("/orders/NOT-EXIST/pay"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    // ================= 取消 POST /orders/{id}/cancel =================

    @Test
    void cancel_success_returns200() throws Exception {
        service.createOrder("A001", 100);

        mockMvc.perform(post("/orders/A001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("取消成功"))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    // ================= 统计 GET /orders/stats =================

    @Test
    void stats_returnsTotalsByStatus() throws Exception {
        service.createOrder("A001", 100);
        service.createOrder("A002", 200);
        service.pay("A001");
        service.createOrder("A003", 50);
        service.cancel("A003");

        mockMvc.perform(get("/orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(100))
                .andExpect(jsonPath("$.unpaid").value(200))
                .andExpect(jsonPath("$.cancelled").value(50));
    }
}
