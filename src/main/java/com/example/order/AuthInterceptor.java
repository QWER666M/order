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
 *   3. 成功 -> 用 token 里的用户名【回数据库取当前角色】，把 username / role 写入 request 属性
 *      失败 -> 返回 401
 *
 * ★ 为什么角色要回库查，而不是直接读 token 里的 role 声明？
 *   token 一旦签发就无法收回。如果用户被降权、甚至被删除，
 *   旧 token 里仍然写着 "ADMIN"。回库查询换来"权限变更立即生效"，
 *   代价是每个请求多一次按唯一索引的查询（以后可以加缓存优化）。
 *   一句话：token 只证明【你是谁】，不决定【你能做什么】。
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

    /** 当前用户的角色，由 RoleInterceptor 读取 */
    public static final String ATTR_ROLE = "currentRole";

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final JwtService jwt;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtService jwt, UserRepository userRepository, ObjectMapper objectMapper) {
        this.jwt = jwt;
        this.userRepository = userRepository;
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

            // 用户可能已被删除 —— 此时即使 token 还没过期，也必须视为未登录
            User user = userRepository.findByUsername(username);
            if (user == null) {
                log.warn("token 有效但用户已不存在: username={}", username);
                writeUnauthorized(response, "登录已失效，请重新登录");
                return false;
            }

            request.setAttribute(ATTR_USERNAME, user.getUsername());
            request.setAttribute(ATTR_ROLE, Role.from(user.getRole()));
            return true;                       // 放行
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不对 / 已过期 / 格式错误 —— 对外统一说"登录已失效"，
            // 不暴露具体是"签名错"还是"过期"，避免给攻击者反馈。
            log.warn("token 校验失败: {}", e.getMessage());
            writeUnauthorized(response, "登录已失效，请重新登录");
            return false;
        }
    }

    /**
     * 手写 401 响应。
     *
     * ★ 更正一个曾经写错的注释：这里以为"拦截器里抛异常不会走到 @RestControllerAdvice"，
     *   实测是【会】的 —— DispatcherServlet 把 applyPreHandle 放在 try 块内，
     *   拦出异常后同样交给 HandlerExceptionResolver，@ExceptionHandler 照样生效。
     *
     *   那为什么还要手写？两个理由：
     *   1) 拦截器可能在异常处理器之外被复用（例如用在过滤器链里），行为更可控
     *   2) 不必依赖"某段框架源码恰好把调用包在 try 里"这种隐含前提
     *   代价是这段 JSON 的构造方式和全局处理器重复了一次。
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), java.util.Map.of("error", message));
    }




}
