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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色与权限模型测试。
 *
 * 覆盖三类问题：
 *   1. 粗粒度：能不能进 /admin/**（401 未登录 / 403 角色不够）
 *   2. 细粒度：同一个订单，谁能看、谁能改（404 防枚举）
 *   3. 权限实时性：撤掉管理员后，手头的旧 token 是否立刻失效
 *
 * 本类显式打开鉴权（其他测试默认关闭）。
 */
@SpringBootTest(properties = "app.auth.enabled=true")
@AutoConfigureMockMvc
class AdminApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 顺序不能反：order_items 有外键指向 orders
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM users");
    }

    // ================= 工具方法 =================

    /** 注册并返回 token */
    private String register(String username) throws Exception {
        String body = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secret123\"}".formatted(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("token").asText();
    }

    /**
     * 注册后再把数据库里的角色改成 ADMIN，返回【注册时那个 token】。
     *
     * ★ 故意不做"重新登录拿新 token"：这样后面就能验证
     *   服务端授权到底看的是 token 里的声明，还是数据库里的真实角色。
     */
    private String adminToken(String username) throws Exception {
        String token = register(username);
        promote(username);
        return token;
    }

    private void promote(String username) {
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", username);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void createOrder(String token, String id, String amount) throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"%s\",\"amount\":%s}".formatted(id, amount)))
                .andExpect(status().isCreated());
    }

    private void createOrderWithItems(String token, String id) throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"id\":\"%s\",\"items\":["
                                + "{\"productName\":\"apple\",\"price\":10.00,\"quantity\":2}]}")
                                .formatted(id)))
                .andExpect(status().isCreated());
    }

    // ================= 1. 路径级：能不能进 /admin =================

    @Test
    void adminEndpoints_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void adminEndpoints_asNormalUser_returns403() throws Exception {
        String alice = register("alice");

        mockMvc.perform(get("/admin/orders").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(get("/admin/users").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/orders").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ================= 2. 资源级：管理员能看到什么 =================

    @Test
    void admin_seesAllUsersOrders() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        createOrder(alice, "A001", "10.00");
        createOrder(bob, "B001", "20.00");

        String admin = adminToken("root");

        mockMvc.perform(get("/admin/orders").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void admin_canFilterByOwnerId() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        createOrder(alice, "A001", "10.00");
        createOrder(bob, "B001", "20.00");

        String admin = adminToken("root");

        mockMvc.perform(get("/admin/orders").param("ownerId", "alice")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("A001"));
    }

    @Test
    void admin_canReadOthersOrderWithItems() throws Exception {
        String alice = register("alice");
        createOrderWithItems(alice, "A001");

        String admin = adminToken("root");

        mockMvc.perform(get("/admin/orders/A001").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("A001"))
                .andExpect(jsonPath("$.amount").value(20.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("apple"));
    }

    @Test
    void admin_canCancelOthersOrder() throws Exception {
        String alice = register("alice");
        createOrder(alice, "A001", "10.00");

        String admin = adminToken("root");

        mockMvc.perform(post("/admin/orders/A001/cancel").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    /** 管理员身份不改变业务规则：已支付的订单同样不能取消（409） */
    @Test
    void admin_cancelPaidOrder_stillReturns409() throws Exception {
        String alice = register("alice");
        createOrder(alice, "A001", "10.00");
        mockMvc.perform(post("/orders/A001/pay").header("Authorization", bearer(alice)))
                .andExpect(status().isOk());

        String admin = adminToken("root");

        mockMvc.perform(post("/admin/orders/A001/cancel").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    /** 管理员也不能"代付"：资金操作只能由订单所属人发起 */
    @Test
    void admin_cannotPayOthersOrder_returns404() throws Exception {
        String alice = register("alice");
        createOrder(alice, "A001", "10.00");

        String admin = adminToken("root");

        // /orders/** 永远是"我的订单"，管理员在这里也只是普通用户 -> 归属校验不通过 -> 404
        mockMvc.perform(post("/orders/A001/pay").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    /** 回归：普通用户依然看不到别人的订单（角色机制不能把原有隔离弄坏） */
    @Test
    void normalUser_stillCannotReadOthersOrder_returns404() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        createOrder(alice, "A001", "10.00");

        mockMvc.perform(get("/orders/A001").header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
    }

    // ================= 3. 出参安全 =================

    @Test
    void admin_users_neverExposePasswordHash() throws Exception {
        register("alice");
        String admin = adminToken("root");

        String body = mockMvc.perform(get("/admin/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("alice"), "应包含用户名: " + body);
        assertFalse(body.toLowerCase().contains("password"), "响应里不应出现密码相关字段: " + body);
    }

    @Test
    void admin_stats_coversAllUsers() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        createOrder(alice, "A001", "10.00");
        createOrder(bob, "B001", "20.00");
        mockMvc.perform(post("/orders/B001/pay").header("Authorization", bearer(bob)))
                .andExpect(status().isOk());

        String admin = adminToken("root");

        mockMvc.perform(get("/admin/stats").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unpaid").value(10.00))
                .andExpect(jsonPath("$.paid").value(20.00));
    }

    // ================= 4. 权限的"实时性" =================

    /**
     * ★ 本类最重要的一条：授权必须以数据库为准，不能信 token 里的 role 声明。
     *
     * token 是注册时签发的，里面写的是 USER；之后数据库把这个人提升为 ADMIN。
     * 同一个 token 立刻就能进管理接口 —— 说明服务端没读 token 里的角色。
     * 再把角色撤回去，同一个 token 立刻 403 —— 不需要等 token 过期，也不需要"强制下线"。
     */
    @Test
    void promotedAndDemotedAdmin_takesEffectImmediately() throws Exception {
        String token = register("root");          // token 里的 role 声明是 USER

        promote("root");
        mockMvc.perform(get("/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        jdbc.update("UPDATE users SET role = 'USER' WHERE username = 'root'");
        mockMvc.perform(get("/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    /** 用户被删除后，未过期的 token 也必须立刻失效（401） */
    @Test
    void deletedUser_tokenBecomesInvalid() throws Exception {
        String token = register("ghost");
        jdbc.update("DELETE FROM users WHERE username = ?", "ghost");

        mockMvc.perform(get("/orders").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }
}
