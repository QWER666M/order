package com.example.order;

import java.math.BigDecimal;

/**
 * 订单实体：只负责"一笔订单长什么样"。
 * 字段用 private，外部通过 getter/setter 访问。
 *
 * ★ 金额为什么用 BigDecimal，而不是 int / double？
 *   - int    ：存不了小数（金额常有"分"，如 99.99）
 *   - double ：二进制浮点无法精确表示十进制小数，0.1 + 0.2 != 0.3，
 *              钱算错了是事故，所以货币一律用精确十进制
 *   - BigDecimal：精确十进制 + 不可变（每次运算返回【新对象】，原值不变）
 */
public class Order {

    private final String id;
    private final BigDecimal amount;
    private OrderStatus status;

    public Order(String id, BigDecimal amount, OrderStatus status) {
        this.id = id;
        this.amount = amount;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public BigDecimal getAmount() {
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
