package com.example.order;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 鉴权拦截器：校验请求头里的 JWT，通过后把用户名放进 request 属性。
 *
 * 流程：
 *   1. 读 Authorization 头，格式必须是 "Bearer <token>"
 *   2. 验签 + 检查过期时间（JwtService 里做）
 *   3. 成功 -> request.setAttribute("currentUsername", 用户名)，后续 Controller 直接取
 *      失败 -> 返回 401
 *
 * ★ 为什么用拦截器而不是在每个 Controller 方法里写一遍校验？
 *   校验逻辑只写一处，新增接口自动受保护，不会漏。
 *
 * ★ 拦截器 vs 过滤器（面试常问）：
 *   过滤器（Filter）在 Servlet 层，包裹所有请求，看不到 Spring 的方法信息；
 *   拦截器（Interceptor）在 Spring MVC 层，能看到 handler（Controller 方法），
 *   能拿到方法上的注解，更适合做"接口级鉴权"。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Controller 通过 @RequestAttribute("currentUsername") 取这个值 */
    public static final String ATTR_USERNAME = "currentUsername";

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final JwtService jwt;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtService jwt, ObjectMapper objectMapper) {
        this.jwt = jwt;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录：请在 Authorization 头中提供 Bearer token");
            return false;
        }

        String token = header.substring("Bearer ".length()).trim();
        try {
            String username = jwt.parseUsername(token);
            request.setAttribute(ATTR_USERNAME, username);
            return true;                       // 放行
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不对 / 已过期 / 格式错误 —— 对外统一说"登录已失效"，
            // 不暴露具体是"签名错"还是"过期"，避免给攻击者反馈。
            log.warn("token 校验失败: {}", e.getMessage());
            writeUnauthorized(response, "登录已失效，请重新登录");
            return false;
        }
    }

    /** 手写 401 响应：拦截器里抛异常不会走到 @RestControllerAdvice，所以这里直接写 JSON */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), java.util.Map.of("error", message));
    }
}
