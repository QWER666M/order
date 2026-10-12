package com.example.order;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 数据层：只负责"订单怎么存、怎么取"。
 *
 * ★ 变化说明：以前用内存 Map，现在换成 H2 数据库（通过 JdbcTemplate 写 SQL）。
 *   关键的是 —— OrderService / OrderController / Main 全都不用改（除了 pay/cancel 要多一句写回），
 *   因为"数据存在哪"只有这一层知道。
 */
@Repository
public class OrderRepository {

    /** 把数据库的一行，变成一个 Order 对象。（每一列 → 构造方法的参数） */
    private static final RowMapper<Order> TO_ORDER = (rs, rowNum) -> new Order(
            rs.getString("id"),
            rs.getInt("amount"),
            OrderStatus.valueOf(rs.getString("status"))
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

    /** 订单号是否已存在 */
    public boolean contains(String id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    /** 一共多少笔 */
    public int size() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        return count == null ? 0 : count;
    }
}
