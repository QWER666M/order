package com.example.order.dto;

import java.math.BigDecimal;
import java.util.List;

import com.example.order.Order;
import com.example.order.OrderItem;
import com.example.order.OrderStatus;

/**
 * 订单【详情】的出参结构：比列表多一个 items 字段。
 *
 * 为什么不复用 OrderResponse？
 *   列表接口一页可能 10~100 条订单。如果列表也带明细，就要为每条订单再查一次明细
 *   —— 这就是经典的 N+1 查询问题，数据库往返次数从 1 次变成 1+N 次。
 *   所以：列表用 OrderResponse（不含明细），详情用 OrderDetailResponse（含明细）。
 *
 * 这也是"不同场景用不同 DTO"最典型的例子。
 */
public record OrderDetailResponse(
        String id,
        BigDecimal amount,
        OrderStatus status,
        List<OrderItemResponse> items) {

    public static OrderDetailResponse from(Order order, List<OrderItem> items) {
        return new OrderDetailResponse(
                order.getId(),
                order.getAmount(),
                order.getStatus(),
                items.stream().map(OrderItemResponse::from).toList()
        );
    }
}
