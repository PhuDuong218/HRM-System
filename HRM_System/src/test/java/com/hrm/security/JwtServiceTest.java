package com.hrm.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import com.hrm.service.AuthService;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.AuditLogRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.mock;

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

    @Test
    void webSessionRefreshRetainsRevocationVersionAndRejectsOtherTokens() {
        JwtService jwt = new JwtService(SECRET_A, 3600, 30);
        AuthService auth = new AuthService(mock(AccountRepository.class), mock(EmployeeRepository.class),
                mock(EmployeeAssignmentRepository.class), mock(AuditLogRepository.class), mock(PasswordEncoder.class), jwt);
        AuthenticatedAccount account = new AuthenticatedAccount(42L, 17L, "admin.test", "ADMIN");
        String initial = jwt.issueWeb(42L, 17L, "admin.test", "ADMIN", 3);
        var initialClaims = jwt.parse(initial);
        assertEquals(30_000L, initialClaims.getExpiration().getTime() - initialClaims.getIssuedAt().getTime());
        var result = auth.refreshWeb(account, initial);
        assertEquals(30, result.expiresInSeconds());
        assertEquals(3, jwt.parse(result.accessToken()).get("token_version", Integer.class));
        assertThrows(BadCredentialsException.class,
                () -> auth.refreshWeb(account, jwt.issue(42L, 17L, "admin.test", "ADMIN", 3)));
        assertThrows(BadCredentialsException.class,
                () -> auth.refreshWeb(new AuthenticatedAccount(99L, 17L, "other", "ADMIN"), initial));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(SECRET_A, 3600, 0));
    }
}
