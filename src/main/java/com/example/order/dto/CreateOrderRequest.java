package com.example.order.dto;

import java.math.BigDecimal;
import java.util.List;

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
public record CreateOrderRequest(String id, BigDecimal amount, List<CreateOrderItemRequest> items) {

    /**
     * 两种创建方式，二选一（服务端以 items 优先）：
     *
     *   1) 带明细（推荐，新用法）：
     *      {
     *        "id": "A001",
     *        "items": [
     *          { "productName": "苹果", "price": 10.00, "quantity": 2 },
     *          { "productName": "香蕉", "price": 3.50,  "quantity": 1 }
     *        ]
     *      }
     *      -> 服务端计算总价 = 10.00*2 + 3.50*1 = 23.50，客户端传的 amount 会被忽略
     *
     *   2) 只给总额（兼容旧用法）：
     *      { "id": "A002", "amount": 100.00 }
     *
     * 为什么 items 可以为 null？因为老客户端只传 amount，JSON 里没有 items 字段时
     * Jackson 会把它设成 null —— 这是向后兼容，不是"允许不传"。
     */
}
