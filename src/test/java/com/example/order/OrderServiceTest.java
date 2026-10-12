package com.example.order;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 简写，让测试里的金额更像钱：money("100") */
    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /**
     * 断言金额相等。
     *
     * ★ 为什么不用 assertEquals？因为 BigDecimal 的 equals 会同时比较【数值】和【精度】：
     *       new BigDecimal("100").equals(new BigDecimal("100.00"))   ->  false ！
     *   而 compareTo 只比较数值大小，返回 0 表示相等：
     *       new BigDecimal("100").compareTo(new BigDecimal("100.00")) ->  0
     *   所以金额比较一律用 compareTo，不要用 equals。
     */
    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望金额 " + expected + "，实际 " + actual);
    }

    // ================= 正常情况 =================
    @Test
    void createOrder_success() {
        // Arrange
        String id = "A001";
        BigDecimal amount = new BigDecimal("100");

        // Act
        Order order = service.createOrder(id, amount);

        // Assert
        assertEquals("A001", order.getId());
        assertAmount("100.00", order.getAmount());   // 精度已统一为两位小数
        assertEquals(OrderStatus.UNPAID, order.getStatus());
    }

    /** 带小数的金额应能正常创建，且精度归一为两位小数。 */
    @Test
    void createOrder_decimalAmount_success() {
        Order order = service.createOrder("A001", money("99.5"));

        assertAmount("99.50", order.getAmount());    // 99.5 -> 99.50
    }

    // ================= 该抛异常的情况 =================
    @Test
    void createOrder_amountNotPositive_throws() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> service.createOrder("A001", BigDecimal.ZERO)
        );
        assertEquals("金额必须大于 0", e.getMessage());
    }

    /** 超过两位小数的金额应被拒绝 —— 钱最多精确到"分"。 */
    @Test
    void createOrder_amountTooManyDecimals_throws() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> service.createOrder("A001", new BigDecimal("0.001"))
        );
        assertEquals("金额最多支持两位小数", e.getMessage());
    }

    @Test
    void createOrder_idDuplicate_throws() {
        service.createOrder("A001", money("100"));

        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> service.createOrder("A001", money("200"))
        );
        assertTrue(e.getMessage().contains("订单号已存在"));
    }

    @Test
    void createOrder_idEmpty_throws() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> service.createOrder("", money("100"))
        );
        assertEquals("订单号不能为空", e.getMessage());
    }

    @Test
    void pay_unpaidOrder_success() {
        service.createOrder("A001", money("100"));

        boolean result = service.pay("A001");

        assertTrue(result);
        assertEquals(OrderStatus.PAID, service.findOrder("A001").getStatus());
    }

    @Test
    void pay_orderNotFound_returnsFalse() {
        boolean result = service.pay("A002");

        assertFalse(result);
    }

    @Test
    void sumAmount_sumsByStatus() {
        service.createOrder("A001", money("10.10"));
        service.createOrder("A002", money("20.20"));
        service.createOrder("A003", money("0.70"));
        service.pay("A002");

        assertAmount("20.20", service.sumAmount(OrderStatus.PAID));
        assertAmount("10.80", service.sumAmount(OrderStatus.UNPAID));   // 10.10 + 0.70
    }

    // ================= 并发安全测试 =================

    /**
     * 20 个线程【同时】支付同一笔订单，只能有 1 个成功。
     *
     * 用 CountDownLatch 当"发令枪"：所有线程先集合等待，一声令下同时冲，
     * 这样才能最大化冲突概率 —— 如果只是简单地依次调用，测不出并发问题。
     */
    @Test
    void pay_concurrentOnlyOneSucceeds() throws Exception {
        service.createOrder("A001", money("100"));

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);          // 发令枪
        CountDownLatch doneGate = new CountDownLatch(threads);     // 等所有线程跑完
        AtomicInteger successCount = new AtomicInteger();          // 线程安全的计数器

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();                 // 所有线程在这儿等发令
                    if (service.pay("A001")) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();                             // ★ 发令！20 个线程同时冲
        assertTrue(doneGate.await(10, TimeUnit.SECONDS), "线程未在 10 秒内跑完");
        pool.shutdown();

        assertEquals(1, successCount.get(), "同一笔订单只允许被支付成功一次");
        assertEquals(OrderStatus.PAID, service.findOrder("A001").getStatus());
    }

    /** 并发扣款/取消同理：20 个线程同时取消，只能有 1 个成功。 */
    @Test
    void cancel_concurrentOnlyOneSucceeds() throws Exception {
        service.createOrder("A001", money("100"));

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (service.cancel("A001")) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS), "线程未在 10 秒内跑完");
        pool.shutdown();

        assertEquals(1, successCount.get(), "同一笔订单只允许被取消一次");
        assertEquals(OrderStatus.CANCELLED, service.findOrder("A001").getStatus());
    }

    /**
     * 20 个线程【同时】创建同一个订单号，只能有 1 个成功。
     *
     * 这一条覆盖的是"先查后插"的窗口期：contains 检查与 INSERT 之间，
     * 两个请求可能都通过了检查，后到的那个会撞上数据库唯一约束。
     * 期望结果是：1 个成功，其余都是友好的 IllegalArgumentException，
     * 而不是数据库异常（那会变成 500，让用户以为系统故障）。
     */
    @Test
    void createOrder_concurrentSameId_onlyOneSucceeds() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    service.createOrder("SAME-ID", money("100"));
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    duplicateCount.incrementAndGet();       // 友好的业务错误
                } catch (RuntimeException e) {
                    unexpectedCount.incrementAndGet();      // 数据库异常等意外错误
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(15, TimeUnit.SECONDS), "线程未在 15 秒内跑完");
        pool.shutdown();

        assertEquals(1, successCount.get(), "同一个订单号只允许创建成功一次");
        assertEquals(0, unexpectedCount.get(), "不应出现数据库异常等意外错误（那会变成 500）");
        assertEquals(threads - 1, duplicateCount.get(), "其余请求应返回友好的业务错误");
    }
}