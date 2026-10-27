package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.security.Principal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentHeadAccessServiceMergeTest {

    @Mock
    private JdbcTemplate jdbc;
    private DepartmentHeadAccessService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentHeadAccessService(jdbc);
    }

    @Test
    void usesJwtAccountUsernameAndReturnsDatabaseAuthorizedAccountId() {
        var account = new AuthenticatedAccount(100L, 200L, "hr-user", "HR");
        var authentication = new UsernamePasswordAuthenticationToken(account, null, List.of());
        when(jdbc.queryForList(anyString(), eq(Long.class), eq("hr-user"))).thenReturn(List.of(7L));

        assertEquals(7L, service.requireManager(authentication));

        verify(jdbc).queryForList(anyString(), eq(Long.class), eq("hr-user"));
    }

    @Test
    void preservesPlainServerPrincipalSupport() {
        Principal principal = () -> "plain-user";
        when(jdbc.queryForList(anyString(), eq(Long.class), eq("plain-user"))).thenReturn(List.of(8L));

        assertEquals(8L, service.requireManager(principal));
    }

    @Test
    void preservesAuthenticatedStringPrincipalSupport() {
        var authentication = new UsernamePasswordAuthenticationToken("legacy-user", null, List.of());
        when(jdbc.queryForList(anyString(), eq(Long.class), eq("legacy-user"))).thenReturn(List.of(9L));

        assertEquals(9L, service.requireManager(authentication));
    }

    static Stream<Arguments> unauthenticatedPrincipals() {
        var account = new AuthenticatedAccount(100L, 200L, "hr-user", "HR");
        return Stream.of(
                Arguments.of((Principal) null),
                Arguments.of(new UsernamePasswordAuthenticationToken(account, null)),
                Arguments.of(new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))),
                Arguments.of((Principal) () -> " "),
                Arguments.of((Principal) () -> null),
                Arguments.of(new UsernamePasswordAuthenticationToken(
                        new AuthenticatedAccount(100L, 200L, null, "HR"), null, List.of())));
    }

    @ParameterizedTest
    @MethodSource("unauthenticatedPrincipals")
    void rejectsMissingUnauthenticatedAnonymousOrNamelessPrincipalsBeforeDatabaseLookup(Principal principal) {
        var failure = assertThrows(ResponseStatusException.class, () -> service.requireManager(principal));

        assertEquals(401, failure.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    static Stream<List<Long>> unauthorizedAccountIds() {
        return Stream.of(List.of(), List.of(7L, 8L));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedAccountIds")
    void rejectsMissingOrAmbiguousDatabaseAuthorization(List<Long> accountIds) {
        var account = new AuthenticatedAccount(100L, 200L, "hr-user", "HR");
        var authentication = new UsernamePasswordAuthenticationToken(account, null, List.of());
        when(jdbc.queryForList(anyString(), eq(Long.class), eq("hr-user"))).thenReturn(accountIds);

        var failure = assertThrows(ResponseStatusException.class, () -> service.requireManager(authentication));

        assertEquals(403, failure.getStatusCode().value());
    }
}
