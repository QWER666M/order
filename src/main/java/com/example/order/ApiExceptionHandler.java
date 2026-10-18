package com.example.order;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 全局异常处理：把所有异常翻译成【统一格式】的 JSON 响应。
 *
 * 统一格式：  {"error": "错误原因"}
 *
 * 为什么要统一？
 *   1. 客户端只需处理一种结构，代码简单
 *   2. 不依赖 Spring 的默认错误页（它会返回 timestamp / path 等，格式与业务错误不一致）
 *
 * ★ 为什么继承 ResponseEntityExceptionHandler？
 *   Spring MVC 自己会抛很多标准异常（JSON 格式错、方法不支持、媒体类型不支持…），
 *   它们由框架决定状态码（400 / 405 / 415…）。这个父类内置了全部这些处理，
 *   我们只需重写 handleExceptionInternal，把响应体换成统一格式 ——
 *   既统一了格式，又不会破坏框架判断出来的正确状态码。
 *
 * 日志原则：日志里说全话（含堆栈，便于排查），响应里说最少的话（不暴露内部细节）。
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 业务规则校验失败（订单号为空、金额 ≤ 0、订单号重复）-> 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("请求参数不合法：{}", e.getMessage());
        return error(HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, e.getMessage(), "请求参数不合法");
    }

    /** 登录失败（用户名或密码错误）-> 401（身份未通过验证，与 400 的"参数格式错"区分开） */
    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<Object> handleLoginFailed(LoginFailedException e) {
        log.warn("登录失败：{}", e.getMessage());
        return error(HttpHeaders.EMPTY, HttpStatus.UNAUTHORIZED, e.getMessage(), "用户名或密码错误");
    }

    /**
     * 接口层显式抛出的状态异常 -> 沿用异常自带的状态码（404 订单不存在、409 状态冲突）。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(ResponseStatusException e) {
        log.warn("请求被拒绝：{} {}", e.getStatusCode(), e.getReason());
        return error(HttpHeaders.EMPTY, e.getStatusCode(), e.getReason(), "请求处理失败");
    }

    /**
     * 兜底：任何未预期的异常（数据库异常、空指针…）-> 500。
     * 这类异常说明代码有 bug 或环境有问题，必须【记全栈日志】，
     * 但对外只回一句"服务器内部错误"，不暴露内部细节。
     *
     * 注意：更具体的异常（上面的业务异常、父类处理的标准异常）会优先匹配，不会落到这里。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e) {
        log.error("未预期的异常", e);
        return error(HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR, null, "服务器内部错误");
    }

    /**
     * 所有 Spring MVC 标准异常都从这里出去。
     * 状态码沿用父类判定好的（400 / 405 / 415…），只把响应体换成统一格式。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        log.warn("请求处理失败：{} - {}", statusCode.value(), ex.getMessage());
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String fallback = (status != null) ? status.getReasonPhrase() : "请求处理失败";
        return error(headers, statusCode, null, fallback);
    }

    /**
     * 构造统一格式的响应。
     * 兜底文案是必要的：Map.of(...) 的值不允许为 null，而异常的 message 可能为空。
     */
    private ResponseEntity<Object> error(
            HttpHeaders headers, HttpStatusCode status, String message, String fallback) {
        String text = (message == null || message.isBlank()) ? fallback : message;
        return new ResponseEntity<>(Map.of("error", text), headers, status);
    }
}
