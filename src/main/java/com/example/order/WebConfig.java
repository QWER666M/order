package com.example.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册鉴权与授权拦截器。
 *
 * 受保护的范围：
 *   /orders/**  —— 业务接口，登录即可（细粒度归属校验在 Controller 里做）
 *   /admin/**   —— 管理接口，登录 + ADMIN 角色（见 AdminController 上的 @RequireRole）
 *
 * 下面这些必须放开，否则会互相打架：
 *   /auth/**          —— 登录、注册本身（拿 token 的前提）
 *   /swagger-ui/**    —— 接口文档页面
 *   /v3/api-docs/**   —— OpenAPI 元数据
 *   /h2-console/**    —— 本地数据库控制台
 *
 * app.auth.enabled=false 时完全不注册（测试环境用，见 src/test/resources）。
 * ★ 注意：这个开关会同时关掉【认证和授权】，所以它绝不能出现在生产配置里。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] PROTECTED_PATHS = {
            "/orders", "/orders/**",
            "/admin", "/admin/**"
    };

    private final AuthInterceptor authInterceptor;
    private final RoleInterceptor roleInterceptor;
    private final boolean authEnabled;

    public WebConfig(AuthInterceptor authInterceptor,
                     RoleInterceptor roleInterceptor,
                     @Value("${app.auth.enabled:true}") boolean authEnabled) {
        this.authInterceptor = authInterceptor;
        this.roleInterceptor = roleInterceptor;
        this.authEnabled = authEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!authEnabled) {
            return;
        }
        // 顺序很重要：先认证（401），再授权（403）。
        // 反过来的话，未登录用户会在角色判断时被当成 USER，得到 403 而不是 401。
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(PROTECTED_PATHS)
                .excludePathPatterns(
                        "/auth/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/h2-console/**");

        registry.addInterceptor(roleInterceptor)
                .addPathPatterns(PROTECTED_PATHS);
    }
}
