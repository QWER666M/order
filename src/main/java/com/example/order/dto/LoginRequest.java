package com.example.order.dto;

/** 登录请求体：{ "username": "alice", "password": "secret123" } */
public record LoginRequest(String username, String password) {
}
