package com.example.order;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明"访问这个接口至少需要什么角色"。
 *
 * 可以标在类上（整个 Controller 统一受约束），也可以标在方法上（覆盖类上的声明）。
 * 没有标注解 = 只要求登录，不限制角色。
 *
 * ★ 为什么用注解，而不是在拦截器里写 if (path.startsWith("/admin"))？
 *   1. 权限要求就写在接口旁边，新增接口时一眼可见，不会漏配
 *   2. 拦截器不需要知道任何具体路径，加接口不用改拦截器
 *   3. 这正是 Spring Security 的 @PreAuthorize 的同一思路（这里是精简版）
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 访问该接口所需的最低角色 */
    Role value();
}
