package com.example.order;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDetailResponse;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 接口层（Controller）：把 HTTP 请求翻译成对 OrderService 的调用。
 *
 * 它是整个服务对外的入口：
 *   HTTP 请求进来 -> 这里负责解析 -> 交给 OrderService 处理业务
 * 所以这里【不做任何业务判断】，规则全在 OrderService。
 *
 * 另一个约定：对外一律使用 dto 包里的 CreateOrderRequest / OrderResponse，
 * 不直接暴露 Order 实体 —— 实体是"内部模型"，DTO 才是"对外契约"。
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    // Spring 看到构造方法要一个 OrderService，会自动把它"递"进来
    public OrderController(OrderService service) {
        this.service = service;
    }

    /** GET /orders —— 分页查看订单，支持 ?page=1&size=10（都有默认值） */
    @GetMapping
    public PageResponse<OrderResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 先拿到"业务对象的一页"，再整体映射成"对外的一页"（PageResponse.map）
        return service.listOrders(page, size).map(OrderResponse::from);
    }

    /**
     * POST /orders —— 创建订单。
     *
     * 支持两种请求体：
     *   带明细：{"id":"A001","items":[{"productName":"苹果","price":10.00,"quantity":2}]}
     *   只给总额：{"id":"A002","amount":100.00}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        // DTO（对外契约）-> 实体（内部模型）
        List<OrderItem> items = (request.items() == null)
                ? List.of()
                : request.items().stream()
                        .map(i -> new OrderItem(i.productName(), i.price(), i.quantity()))
                        .toList();

        Order created = service.createOrder(request.id(), request.amount(), items);
        return OrderResponse.from(created);
    }

    /** GET /orders/{id} —— 查询订单详情（含明细）；查不到返回 404 */
    @GetMapping("/{id}")
    public OrderDetailResponse getOne(@PathVariable String id) {
        Order order = service.findOrder(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在：" + id);
        }
        // 明细只在这一层按需查询：列表接口不会为每条订单都查一次明细（避免 N+1）
        return OrderDetailResponse.from(order, service.findOrderItems(id));
    }

    /** POST /orders/{id}/pay —— 支付；失败返回 409（冲突） */
    @PostMapping("/{id}/pay")
    public Map<String, Object> pay(@PathVariable String id) {
        if (!service.pay(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "支付失败：订单不存在或不是待支付状态");
        }
        return Map.of("message", "支付成功", "order", OrderResponse.from(service.findOrder(id)));
    }

    /** POST /orders/{id}/cancel —— 取消；失败返回 409（冲突） */
    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        if (!service.cancel(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "取消失败：订单不存在或不是待取消状态");
        }
        return Map.of("message", "取消成功", "order", OrderResponse.from(service.findOrder(id)));
    }

    /** GET /orders/stats —— 按状态统计总金额 */
    @GetMapping("/stats")
    public Map<String, BigDecimal> stats() {
        return Map.of(
                "unpaid", service.sumAmount(OrderStatus.UNPAID),
                "paid", service.sumAmount(OrderStatus.PAID),
                "cancelled", service.sumAmount(OrderStatus.CANCELLED)
        );
    }
}
