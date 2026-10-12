package com.example.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 全局异常处理：把所有异常翻译成【统一格式】的 JSON 响应。
 *
 * 统一格式：  {"error": "错误原因"}
 *
 * 为什么要统一？
 *   1. 客户端只需处理一种结构，代码简单
 *   2. 避免落到 Spring 的默认错误页 —— 它会暴露 timestamp / path / status 等内部信息
 *
 * 控制台版是用 try/catch 接异常并 println；
 * Web 版是把异常转成 HTTP 状态码 + JSON —— 同一件事，两种表达方式。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 业务规则校验失败（订单号为空、金额 ≤ 0、订单号重复）-> 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), "请求参数不合法");
    }

    /**
     * 接口层显式抛出的状态异常 -> 沿用异常自带的状态码（404 订单不存在、409 状态冲突）。
     *
     * 若不处理它，请求会落到 Spring 的默认错误处理，返回：
     *   {"timestamp":"...","status":404,"error":"Not Found","path":"/orders/xxx"}
     * 既与 400 的格式不一致，又泄露了内部信息。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return error(e.getStatusCode(), e.getReason(), "请求处理失败");
    }

    /**
     * 构造统一格式的响应。
     * 兜底文案是必要的：Map.of(...) 的值不允许为 null，而异常的 message 可能为空。
     */
    private ResponseEntity<Map<String, String>> error(
            HttpStatusCode status, String message, String fallback) {
        String text = (message == null || message.isBlank()) ? fallback : message;
        return ResponseEntity.status(status).body(Map.of("error", text));
    }
}
