package com.example.order;

import com.example.order.dto.AuthResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户业务：注册与登录。
 *
 * 这一层负责"密码怎么处理、用户名是否合法"，数据层只管存取。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public UserService(UserRepository repository, PasswordEncoder encoder, JwtService jwt) {
        this.repository = repository;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** 注册：校验用户名/密码 -> 哈希密码 -> 入库 -> 直接签发 token */
    public AuthResponse register(String username, String rawPassword) {
        validateUsername(username);
        validatePassword(rawPassword);

        if (repository.contains(username)) {
            throw new IllegalArgumentException("用户名已存在：" + username);
        }

        String hash = encoder.encode(rawPassword);      // BCrypt 哈希（自带随机盐）
        try {
            repository.add(new User(username, hash, Role.USER.name()));
        } catch (DuplicateKeyException e) {
            // 并发兜底：contains 与 insert 之间有窗口期，靠数据库唯一约束最终拦截
            throw new IllegalArgumentException("用户名已存在：" + username);
        }

        log.info("用户注册成功: username={}", username);
        // ★ 注册一律是 USER。管理员只能通过数据库/运维手段授予，
        //   绝不接受客户端在注册时自己指定 role —— 否则任何人都能注册成 ADMIN。
        return new AuthResponse(jwt.createToken(username, Role.USER.name()), username, Role.USER.name());
    }

    /** 登录：校验密码 -> 签发 token */
    public AuthResponse login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null) {
            throw new LoginFailedException("用户名或密码错误");
        }

        User user = repository.findByUsername(username);

        // ★ 安全要点：用户不存在和密码错误返回【同一句话】。
        //   如果分别提示"用户不存在"/"密码错误"，等于帮攻击者确认哪些用户名是有效的（用户枚举）。
        if (user == null || !encoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("登录失败: username={}", username);
            throw new LoginFailedException("用户名或密码错误");
        }

        log.info("用户登录成功: username={}", username);
        return new AuthResponse(jwt.createToken(user.getUsername(), user.getRole()),
                user.getUsername(), user.getRole());
    }

    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 32) {
            throw new IllegalArgumentException("用户名长度必须在 3 ~ 32 之间");
        }
        if (!username.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("用户名只能包含字母、数字和下划线");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
    }
}
