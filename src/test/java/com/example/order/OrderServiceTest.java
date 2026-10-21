package com.example.order;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.example.order.dto.PageResponse;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        // ★ 顺序不能反：order_items 有外键指向 orders，必须先删明细，再删订单
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM users");
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

    // ================= 分页 =================

    /**
     * 分页最容易犯"差一错误"（off-by-one）：页数怎么算、第 2 页该从哪里开始，
     * 用具体数据把它钉死，以后改代码就能立刻发现有没有弄坏。
     */
    @Test
    void listOrders_pagination() {
        service.createOrder("A001", money("100"));
        service.createOrder("A002", money("100"));
        service.createOrder("A003", money("100"));

        // 第 1 页：前两条
        PageResponse<Order> page1 = service.listOrders(1, 2);
        assertEquals(2, page1.items().size());
        assertEquals("A001", page1.items().get(0).getId());
        assertEquals("A002", page1.items().get(1).getId());
        assertEquals(3, page1.total());
        assertEquals(2, page1.getTotalPages());          // 3 条 / 每页 2 条 = 2 页

        // 第 2 页：跳过前 2 条，从 A003 开始（最易写错的地方）
        PageResponse<Order> page2 = service.listOrders(2, 2);
        assertEquals(1, page2.items().size());
        assertEquals("A003", page2.items().get(0).getId());
        assertEquals(3, page2.total());

        // 页码超出范围：返回空页，而不是报错
        PageResponse<Order> far = service.listOrders(99, 2);
        assertTrue(far.items().isEmpty());
        assertEquals(3, far.total());
    }

    @Test
    void listOrders_invalidPagingParams_throws() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> service.listOrders(0, 10));
        assertEquals("页码必须大于等于 1", e1.getMessage());

        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> service.listOrders(1, 101));
        assertEquals("每页条数必须在 1 ~ 100 之间", e2.getMessage());
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

    // ================= 订单明细（1:N）与事务 =================

    private static OrderItem item(String name, String price, int qty) {
        return new OrderItem(name, new BigDecimal(price), qty);
    }

    /** 总价由服务端按明细计算：10.00*2 + 3.50*1 = 23.50 */
    @Test
    void createOrder_withItems_computesTotalOnServer() {
        Order order = service.createOrder("A001", null, List.of(
                item("苹果", "10.00", 2),
                item("香蕉", "3.50", 1)));

        assertAmount("23.50", order.getAmount());

        List<OrderItem> items = service.findOrderItems("A001");
        assertEquals(2, items.size());
        assertEquals("苹果", items.get(0).getProductName());
        assertAmount("20.00", items.get(0).getSubtotal());
        assertAmount("3.50", items.get(1).getSubtotal());
    }

    /**
     * 安全要点：客户端就算传了 amount=1.00，也无法影响总价。
     * 有明细时一律以服务端计算的金额为准。
     */
    @Test
    void createOrder_itemsTakePrecedenceOverClientAmount() {
        Order order = service.createOrder("A001", new BigDecimal("1.00"), List.of(
                item("苹果", "10.00", 2),
                item("香蕉", "3.50", 1)));

        assertAmount("23.50", order.getAmount());
    }

    @Test
    void createOrder_invalidItem_throws() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> service.createOrder("A001", null, List.of(item("  ", "10.00", 1))));
        assertEquals("商品名称不能为空", e1.getMessage());

        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> service.createOrder("A001", null, List.of(item("苹果", "0", 1))));
        assertEquals("商品单价必须大于 0", e2.getMessage());

        IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class,
                () -> service.createOrder("A001", null, List.of(item("苹果", "10.00", 0))));
        assertEquals("商品数量必须大于 0", e3.getMessage());
    }

    /**
     * ★ 事务回滚验证（这是 @Transactional 存在的理由）
     *
     * 第 1 条明细正常，第 2 条商品名超过 VARCHAR(128) -> 数据库拒绝。
     * 期望：整个方法回滚 —— 订单本身也不应留在库里，
     *       否则就会出现一张"没有明细的半个订单"（脏数据）。
     */
    @Test
    void createOrder_itemInsertFails_rollsBackOrder() {
        String tooLong = "x".repeat(200);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.createOrder("A001", null, List.of(
                        item("正常商品", "10.00", 1),
                        item(tooLong, "5.00", 1))));

        // 断言异常确实来自【数据库拒绝明细】，而不是前置校验（否则这个测试就白测了）
        assertEquals("明细数据不合法：商品名称过长或数值超出允许范围", e.getMessage());

        assertNull(service.findOrder("A001"), "订单应被回滚");
        assertTrue(service.findOrderItems("A001").isEmpty(), "明细也应被回滚");
    }

    // ================= 归属判断（fail-closed） =================

    /**
     * ★ isOwnedBy 的唯一正确行为：拿不到身份 = 谁的订单都不是你的。
     *
     * 旧实现写成 `if (ownerId == null) return true;`（"未开启鉴权就不做归属校验"），
     * 后果是：只要应用跑在鉴权关闭的配置下，任何人都能按订单号读任意订单。
     * 为了方便测试而放宽安全判断，代价就是这个。
     */
    @Test
    void isOwnedBy_nullIdentity_isAlwaysFalse() {
        service.createOrder("A001", money("100"), List.of(), "alice");

        assertFalse(service.isOwnedBy("A001", null), "没有身份时必须拒绝");
        assertTrue(service.isOwnedBy("A001", "alice"), "本人的订单应通过");
        assertFalse(service.isOwnedBy("A001", "bob"), "别人的订单必须拒绝");
        assertFalse(service.isOwnedBy("NOT-EXIST", "alice"), "不存在也返回 false");
    }

    // ================= 超时订单自动取消 =================
    /**
     * 只有「未支付 且 已超时」的订单会被取消。
     *
     * ★ 为什么必须手动改 created_at？
     *   createOrder 写入的是 CURRENT_TIMESTAMP（永远是"现在"），
     *   要造出"2 天前创建的订单"，只能直接改库。
     */
    @Test
    void cancelExpired_cancelsExpiredUnpaidOnly() throws Exception {
        service.createOrder("OLD", money("10"));
        service.createOrder("NEW", money("20"));

        jdbc.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(2)), "OLD");

        int cancelled = service.cancelExpiredOrders(24);

        assertEquals(1, cancelled, "只应取消 OLD 一笔");
        assertEquals(OrderStatus.CANCELLED, service.findOrder("OLD").getStatus(), "超时未支付的应被取消");
        assertEquals(OrderStatus.UNPAID, service.findOrder("NEW").getStatus(), "未超时的不能被误伤");
    }

    /**
     * ★ 这个测试比上一个重要得多。
     *
     * 如果 SQL 漏了 status = 'UNPAID'（只按时间筛），上一个测试仍然会绿
     * （OLD 本来就是未支付，取消它是对的），但这里会红：
     * 用户已经付过款的订单会被错误地改成「已取消」——钱收了，货不发。
     *
     * 这类事故没有任何报错，只有测试能提前拦住。
     */
    @Test
    void cancelExpired_doesNotTouchPaidOrders() throws Exception {
        service.createOrder("PAID-OLD", money("30"));
        assertTrue(service.pay("PAID-OLD"), "前置条件：订单应支付成功");

        jdbc.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(2)), "PAID-OLD");

        int cancelled = service.cancelExpiredOrders(24);

        assertEquals(0, cancelled, "已支付的订单不该被取消");
        assertEquals(OrderStatus.PAID, service.findOrder("PAID-OLD").getStatus(), "状态必须保持 PAID");
    }
}
