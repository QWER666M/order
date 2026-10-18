package com.example.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册鉴权拦截器。
 *
 * 只保护 /orders/**（业务接口）；下面这些必须放开，否则会互相打架：
 *   /auth/**          —— 登录、注册本身（拿 token 的前提）
 *   /swagger-ui/**    —— 接口文档页面
 *   /v3/api-docs/**   —— OpenAPI 元数据
 *   /h2-console/**    —— 本地数据库控制台
 *
 * app.auth.enabled=false 时完全不注册（测试环境用，见 src/test/resources）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final boolean authEnabled;

    public WebConfig(AuthInterceptor authInterceptor,
                     @Value("${app.auth.enabled:true}") boolean authEnabled) {
        this.authInterceptor = authInterceptor;
        this.authEnabled = authEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!authEnabled) {
            return;
        }
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/orders", "/orders/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/h2-console/**");
    }
}
