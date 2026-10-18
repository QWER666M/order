package com.example.order.dto;

import java.math.BigDecimal;

/**
 * 创建订单时，一条明细的入参结构。
 *
 * 注意这里【没有】小计（subtotal）字段，也【没有】总价字段。
 * 原因：金额必须由服务端根据 单价 × 数量 自己算。
 *      如果让客户端传小计/总价，客户端就能把 1000 元改成 1 元 —— 这是严重的安全问题。
 *
 * 客户端只能决定"买什么、单价多少、买几件"，不能决定"该付多少钱"。
 */
public record CreateOrderItemRequest(String productName, BigDecimal price, int quantity) {
}
