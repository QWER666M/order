package com.example.order.dto;

/**
 * 登录/注册成功后的响应：只返回 token 和公开信息。
 *
 * ★ 绝对不能把 passwordHash 返回给客户端 ——
 *   这是"出参 DTO 只暴露必要字段"原则的典型例子。
 */
public record AuthResponse(String token, String username, String role) {
}
