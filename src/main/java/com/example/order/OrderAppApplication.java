package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web 版程序入口。
 *
 * 对比一下两个入口：
 *   - Main.java            ：控制台版，自己 new OrderService(new OrderRepository())
 *   - OrderAppApplication  ：Web 版，对象由 Spring 创建并自动组装（依赖注入）
 *
 * 两边的 OrderService / OrderRepository 是同一份代码，一行都没改。
 */
@SpringBootApplication
public class OrderAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderAppApplication.class, args);
    }
}
