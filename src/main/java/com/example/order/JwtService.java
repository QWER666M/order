package com.example.order;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 的签发与校验。
 *
 * ★★ JWT 是什么（面试高频）
 *   一个"自包含的令牌"，三段用 . 分隔：
 *       header.payload.signature
 *   1) header   ：算法（如 HS256）
 *   2) payload  ：声明（subject=用户名、role、过期时间 exp…）
 *   3) signature：用【服务端密钥】对前两段做 HMAC 得到的签名
 *
 *   ★ 重要认知：JWT 【是签名，不是加密】。
 *     payload 只是 Base64 编码，任何人都能解开看内容 ——
 *     所以里面绝不能放密码、身份证号等敏感信息。
 *     它的价值在于：服务端不用存 session，只要验签通过就能确认"这确实是服务端签发的"。
 *
 *   ★ 为什么改不了内容？
 *     篡改 payload 后，除非知道密钥，否则算不出正确的 signature，验签会失败。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expireMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-minutes:120}") long expireMinutes) {
        // HS256 要求密钥至少 256 位（32 字节）。太短会在启动时直接抛异常，这是好事 ——
        // 避免"用弱密钥上线"这种问题被带到生产。
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    /**
     * 签发 token：把用户名和角色写进 payload，并设置过期时间。
     *
     * ★ 注意：这里的 role 声明只用于【前端展示/调试】，服务端授权一律以数据库为准
     *   （见 AuthInterceptor）。因为 token 签发后无法收回，
     *   用户被降权后旧 token 里的 role 依然是旧值，据此授权等于给了后门。
     */
    public String createToken(String username, String role) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expireMinutes * 60_000);
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token，返回用户名。
     * 验签失败、格式错误、已过期都会抛 JwtException —— 调用方统一按"未登录"处理。
     */
    public String parseUsername(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)          // 用同一个密钥验签
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
