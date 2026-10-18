package com.example.order;

import com.example.order.dto.AuthResponse;
import com.example.order.dto.LoginRequest;
import com.example.order.dto.RegisterRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（不需要 token）。
 *
 * 为什么 /auth/** 不被拦截器保护？
 *   登录/注册本身就是"获取 token"的过程，如果也要求 token，就成了死循环。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService service;

    public AuthController(UserService service) {
        this.service = service;
    }

    /** POST /auth/register —— 注册并直接返回 token */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return service.register(request.username(), request.password());
    }

    /** POST /auth/login —— 登录，失败返回 401 */
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return service.login(request.username(), request.password());
    }
}
