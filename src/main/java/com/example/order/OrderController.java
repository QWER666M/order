package com.example.order;

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
    public List<Order> list() {
        return service.listOrders();
    }

    /** POST /orders —— 创建订单，请求体形如 {"id":"A001","amount":100} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@RequestBody CreateOrderRequest request) {
        return service.createOrder(request.id(), request.amount());
    }

    /** 创建订单的请求体。record 是 Java 14+ 的"只装数据的类"，一行顶过去十几行 */
    record CreateOrderRequest(String id, int amount) {
    }

    // ================= 下面 4 个交给你写 =================
    //
    /** GET /orders/{id} —— 按订单号查一笔 */
@GetMapping("/{id}")
public Order getOne(@PathVariable String id) {
    Order order = service.findOrder(id);
    if (order == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在：" + id);
    }
    return order;
}
   /** POST /orders/{id}/pay —— 支付 */
@PostMapping("/{id}/pay")
public Map<String, Object> pay(@PathVariable String id) {
    if (!service.pay(id)) {                       // ← 填空1：调 service 的哪个方法？
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,                    // ← 填空2：状态冲突用哪个码？
                "支付失败：订单不存在或不是待支付状态");
    }
    return Map.of("message", "支付成功", "order", service.findOrder(id));
}
    
@PostMapping("/{id}/cancel")
public Map<String, Object> cancel(@PathVariable String id) {
    if (!service.cancel(id)) {                       // ← 填空1：调 service 的哪个方法？
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,                    // ← 填空2：状态冲突用哪个码？
                "取消失败：订单不存在或不是待取消状态");
    }
    return Map.of("message", "取消成功", "order", service.findOrder(id));
}
/** GET /orders/stats —— 按状态统计总金额 */
@GetMapping("/stats")
public Map<String, Integer> stats() {
    return Map.of(
            "unpaid", service.sumAmount(OrderStatus.UNPAID),
            "paid", service.sumAmount(OrderStatus.PAID),        // ← 填空3
            "cancelled", service.sumAmount(OrderStatus.CANCELLED)    // ← 填空4
    );
}



}
