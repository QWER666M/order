package com.example.order;

/**
 * 用户实体。
 *
 * ★ passwordHash 存的是 BCrypt 哈希，永不放明文。
 *   BCrypt 输出固定 60 字符，自带随机盐：同一个密码每次哈希结果都不同，
 *   校验时用 encoder.matches(明文, 哈希)，无法从哈希反推原文。
 */
public class User {

    private final String username;
    private final String passwordHash;
    private final String role;          // USER / ADMIN

    public User(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    /** 只给 UserService 校验密码用，不要出现在任何出参 DTO 里 */
    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }
}
