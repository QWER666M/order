package com.example.order;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 业务层：只负责"订单的业务规则"。
 * 比如：订单号不能重复、金额必须为正、只有待支付订单才能取消。
 */
@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(String id, int amount) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        if (repository.contains(id)) {
            throw new IllegalArgumentException("订单号已存在：" + id);
        }

        Order order = new Order(id, amount, OrderStatus.UNPAID);
        try {
            repository.add(order);
        } catch (DuplicateKeyException e) {
            // 并发兜底：上面的 contains 检查与这里的插入之间存在窗口期，
            // 两个请求可能都通过了检查，后到的那个会在数据库唯一约束上失败。
            // 把它翻译成同样的业务错误，而不是让数据库异常冒出去变成 500。
            throw new IllegalArgumentException("订单号已存在：" + id);
        }
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
        return repository.updateStatusIfUnpaid(id, OrderStatus.PAID);
    }

    /** 取消：只有待支付订单能取消。 */
    public boolean cancel(String id) {
        return repository.updateStatusIfUnpaid(id, OrderStatus.CANCELLED);
    }

    /** 统计某个状态下的订单总金额。 */
    public int sumAmount(OrderStatus status) {
        int sum = 0;
        for (Order order : repository.listAll()) {
            if (order.getStatus() == status) {
                sum += order.getAmount();
            }
        }
        return sum;
    }
}
