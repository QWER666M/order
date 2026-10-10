package com.example.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrderServiceTest {

    // 不再手动 new，而是让 Spring 把造好的对象递进来
    @Autowired
    private OrderService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // 每个测试方法跑之前都清空表 —— 保证每个测试都从"空数据库"开始，互不干扰
        jdbc.update("DELETE FROM orders");
    }

    // ================= 例子 1：正常情况 =================
    @Test
    void createOrder_success() {
        // Arrange
        String id = "A001";
        int amount = 100;

        // Act
        Order order = service.createOrder(id, amount);

        // Assert
        assertEquals("A001", order.getId());
        assertEquals(100, order.getAmount());
        assertEquals(OrderStatus.UNPAID, order.getStatus());
    }

    // ================= 例子 2：该抛异常的情况 =================
    @Test
    void createOrder_amountNotPositive_throws() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> service.createOrder("A001", 0)   // 这段"应该"抛异常
        );
        assertEquals("金额必须大于 0", e.getMessage());
    }

    // ================= 下面 4 个你自己写 =================
    @Test
void createOrder_idDuplicate_throws() {
    service.createOrder("A001", 100);          // 先建一次，成功

    IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> service.createOrder("A001", 200)   // ← 填空1：再建一次"同一个订单号"
    );
    assertTrue(e.getMessage().contains("订单号已存在"));
}
 @Test
void createOrder_idEmpty_throws() {
    IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> service.createOrder("", 100)   // ← 订单号传空字符串
    );
    assertEquals("订单号不能为空", e.getMessage());   // ← 期望的那句错误信息
}
    

   @Test
void pay_unpaidOrder_success() {
    service.createOrder("A001", 100);           // 建一个待支付订单

    boolean result = service.pay("A001");       // 支付

    assertTrue(result);                         // 断言1：返回 true
    assertEquals(OrderStatus.PAID, service.findOrder("A001").getStatus());  // ← 填空3：付完应该是什么状态？
}
  @Test
void pay_orderNotFound_returnsFalse() {
    boolean result = service.pay("A002");         // ← 填空4：支付一个仓库里没有的订单号
    assertFalse(result);
}
}