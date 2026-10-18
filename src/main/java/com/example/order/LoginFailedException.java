package com.example.order;

/**
 * 登录失败（用户名或密码错误）。
 *
 * 为什么不直接用 IllegalArgumentException？
 *   因为两者要返回不同的 HTTP 状态码：
 *     参数格式不对     -> 400（你传错了）
 *     用户名/密码不对   -> 401（身份未通过验证）
 *   用不同的异常类型，全局处理器才能分别映射。
 */
public class LoginFailedException extends RuntimeException {

    public LoginFailedException(String message) {
        super(message);
    }
}
