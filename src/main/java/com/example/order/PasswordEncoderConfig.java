package com.example.order;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 只注册一个密码编码器 Bean，不引入 Spring Security 的整套过滤器链
 * （否则所有接口会被默认保护，需要额外配置才能放开，对学习阶段是干扰）。
 *
 * 为什么用 BCrypt 而不是 MD5 / SHA-256？
 *   1) MD5/SHA 是"快"的哈希 —— 快正适合暴力破解，GPU 每秒能算几十亿次；
 *      BCrypt 故意设计得慢（可调成本因子），暴力破解成本极高。
 *   2) MD5/SHA 不加盐时，同样的密码哈希结果相同，可被彩虹表直接反查；
 *      BCrypt 每次自动加随机盐，同一密码两次结果也不同。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
