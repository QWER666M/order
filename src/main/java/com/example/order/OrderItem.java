package com.example.order;

import java.math.BigDecimal;

/**
 * 订单明细：订单里的一行商品。
 *
 * 关系：一个 Order 对应多条 OrderItem（1:N）。
 * 这里刻意【不】在 Order 里放 List<OrderItem> 字段，原因有三：
 *   1) 列表接口不需要明细 —— 如果 Order 带着明细，查 10 条订单就要额外查 10 次明细
 *      （这就是著名的 N+1 查询问题）；
 *   2) 明细只在"查详情"时才需要，按需查询更省资源；
 *   3) 实体之间不互相持有，避免循环引用与序列化死循环。
 * 需要明细时，由 Service 调 repository.findItems(orderId) 显式取。
 */
public class OrderItem {

    private final String productName;
    private final BigDecimal price;
    private final int quantity;

    public OrderItem(String productName, BigDecimal price, int quantity) {
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    /**
     * 小计 = 单价 × 数量。
     *
     * ★ 金额出于安全考虑【必须由服务端计算】：
     *   如果让客户端自己传小计或总价，客户端就能把 1000 元改成 1 元。
     */
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2);
    }

    @Override
    public String toString() {
        return productName + " x" + quantity + " @ " + price + " = " + getSubtotal();
    }
}
