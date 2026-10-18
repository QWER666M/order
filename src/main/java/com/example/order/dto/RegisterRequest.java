package com.example.order.dto;

/** 注册请求体：{ "username": "alice", "password": "secret123" } */
public record RegisterRequest(String username, String password) {
}
