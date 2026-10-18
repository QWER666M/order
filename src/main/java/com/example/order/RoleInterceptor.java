package com.example.order;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 授权拦截器：按 @RequireRole 判断"当前角色够不够"。
 *
 * ★ 它必须排在 AuthInterceptor 之后执行 —— 因为要用后者写入 request 的 currentRole。
 *   注册顺序即执行顺序（见 WebConfig）。
 *
 * ★ 为什么"认证"和"授权"要拆成两个拦截器？
 *   认证对所有接口都一样（验 token），授权则因接口而异。
 *   拆开后：认证逻辑只有一份；授权的"要求"写在注解里，拦截器只负责读注解和比较。
 *   合在一起会变成"一个类里堆满 if"，新增接口就得改拦截器。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RoleInterceptor.class);

    private final ObjectMapper objectMapper;

    public RoleInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        // 静态资源等非 Controller 方法的请求，不参与角色判断
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        // 方法上的注解优先，其次是类上的（这样可以"整类 ADMIN，个别方法放开"）
        RequireRole required = method.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required == null) {
            return true;                     // 没声明 = 登录即可，已由 AuthInterceptor 保证
        }

        Object raw = request.getAttribute(AuthInterceptor.ATTR_ROLE);
        Role current = (raw instanceof Role role) ? role : Role.USER;

        // 角色是分级的：ADMIN 天然满足 USER 的要求
        boolean allowed = required.value() == Role.USER || current.isAdmin();
        if (allowed) {
            return true;
        }

        log.warn("权限不足: user={}, role={}, required={}, path={}",
                request.getAttribute(AuthInterceptor.ATTR_USERNAME), current, required.value(),
                request.getRequestURI());
        writeForbidden(response, "权限不足：该接口仅限 " + required.value() + " 角色");
        return false;
    }

    /**
     * 手写 403 响应。
     *
     * 和 AuthInterceptor 里的 401 同理：拦截器里抛出的异常不会走到 @RestControllerAdvice
     * （异常在 DispatcherServlet 之前就结束处理了），所以必须自己写 JSON，
     * 否则客户端拿到的是 Tomcat 的 HTML 错误页 —— 和我们"统一错误格式"的约定不符。
     */
    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}
