package com.example.order.dto;

import java.time.LocalDateTime;

/**
 * 管理端用户列表项。
 *
 * ★ 只有三个字段 —— 没有 password_hash。
 *   出参 DTO 的意义不是"记得别返回密码"，而是"密码字段压根不存在"：
 *   写代码时就没有这个字段可返回，比靠自觉可靠得多。
 */
public record AdminUserResponse(String username, String role, LocalDateTime createdAt) {
}
