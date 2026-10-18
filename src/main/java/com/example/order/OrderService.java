package com.example.order;

import java.math.BigDecimal;
import java.util.List;

import com.example.order.dto.PageResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务层：只负责"订单的业务规则"。
 * 比如：订单号不能重复、金额必须为正、只有待支付订单才能取消。
 *
 * 日志约定（真实项目的常见做法）：
 *   - 状态变更成功 -> info（能看出"谁在什么时候做了什么"）
 *   - 业务失败     -> warn（可预期，但要能追溯原因）
 *   - 未预期异常   -> error + 完整堆栈（在 ApiExceptionHandler 里统一处理）
 *   参数用 {} 占位符而不是字符串拼接，避免"没开日志也白拼字符串"的性能损耗。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** 每页最多允许取多少条（防止客户端一次拉走全表） */
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * 创建订单（带明细）。
     *
     * ★★ 为什么要 @Transactional？
     *   一次业务操作要写【两张表】：orders 和 order_items。
     *   如果订单插入成功、明细插入失败，数据库里就留下一张"没有明细的空订单" —— 脏数据。
     *   @Transactional 保证"要么全成功，要么全失败"（原子性）：
     *   方法内抛出运行时异常时，Spring 会把本次事务内的所有写操作【全部回滚】。
     *
     * ★ 金额由服务端计算，不信任客户端传来的总价。
     *   客户端能改的只有"买什么、买几件"，改不了"该付多少钱"。
     *
     * ★ 注意 @Transactional 的一个经典坑（自调用失效）：
     *   如果同一个类里的 A 方法直接调用 B 方法，B 上的 @Transactional 【不会生效】，
     *   因为调用没有经过 Spring 生成的代理对象。所以下面两个重载方法都加了注解，
     *   保证从外部调用任意一个都能开启事务。
     */
    /** 兼容旧用法：不指定归属人（历史数据 / 未开启鉴权） */
    @Transactional
    public Order createOrder(String id, BigDecimal amount, List<OrderItem> items) {
        return createOrder(id, amount, items, null);
    }

    @Transactional
    public Order createOrder(String id, BigDecimal amount, List<OrderItem> items, String ownerId) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("订单号不能为空");
        }

        List<OrderItem> safeItems = (items == null) ? List.of() : items;
        BigDecimal total;

        if (safeItems.isEmpty()) {
            // 无明细：沿用原来的"直接指定总额"模式（兼容旧接口）
            total = validateAndNormalizeAmount(amount);
        } else {
            // 有明细：逐条校验，总价 = 各条小计之和
            total = BigDecimal.ZERO;
            for (OrderItem item : safeItems) {
                validateItem(item);
                total = total.add(item.getSubtotal());   // BigDecimal 不可变，必须接收返回值
            }
            total = total.setScale(2);
        }

        if (repository.contains(id)) {
            throw new IllegalArgumentException("订单号已存在：" + id);
        }

        Order order = new Order(id, total, OrderStatus.UNPAID, ownerId);
        try {
            repository.add(order);
            if (!safeItems.isEmpty()) {
                repository.addItems(id, safeItems);
            }
        } catch (DuplicateKeyException e) {
            // 并发兜底：contains 检查与插入之间存在窗口期
            log.warn("创建订单时发生订单号冲突（并发）: id={}", id);
            throw new IllegalArgumentException("订单号已存在：" + id);
        } catch (DataIntegrityViolationException e) {
            // 明细被数据库拒绝（例如商品名称超过列长度）。
            // 这里【不再】手工校验长度：让数据库作为唯一口径，避免两处硬编码不一致。
            // 抛出运行时异常 -> 事务回滚 -> 刚才插入的订单也会被撤销。
            log.warn("明细数据被数据库拒绝: id={}, reason={}", id, e.getMostSpecificCause().getMessage());
            throw new IllegalArgumentException("明细数据不合法：商品名称过长或数值超出允许范围");
        }

        log.info("创建订单成功: id={}, amount={}, items={}", id, total, safeItems.size());
        return order;
    }

    /** 兼容旧用法：只给总金额、不给明细 */
    @Transactional
    public Order createOrder(String id, BigDecimal amount) {
        return createOrder(id, amount, List.of());
    }

    /** 校验并归一化总额（两位小数） */
    private BigDecimal validateAndNormalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("金额最多支持两位小数");
        }
        // setScale(2) 在需要"舍入"时会抛异常 —— 上面已经拦掉了超过两位小数的入参
        return amount.setScale(2);
    }

    /** 校验单条明细 */
    private void validateItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("订单明细不能为空");
        }
        if (item.getProductName() == null || item.getProductName().trim().isEmpty()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("商品单价必须大于 0");
        }
        if (item.getPrice().scale() > 2) {
            throw new IllegalArgumentException("商品单价最多支持两位小数");
        }
        if (item.getQuantity() <= 0) {
            throw new IllegalArgumentException("商品数量必须大于 0");
        }
    }

    public Order findOrder(String id) {
        return repository.get(id);
    }

    /** 查询某张订单的全部明细 */
    public List<OrderItem> findOrderItems(String id) {
        return repository.findItems(id);
    }

    /**
     * 分页查询订单（page 从 1 开始）。
     *
     * 分页参数属于"业务约束"，所以校验放在业务层 —— 不论从哪个入口进来都受约束：
     *   - page 至少为 1
     *   - size 至少为 1，且不能超过上限（防止客户端用 size=999999 一次拉走全表，
     *     那等于没分页，还可能把内存撑爆）
     */
    public PageResponse<Order> listOrders(int page, int size) {
        return listOrders(page, size, null);
    }

    /**
     * 按归属人分页查询。
     *
     * ★ 授权要点：ownerId 由接口层从 JWT 中取出后传进来，【绝不】接受客户端自己传的用户名 ——
     *   否则客户端传别人的用户名就能查到别人的订单。
     */
    public PageResponse<Order> listOrders(int page, int size, String ownerId) {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于等于 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页条数必须在 1 ~ " + MAX_PAGE_SIZE + " 之间");
        }

        int offset = (page - 1) * size;          // 第 1 页从第 0 条开始；第 2 页跳过 size 条
        List<Order> items = (ownerId == null)
                ? repository.listPage(offset, size)
                : repository.listPage(offset, size, ownerId);
        long total = (ownerId == null) ? repository.count() : repository.count(ownerId);
        return new PageResponse<>(items, total, page, size);
    }

    /**
     * 判断某笔订单是否属于该用户。
     * ★ 订单不存在、或属于别人，都返回 false —— 接口层统一转成 404，
     *   这样不会泄露"这个订单号是存在的"（否则攻击者可以枚举订单号）。
     */
    public boolean isOwnedBy(String id, String ownerId) {
        if (ownerId == null) {
            return true;                        // 未开启鉴权（测试环境）时不做归属校验
        }
        Order order = repository.get(id);
        return order != null && ownerId.equals(order.getOwnerId());
    }

    /**
     * 支付：只有待支付订单能支付。
     *
     * 并发安全由数据层的原子条件更新保证 —— 不再"先查状态、再判断、再更新"，
     * 因为那三步之间存在窗口期，并发时可能两个请求都判定为"可以支付"。
     */
    public boolean pay(String id) {
        boolean success = repository.updateStatusIfUnpaid(id, OrderStatus.PAID);
        if (success) {
            log.info("订单支付成功: id={}", id);
        } else {
            log.warn("订单支付失败（订单不存在或状态不是待支付）: id={}", id);
        }
        return success;
    }

    /** 取消：只有待支付订单能取消。 */
    public boolean cancel(String id) {
        boolean success = repository.updateStatusIfUnpaid(id, OrderStatus.CANCELLED);
        if (success) {
            log.info("订单取消成功: id={}", id);
        } else {
            log.warn("订单取消失败（订单不存在或状态不是待支付）: id={}", id);
        }
        return success;
    }

    /** 统计某个状态下的订单总金额。 */
    public BigDecimal sumAmount(OrderStatus status) {
        return sumAmount(status, null);
    }

    /** 只统计该用户的订单金额 */
    public BigDecimal sumAmount(OrderStatus status, String ownerId) {
        BigDecimal sum = BigDecimal.ZERO;
        List<Order> orders = (ownerId == null) ? repository.listAll() : repository.listAll(ownerId);
        for (Order order : orders) {
            if (order.getStatus() == status) {
                // ★ BigDecimal 不可变：add 返回新对象，必须接收返回值！
                //   写成 sum.add(...) 会静默失效（sum 还是 0），是极常见的低级错误
                sum = sum.add(order.getAmount());
            }
        }
        return sum;
    }
}
