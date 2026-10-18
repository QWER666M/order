package com.example.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.example.order.dto.AdminUserResponse;
import com.example.order.dto.OrderDetailResponse;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理端接口：只有 ADMIN 能访问。
 *
 * ★ 权限怎么保证？
 *   1) 类上的 @RequireRole(Role.ADMIN) 让 RoleInterceptor 拦住所有非 ADMIN（403）
 *   2) 路径 /admin/** 已在 WebConfig 里被认证拦截器覆盖（未登录 401）
 *   所以这些方法【不需要】再写任何身份判断 —— 能进到这里的一定是管理员。
 *
 * ★ 为什么把管理接口放在 /admin 前缀下，而不是给 /orders 加个"管理员能看全部"的分支？
 *   1) 路径级保护可审计：一眼就能看出哪些接口是敏感的
 *   2) 契约稳定：/orders 永远是"我的订单"，含义不随角色变化，客户端不用猜
 *   3) 权限要求可见：@RequireRole 就写在类上，新增方法自动继承
 *
 * ★ 管理员能做什么、不能做什么（权限模型的核心不是"全开"）：
 *   ✅ 查看任意订单（审计、客服排查）
 *   ✅ 取消任意订单（运营止损）
 *   ❌ 代付（资金操作必须由订单所属人发起，管理员没有这个能力，也没有对应接口）
 *   即：读权限可以放宽，写权限要按业务性质分级。
 */
@RestController
@RequestMapping("/admin")
@RequireRole(Role.ADMIN)
public class AdminController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    public AdminController(OrderService orderService, UserRepository userRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    /**
     * GET /admin/orders?page=1&size=10&ownerId=alice
     *
     * 与 GET /orders 的区别：这里【不按当前用户过滤】，默认返回全部人的订单。
     * ownerId 为可选筛选条件（传了就只看这个人的）。
     */
    @GetMapping("/orders")
    public PageResponse<OrderResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String ownerId) {

        // 空字符串按"不筛选"处理，避免 ?ownerId= 变成一个查不到任何数据的条件
        String filter = (ownerId == null || ownerId.isBlank()) ? null : ownerId;
        return orderService.listOrders(page, size, filter).map(OrderResponse::from);
    }

    /** GET /admin/orders/{id} —— 查看任意订单详情（含明细） */
    @GetMapping("/orders/{id}")
    public OrderDetailResponse getOne(@PathVariable String id) {
        Order order = orderService.findOrder(id);
        if (order == null) {
            // 这里用 404 是"资源真的不存在"，与"权限不够"无关（权限不够在拦截器就已返回 403）
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在：" + id);
        }
        return OrderDetailResponse.from(order, orderService.findOrderItems(id));
    }

    /**
     * POST /admin/orders/{id}/cancel —— 取消任意订单（运营场景：用户下单后要求人工撤单）。
     *
     * 状态规则与用户侧完全一致：只有 UNPAID 能取消，否则 409。
     * ★ 管理员身份不改变业务规则 —— 权限是"能对谁做"，不是"能跳过规则"。
     */
    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        if (!orderService.cancel(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "取消失败：订单不存在或不是待取消状态");
        }
        return Map.of("message", "取消成功", "order", OrderResponse.from(orderService.findOrder(id)));
    }

    /** GET /admin/users —— 用户列表（只返回用户名/角色/创建时间，绝不含密码哈希） */
    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return userRepository.findAllSummaries();
    }

    /** GET /admin/stats —— 全部用户的金额统计（与 /orders/stats 的"只统计自己"形成对照） */
    @GetMapping("/stats")
    public Map<String, BigDecimal> stats() {
        // 传 null 表示不按归属人过滤 —— 复用同一段统计逻辑，不复制代码
        return Map.of(
                "unpaid", orderService.sumAmount(OrderStatus.UNPAID, null),
                "paid", orderService.sumAmount(OrderStatus.PAID, null),
                "cancelled", orderService.sumAmount(OrderStatus.CANCELLED, null)
        );
    }
}
