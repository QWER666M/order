package com.example.order.dto;

import java.math.BigDecimal;

import com.example.order.OrderItem;

/**
 * 明细的出参结构。
 *
 * 与入参的区别：多了 subtotal（小计）。
 * 小计由服务端计算后返回给前端，方便界面直接显示"这一行多少钱"，
 * 但客户端传进来的值不会被采用。
 */
public record OrderItemResponse(
        String productName,
        BigDecimal price,
        int quantity,
        BigDecimal subtotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProductName(),
                item.getPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
