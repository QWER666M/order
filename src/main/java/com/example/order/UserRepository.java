package com.example.order;

import java.util.List;

import com.example.order.dto.AdminUserResponse;

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

    /**
     * 管理端：列出全部用户（不含密码哈希）。
     *
     * ★ 显式只查三列，不用 SELECT * ——
     *   这样 password_hash 在【代码层面】就不可能被带出来，
     *   比"记得别返回它"可靠得多。列多的表尤其要注意这点。
     */
    public List<AdminUserResponse> findAllSummaries() {
        return jdbc.query(
                "SELECT username, role, created_at FROM users ORDER BY id",
                (rs, rowNum) -> new AdminUserResponse(
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
    }
}
