package com.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理超时未支付订单。
 *
 * ★ 只负责「什么时候跑」，不负责「怎么判断超时」——
 *   规则在 OrderService，所以手动触发、CLI 批量跑都能复用同一份逻辑。
 */
@Component
public class OrderCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OrderCleanupJob.class);

    private final OrderService orderService;
    private final int expireHours;

    public OrderCleanupJob(OrderService orderService,
                           @Value("${app.order.expire-hours:24}") int expireHours) {
        this.orderService = orderService;
        this.expireHours = expireHours;
    }

    /**
     * cron 从配置读取，测试环境设成 "-" 可完全禁用。
     * 格式：秒 分 时 日 月 周（注意比 Linux crontab 多一个"秒"）
     */
    @Scheduled(cron = "${app.order.cleanup-cron:0 */10 * * * *}", zone = "Asia/Shanghai")
    public void cancelExpiredOrders() {
        log.debug("开始扫描超时未支付订单（{} 小时）", expireHours);
        int cancelled = orderService.cancelExpiredOrders(expireHours);        // ← 填空
        if (cancelled > 0) {
            log.info("定时任务：自动取消 {} 笔超时未支付订单", cancelled);
        }
    }
}
