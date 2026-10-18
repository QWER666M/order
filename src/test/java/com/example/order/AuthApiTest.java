package com.example.order;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与授权的接口测试。
 *
 * ★ 这个类显式打开鉴权（其他测试类默认关闭）：
 *   @SpringBootTest(properties = "app.auth.enabled=true")
 *   这样既能验证真实的安全行为，又不影响既有的大量业务测试。
 */
@SpringBootTest(properties = "app.auth.enabled=true")
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM users");
    }

    // ================= 工具方法 =================

    private String register(String username) throws Exception {
        String body = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secret123\"}".formatted(username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                // ★ 响应里绝不能出现密码哈希
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ================= 注册与登录 =================

    @Test
    void register_thenLogin_success() throws Exception {
        register("alice");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void register_duplicateUsername_returns400() throws Exception {
        register("alice");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"another123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("用户名已存在：alice"));
    }

    /** 登录失败返回 401，且信息不区分"用户不存在"与"密码错误" */
    @Test
    void login_wrongPassword_returns401() throws Exception {
        register("alice");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("用户名或密码错误"));
    }

    @Test
    void login_unknownUser_returns401WithSameMessage() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("用户名或密码错误"));
    }

    // ================= 未登录访问 =================

    @Test
    void orders_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void orders_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/orders").header("Authorization", "Bearer this.is.not.a.valid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("登录已失效，请重新登录"));
    }

    /** 伪造的 token（改了一个字符）也过不了验签 */
    @Test
    void orders_withTamperedToken_returns401() throws Exception {
        String token = register("alice");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/orders").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orders_withToken_returns200() throws Exception {
        String token = register("alice");

        mockMvc.perform(get("/orders").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // ================= 授权：数据隔离 =================

    /**
     * ★ 核心用例：A 的订单，B 看不到也动不了。
     *   这是"认证"之外真正的"授权" —— 光能登录还不够，不能越权访问别人的数据。
     */
    @Test
    void userCannotSeeOrTouchAnotherUsersOrder() throws Exception {
        String aliceToken = register("alice");
        String bobToken = register("bob");

        // alice 创建订单
        mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"A-ALICE\",\"amount\":100.00}"))
                .andExpect(status().isCreated());

        // bob 的列表里看不到
        mockMvc.perform(get("/orders").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        // bob 查详情 -> 404（而不是 403，避免泄露"订单存在"）
        mockMvc.perform(get("/orders/A-ALICE").header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());

        // bob 想支付 -> 404
        mockMvc.perform(post("/orders/A-ALICE/pay").header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());

        // bob 想取消 -> 404
        mockMvc.perform(post("/orders/A-ALICE/cancel").header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());

        // alice 自己一切正常
        mockMvc.perform(get("/orders/A-ALICE").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("A-ALICE"));

        // 订单状态没被 bob 改掉
        mockMvc.perform(post("/orders/A-ALICE/pay").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk());
    }

    /** 统计接口也只统计自己的订单 */
    @Test
    void stats_onlyCountsCurrentUsersOrders() throws Exception {
        String aliceToken = register("alice");
        String bobToken = register("bob");

        mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"A-1\",\"amount\":100.00}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/orders/stats").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unpaid").value(100.00));

        mockMvc.perform(get("/orders/stats").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unpaid").value(0));
    }

    /** 未登录时 /auth/** 仍可访问（否则无法登录） */
    @Test
    void authEndpoints_arePublic() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());
    }
}
