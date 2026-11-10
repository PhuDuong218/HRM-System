package com.hrm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final long webIdleSeconds;

    @Autowired
    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expiration-seconds:3600}") long expirationSeconds,
            @Value("${app.security.web-idle-seconds:600}") long webIdleSeconds) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("app.security.jwt-secret phải có ít nhất 32 ký tự");
        }
        if (webIdleSeconds <= 0) {
            throw new IllegalArgumentException("app.security.web-idle-seconds phải lớn hơn 0");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.webIdleSeconds = webIdleSeconds;
    }

    public JwtService(String secret, long expirationSeconds) {
        this(secret, expirationSeconds, 600);
    }

    public String issue(Long accountId, Long employeeId, String username, String roleCode, int tokenVersion) {
        return issue(accountId, employeeId, username, roleCode, tokenVersion, expirationSeconds, false);
    }

    public String issueWeb(Long accountId, Long employeeId, String username, String roleCode, int tokenVersion) {
        return issue(accountId, employeeId, username, roleCode, tokenVersion, webIdleSeconds, true);
    }

    private String issue(Long accountId, Long employeeId, String username, String roleCode, int tokenVersion,
                         long lifetimeSeconds, boolean webSession) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("account_id", accountId)
                .claim("employee_id", employeeId)
                .claim("role", roleCode)
                .claim("token_version", tokenVersion)
                .claim("web_session", webSession)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(lifetimeSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public long getWebIdleSeconds() {
        return webIdleSeconds;
    }
}
