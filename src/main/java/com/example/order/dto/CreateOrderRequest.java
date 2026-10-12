package com.example.order.dto;

import java.math.BigDecimal;

/**
 * 创建订单的【请求体结构】（入参 DTO）。
 *
 * 放在 dto 包里，和 OrderResponse（出参 DTO）成对：
 *   入参 DTO  -> 外面传进来的数据长什么样
 *   出参 DTO  -> 我们返回给外面的数据长什么样
 *
 * record 是 Java 16+ 的特性，一行顶过去十几行（自动生成构造方法、getter 等）。
 * 取值用 request.id() / request.amount()（注意不是 getId()）。
 *
 * 金额类型是 BigDecimal：Jackson 会把 JSON 里的 100 / 100.5 / 100.55
 * 精确解析成 BigDecimal，不会经过 double 而丢失精度。
 */
public record CreateOrderRequest(String id, BigDecimal amount) {
}
