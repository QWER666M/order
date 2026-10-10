package com.example.order;

/**
 * 订单状态。用枚举表示，避免到处写 "未支付"/"已支付" 这样的字符串。
 */
public enum OrderStatus {
    UNPAID,
    PAID,
    CANCELLED
}
