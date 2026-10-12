package com.example.order;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 业务层：只负责"订单的业务规则"。
 * 比如：订单号不能重复、金额必须为正、只有待支付订单才能取消。
 *
 * 日志约定（真实项目的常见做法）：
 *   - 状态变更成功 -> info（能看出"谁在什么时候做了什么"）
 *   - 业务失败     -> warn（可预期，但要能追溯原因）
 *   - 未预期异常   -> error + 完整堆栈（在 ApiExceptionHandler 里统一处理）
 *   参数用 {} 占位符而不是字符串拼接，避免"没开日志也白拼字符串"的性能损耗。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(String id, BigDecimal amount) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("金额最多支持两位小数");
        }
        if (repository.contains(id)) {
            throw new IllegalArgumentException("订单号已存在：" + id);
        }

        // 统一精度：不管入参是 100 还是 100.5，统一存成两位小数（100.00 / 100.50），
        // 这样数据库里、接口返回的 JSON 里的金额格式始终一致。
        // setScale(2) 在需要"舍入"时会抛异常 —— 上面已经拦掉了超过两位小数的入参。
        BigDecimal normalized = amount.setScale(2);

        Order order = new Order(id, normalized, OrderStatus.UNPAID);
        try {
            repository.add(order);
        } catch (DuplicateKeyException e) {
            // 并发兜底：上面的 contains 检查与这里的插入之间存在窗口期，
            // 两个请求可能都通过了检查，后到的那个会在数据库唯一约束上失败。
            // 把它翻译成同样的业务错误，而不是让数据库异常冒出去变成 500。
            log.warn("创建订单时发生订单号冲突（并发）: id={}", id);
            throw new IllegalArgumentException("订单号已存在：" + id);
        }
        log.info("创建订单成功: id={}, amount={}", id, amount);
        return order;
    }

    public Order findOrder(String id) {
        return repository.get(id);
    }

    public List<Order> listOrders() {
        return repository.listAll();
    }

    /**
     * 支付：只有待支付订单能支付。
     *
     * 并发安全由数据层的原子条件更新保证 —— 不再"先查状态、再判断、再更新"，
     * 因为那三步之间存在窗口期，并发时可能两个请求都判定为"可以支付"。
     */
    public boolean pay(String id) {
        boolean success = repository.updateStatusIfUnpaid(id, OrderStatus.PAID);
        if (success) {
            log.info("订单支付成功: id={}", id);
        } else {
            log.warn("订单支付失败（订单不存在或状态不是待支付）: id={}", id);
        }
        return success;
    }

    /** 取消：只有待支付订单能取消。 */
    public boolean cancel(String id) {
        boolean success = repository.updateStatusIfUnpaid(id, OrderStatus.CANCELLED);
        if (success) {
            log.info("订单取消成功: id={}", id);
        } else {
            log.warn("订单取消失败（订单不存在或状态不是待支付）: id={}", id);
        }
        return success;
    }

    /** 统计某个状态下的订单总金额。 */
    public BigDecimal sumAmount(OrderStatus status) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Order order : repository.listAll()) {
            if (order.getStatus() == status) {
                // ★ BigDecimal 不可变：add 返回新对象，必须接收返回值！
                //   写成 sum.add(...) 会静默失效（sum 还是 0），是极常见的低级错误
                sum = sum.add(order.getAmount());
            }
        }
        return sum;
    }
}
