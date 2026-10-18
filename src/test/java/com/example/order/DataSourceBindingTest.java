package com.example.order;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 数据源绑定校验。
 *
 * ★ 这个测试为什么必须存在？
 *
 *   CI 里用环境变量把测试配置从 H2 覆盖成 MySQL：
 *       SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/orderdb?...
 *
 *   但环境变量是"宽松绑定"的——名字写错一个下划线（例如写成
 *   SPRING_DATASOURCE_URI），Spring 不会报错，只会【静默忽略】。
 *   结果就是：测试照样在 H2 上全绿，流水线却报告"MySQL 通过"，
 *   实际上从头到尾没碰过 MySQL —— 这就是"流水线在说谎"。
 *
 *   所以这里主动断言"当前到底连的是谁"：
 *     - CI 设置 EXPECT_DB=mysql 时，连接串里必须出现 mysql
 *     - 本地不设该变量时自动跳过（默认跑 H2，不需要装数据库）
 */
@SpringBootTest
class DataSourceBindingTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsToExpectedDatabase() throws Exception {
        String expected = System.getenv("EXPECT_DB");
        assumeTrue(expected != null && !expected.isBlank(),
                "未设置 EXPECT_DB，跳过数据源校验（本地默认 H2）");

        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            assertTrue(url.toLowerCase().contains(expected.toLowerCase()),
                    "期望连接到 " + expected + "，实际连接串是：" + url);
        }
    }
}
