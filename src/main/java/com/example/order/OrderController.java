package com.example.order;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 接口层（Controller）：把 HTTP 请求翻译成对 OrderService 的调用。
 *
 * 它和 Main.java 是同一层的东西 —— 都是"入口"，
 * 区别只是：Main 从键盘读输入，Controller 从 HTTP 请求读。
 * 所以这里也【不做任何业务判断】，规则全在 OrderService。
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

    /** GET /orders —— 查看全部订单 */
    @GetMapping
    public List<OrderResponse> list() {
        // stream：把每个 Order 逐个转成 OrderResponse，.toList() 收集成新的 List
        return service.listOrders().stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** POST /orders —— 创建订单，请求体形如 {"id":"A001","amount":100} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        Order created = service.createOrder(request.id(), request.amount());
        return OrderResponse.from(created);
    }

    /** GET /orders/{id} —— 按订单号查一笔；查不到返回 404 */
    @GetMapping("/{id}")
    public OrderResponse getOne(@PathVariable String id) {
        Order order = service.findOrder(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在：" + id);
        }
        return OrderResponse.from(order);
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
    public Map<String, Integer> stats() {
        return Map.of(
                "unpaid", service.sumAmount(OrderStatus.UNPAID),
                "paid", service.sumAmount(OrderStatus.PAID),
                "cancelled", service.sumAmount(OrderStatus.CANCELLED)
        );
    }
}
