package com.example.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理：把 OrderService 抛出的 IllegalArgumentException
 * 翻译成 HTTP 400 响应，并把错误信息装进 JSON。
 *
 * 控制台版是用 try/catch 接异常并 println；
 * Web 版是把异常转成 HTTP 状态码 + JSON —— 同一件事，两种表达方式。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }
}
