package com.example.order.dto;

import com.example.order.Order;
import com.example.order.OrderStatus;

/**
 * 订单的【对外响应结构】（DTO = Data Transfer Object，数据传输对象）。
 *
 * 为什么不让 Controller 直接返回 Order 实体？
 *   1. 隔离：实体会随内部需求演化（可能新增 costPrice 等敏感字段），
 *      直接暴露实体等于把内部结构变成了对外契约 —— 表结构一改，API 就变了。
 *   2. 可控：这里明确声明"对外只暴露这三个字段"，新增字段不会被意外泄露。
 *   3. 可裁剪：不同接口可以有自己的 DTO（列表用简版、详情用全版）。
 *
 * 说明：当前 DTO 字段与实体恰好一致，是因为 Order 目前没有需要隐藏的字段；
 *       真正的价值在于【把"对外契约"和"内部模型"分离开】，为后续演化留出空间。
 */
public record OrderResponse(String id, int amount, OrderStatus status) {

    /** 实体 -> DTO。转换集中在这里，调用方不需要知道字段怎么对应。 */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
