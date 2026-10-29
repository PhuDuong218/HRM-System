package com.hrm.service;

import com.hrm.dto.HrScopeDTOs.*;
import com.hrm.repository.HrDepartmentScopeRepository;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrDepartmentScopeServiceTest {
    private HrDepartmentScopeRepository repository;
    private HrDepartmentScopeService service;
    private final AuthenticatedAccount admin = new AuthenticatedAccount(1L, 1L, "admin", "ADMIN");
    private final LocalDateTime future = LocalDateTime.of(2030, 11, 1, 1, 0);

    @BeforeEach
    void setup() {
        repository = mock(HrDepartmentScopeRepository.class);
        service = new HrDepartmentScopeService(repository);
        when(repository.isActiveAdmin(1L)).thenReturn(true);
    }

    @Test
    void denyMissingActor() {
        assertThrows(ResponseStatusException.class, () -> service.all(null));
        verify(repository, never()).all();
    }

    @Test
    void denyHrEvenIfAuthenticated() {
        AuthenticatedAccount hr = new AuthenticatedAccount(2L, 2L, "hr", "HR");
        assertThrows(ResponseStatusException.class, () -> service.all(hr));
    }

    @Test
    void denyInactiveAdmin() {
        when(repository.isActiveAdmin(1L)).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.all(admin));
    }

    @Test
    void rejectGrantWithInvalidEnd() {
        var req = new GrantRequest(2L, 3L, future, future, "Test");
        assertThrows(ResponseStatusException.class, () -> service.grant(admin, req));
        verify(repository, never()).grant(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void rejectGrantToNonHr() {
        var req = new GrantRequest(2L, 3L, future, null, "Test");
        assertThrows(ResponseStatusException.class, () -> service.grant(admin, req));
    }

    @Test
    void rejectOverlappingGrant() {
        when(repository.isHrAccount(2L)).thenReturn(true);
        when(repository.departmentExists(3L)).thenReturn(true);
        when(repository.overlaps(eq(2L), eq(3L), eq(future), isNull(), eq(-1L))).thenReturn(true);
        var req = new GrantRequest(2L, 3L, future, null, "Test");
        assertThrows(ResponseStatusException.class, () -> service.grant(admin, req));
        verify(repository, never()).grant(anyLong(), anyLong(), any(), any(), anyLong());
    }

    private ScopeView existing(LocalDateTime from, LocalDateTime end, LocalDateTime revoked) {
        return new ScopeView(10, 2, 3, from, end, revoked, null, from.minusDays(2), 1, null, null);
    }

    @Test
    void cannotChangeStartAfterEffective() {
        when(repository.byIdForUpdate(10L)).thenReturn(existing(future, null, null));
        when(repository.utcNow()).thenReturn(future.plusDays(1));
        var req = new UpdateRequest(future.plusDays(2), null, "Move start");
        assertThrows(ResponseStatusException.class, () -> service.update(admin, 10, req));
        verify(repository, never()).change(anyLong(), any(), any(), anyLong());
    }

    @Test
    void canChangeFutureStartWithAudit() {
        when(repository.byIdForUpdate(10L)).thenReturn(existing(future, null, null));
        when(repository.utcNow()).thenReturn(future.minusDays(1));
        when(repository.change(eq(10L), any(), any(), eq(1L))).thenReturn(1);
        when(repository.byId(10L)).thenReturn(existing(future.plusDays(1), null, null));
        var req = new UpdateRequest(future.plusDays(1), null, "Reschedule");
        service.update(admin, 10, req);
        verify(repository).audit(eq(1L), eq("HR_SCOPE_UPDATED"), eq(10L), eq("Reschedule"), any(), any());
    }

    @Test
    void doNotReactivateRevokedScope() {
        when(repository.byIdForUpdate(10L)).thenReturn(existing(future, null, future));
        assertThrows(ResponseStatusException.class,
                () -> service.update(admin, 10, new UpdateRequest(future, null, "Again")));
    }

    @Test
    void revokeWritesAudit() {
        when(repository.byIdForUpdate(10L)).thenReturn(existing(future, null, null));
        when(repository.revoke(10L, 1L)).thenReturn(1);
        when(repository.byId(10L)).thenReturn(existing(future, null, future.plusDays(1)));
        service.revoke(admin, 10, new RevokeRequest("Change assignment"));
        verify(repository).audit(eq(1L), eq("HR_SCOPE_REVOKED"), eq(10L), any(), any(), any());
    }
}
