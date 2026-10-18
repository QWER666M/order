package com.example.order;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 数据层：只负责"订单怎么存、怎么取"。
 *
 * ★ 变化说明：以前用内存 Map，现在换成 H2 数据库（通过 JdbcTemplate 写 SQL）。
 *   关键的是 —— OrderService / OrderController / Order 全都不用改
 *   （除了 pay/cancel 改成了原子条件更新），
 *   因为"数据存在哪"只有这一层知道。
 */
@Repository
public class OrderRepository {

    /** 把数据库的一行，变成一个 Order 对象。（每一列 → 构造方法的参数） */
    private static final RowMapper<Order> TO_ORDER = (rs, rowNum) -> new Order(
            rs.getString("id"),
            rs.getBigDecimal("amount"),                 // 金额用 getBigDecimal，与 DECIMAL 列对应
            OrderStatus.valueOf(rs.getString("status"))
    );

    /** 把明细表的一行，变成一个 OrderItem 对象 */
    private static final RowMapper<OrderItem> TO_ITEM = (rs, rowNum) -> new OrderItem(
            rs.getString("product_name"),
            rs.getBigDecimal("price"),
            rs.getInt("quantity")
    );

    private final JdbcTemplate jdbc;

    // Spring 会自动把 JdbcTemplate 递进来（它由 spring-boot-starter-jdbc 提供）
    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 存一笔新订单（INSERT） */
    public void add(Order order) {
        jdbc.update("INSERT INTO orders (id, amount, status) VALUES (?, ?, ?)",
                order.getId(),
                order.getAmount(),
                order.getStatus().name());
    }

    /**
     * 原子地把「待支付」订单改成新状态，返回是否成功（并发安全）。
     *
     * 关键：把"当前必须是 UNPAID"这个条件写进 UPDATE 的 WHERE 里，
     * 由数据库在【一次操作内】完成"判断 + 修改"，不再有"先查后改"的窗口期。
     * 返回受影响行数：1 = 我抢到了；0 = 订单不存在，或已被别人改成别的状态。
     */
    public boolean updateStatusIfUnpaid(String id, OrderStatus newStatus) {
        int rows = jdbc.update("UPDATE orders SET status = ? WHERE id = ? AND status = ?",
                newStatus.name(),
                id,
                OrderStatus.UNPAID.name());
        return rows == 1;
    }

    /** 按订单号取一笔；没有就返回 null */
    public Order get(String id) {
        List<Order> found = jdbc.query(
                "SELECT id, amount, status FROM orders WHERE id = ?", TO_ORDER, id);
        return found.isEmpty() ? null : found.get(0);
    }

    /** 取全部订单，按插入顺序（seq 是自增主键，等价于原来 LinkedHashMap 的顺序） */
    public List<Order> listAll() {
        return jdbc.query("SELECT id, amount, status FROM orders ORDER BY seq", TO_ORDER);
    }

    /**
     * 分页查询：跳过 offset 条，取 limit 条。
     *
     * SQL 里的 LIMIT / OFFSET 由数据库完成"只取一部分"，不会把全表读进内存，
     * 这正是分页能省内存的关键 —— 不是查出来再截取。
     * （H2 与 MySQL、PostgreSQL 都支持 LIMIT ? OFFSET ? 这种写法。）
     */
    public List<Order> listPage(int offset, int limit) {
        return jdbc.query(
                "SELECT id, amount, status FROM orders ORDER BY seq LIMIT ? OFFSET ?",
                TO_ORDER, limit, offset);
    }

    /** 一共多少笔（用 long：记录数可能超过 int 上限） */
    public long count() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return total == null ? 0L : total;
    }

    /** 订单号是否已存在 */
    public boolean contains(String id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    /**
     * 批量插入订单明细。
     *
     * ★ 为什么用 batchUpdate 而不是循环 update？
     *   循环调用 = n 次"应用 → 数据库"的网络往返；batchUpdate 会攒成一批发送，
     *   由驱动/数据库一次处理，明细多时差距很明显。
     *   参数用 List<Object[]>：每个数组就是一条 SQL 的 ? 参数。
     */
    public void addItems(String orderId, List<OrderItem> items) {
        List<Object[]> args = items.stream()
                .map(item -> new Object[]{
                        orderId,
                        item.getProductName(),
                        item.getPrice(),
                        item.getQuantity()
                })
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO order_items (order_id, product_name, price, quantity) VALUES (?, ?, ?, ?)",
                args);
    }

    /** 按订单号取明细，按插入顺序返回 */
    public List<OrderItem> findItems(String orderId) {
        return jdbc.query(
                "SELECT product_name, price, quantity FROM order_items WHERE order_id = ? ORDER BY seq",
                TO_ITEM, orderId);
    }

    /** 删除某张订单的明细（测试清库时用；有外键，必须先删明细再删订单） */
    public void deleteItems(String orderId) {
        jdbc.update("DELETE FROM order_items WHERE order_id = ?", orderId);
    }
}
