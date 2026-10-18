package com.example.order;

/**
 * 角色（权限模型的最小单位）。
 *
 * ★ 认证（Authentication）vs 授权（Authorization），面试必问，别混：
 *   认证 = "你是谁"     -> 由 JWT 验签回答，失败返回 401
 *   授权 = "你能做什么" -> 由角色 + 资源归属回答，失败返回 403 或 404
 *
 * 状态码语义（本项目遵守）：
 *   401 未登录 / token 无效            —— 不知道你是谁
 *   403 已登录但角色不够                —— 知道你是谁，但这条路径你不该进
 *   404 已登录但资源不属于你            —— 知道你是谁，但不告诉你资源存不存在（防枚举）
 */
public enum Role {

    USER,
    ADMIN;

    /**
     * 把数据库里的字符串转成枚举。
     *
     * ★ 未知值/空值一律降级为 USER —— 权限判断必须"默认最小权限"（fail-safe）：
     *   数据脏了、写错了，宁可少给权限，也绝不能意外提权。
     */
    public static Role from(String raw) {
        if (raw == null) {
            return USER;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
