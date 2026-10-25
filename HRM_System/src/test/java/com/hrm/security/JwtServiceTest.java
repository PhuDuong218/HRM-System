package com.hrm.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET_A = "local-test-only-signing-key-AAA-12345678901234567890";
    private static final String SECRET_B = "local-test-only-signing-key-BBB-12345678901234567890";

    @Test
    void issueAndParseRoundTrip() {
        JwtService service = new JwtService(SECRET_A, 3600);
        String token = service.issue(42L, 17L, "admin.test", "ADMIN", 3);
        var claims = service.parse(token);
        assertEquals("admin.test", claims.getSubject());
        assertEquals(42L, ((Number) claims.get("account_id")).longValue());
        assertEquals(17L, ((Number) claims.get("employee_id")).longValue());
        assertEquals("ADMIN", claims.get("role", String.class));
        assertEquals(3, ((Number) claims.get("token_version")).intValue());
        assertNotNull(claims.getExpiration());
        assertEquals(3600L, service.getExpirationSeconds());
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        JwtService issuer = new JwtService(SECRET_A, 3600);
        JwtService verifier = new JwtService(SECRET_B, 3600);
        String token = issuer.issue(42L, 17L, "admin.test", "ADMIN", 3);
        assertThrows(JwtException.class, () -> verifier.parse(token));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = new JwtService(SECRET_A, -60);
        String token = service.issue(42L, 17L, "admin.test", "ADMIN", 3);
        assertThrows(JwtException.class, () -> service.parse(token));
    }

    @Test
    void rejectsMalformedToken() {
        JwtService service = new JwtService(SECRET_A, 3600);
        assertThrows(JwtException.class, () -> service.parse("invalid.test.token"));
    }

    @Test
    void rejectsTooShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("short", 3600));
    }
}
