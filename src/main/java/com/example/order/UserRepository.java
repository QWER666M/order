package com.example.order;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 用户数据层。
 *
 * 注意：这里没有任何"密码是否正确"的判断 —— 那是业务层的事。
 * 数据层只负责"把用户查出来 / 存进去"。
 */
@Repository
public class UserRepository {

    private static final RowMapper<User> TO_USER = (rs, rowNum) -> new User(
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("role")
    );

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(User user) {
        jdbc.update("INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole());
    }

    /** 按用户名查；不存在返回 null */
    public User findByUsername(String username) {
        List<User> found = jdbc.query(
                "SELECT username, password_hash, role FROM users WHERE username = ?",
                TO_USER, username);
        return found.isEmpty() ? null : found.get(0);
    }

    public boolean contains(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }
}
