package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 程序入口（唯一的入口）。
 *
 * 这里只做一件事：启动 Spring Boot。
 * 之后对象都由 Spring 创建并自动组装（依赖注入），而不是手动 new：
 *   - @Service    -> 创建 OrderService，并把它需要的 OrderRepository 递进去
 *   - @Repository -> 创建 OrderRepository，并把它需要的 JdbcTemplate 递进去
 *   - @RestController -> 创建 OrderController，并把 OrderService 递进去
 *
 * 这套组装关系，无需在 main 里写任何 new。
 */
@SpringBootApplication
public class OrderAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderAppApplication.class, args);
    }
}
