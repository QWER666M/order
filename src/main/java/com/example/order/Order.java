package com.example.order;

/**
 * 订单实体：只负责"一笔订单长什么样"。
 * 字段用 private，外部通过 getter/setter 访问。
 */
public class Order {

    private final String id;
    private final int amount;
    private OrderStatus status;

    public Order(String id, int amount, OrderStatus status) {
        this.id = id;
        this.amount = amount;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public int getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    // 状态只能通过这个方法修改，外部不能直接赋值
    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return id + " | 金额 " + amount + " | 状态 " + status;
    }
}
