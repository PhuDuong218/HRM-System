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
    void activeDepartmentsRequireActiveAdminAndValidAccount() {
        var hr = new AuthenticatedAccount(2L, 2L, "hr", "HR");
        assertThrows(ResponseStatusException.class, () -> service.activeDepartments(hr, 2));
        assertThrows(ResponseStatusException.class, () -> service.activeDepartments(admin, 0));
        verify(repository, never()).activeDepartments(anyLong());
        when(repository.activeDepartments(2)).thenReturn(java.util.List.of(new ActiveDepartment(3, "Nhân sự")));
        assertEquals("Nhân sự", service.activeDepartments(admin, 2).get(0).departmentName());
    }

    @Test
    void adminCanLoadDepartmentChoicesBeforeChangingAccountToHr() {
        when(repository.accountExists(2L)).thenReturn(true);
        when(repository.departmentOptions(2L)).thenReturn(java.util.List.of(new DepartmentOption(3L, "Đào tạo", true, false)));
        assertEquals(3L, service.departmentOptions(admin, 2L).get(0).departmentId());
        verify(repository, never()).isHrAccount(2L);
    }

    @Test
    void replaceScopesAuditsGrantsAndRevocationsEvenForLockedHr() {
        when(repository.isHrAccount(2L)).thenReturn(true);
        when(repository.utcNow()).thenReturn(future);
        when(repository.currentScopesForUpdate(2L, future)).thenReturn(java.util.List.of(existing(future.minusDays(1), null, null)));
        when(repository.departmentOptions(2L)).thenReturn(java.util.List.of(new DepartmentOption(3, "A", true, true), new DepartmentOption(4, "B", true, false)));
        when(repository.grant(2L, 4L, future, null, 1L)).thenReturn(10L);
        when(repository.asJson(10L)).thenReturn("{}");
        when(repository.revoke(10L, 1L)).thenReturn(1);
        when(repository.activeDepartments(2L)).thenReturn(java.util.List.of(new ActiveDepartment(4, "B")));
        assertEquals(1, service.replaceDepartments(admin, 2, new ReplaceDepartmentsRequest(java.util.Set.of(3L), java.util.Set.of(4L), "Phân công")).size());
        verify(repository).lockPair(2L, 3L);
        verify(repository).lockPair(2L, 4L);
        verify(repository).audit(1L, "HR_SCOPE_GRANTED", 10L, "Phân công", null, "{}");
        verify(repository).audit(1L, "HR_SCOPE_REVOKED", 10L, "Phân công", "{}", "{}");
        verify(repository, never()).isActiveHrAccount(2L);
    }

    @Test
    void replaceScopesRejectsEmptyStaleNonHrAndOverlappingAssignments() {
        var request = new ReplaceDepartmentsRequest(java.util.Set.of(), java.util.Set.of(3L), "Phân công");
        assertThrows(ResponseStatusException.class, () -> service.replaceDepartments(new AuthenticatedAccount(2L, 2L, "hr", "HR"), 2L, request));
        assertThrows(ResponseStatusException.class, () -> service.replaceDepartments(admin, 2L, request));
        when(repository.isHrAccount(2L)).thenReturn(true);
        when(repository.utcNow()).thenReturn(future);
        assertThrows(ResponseStatusException.class, () -> service.replaceDepartments(admin, 2L, new ReplaceDepartmentsRequest(java.util.Set.of(), java.util.Set.of(), "Phân công")));
        when(repository.currentScopesForUpdate(2L, future)).thenReturn(java.util.List.of(existing(future.minusDays(1), null, null)));
        assertThrows(ResponseStatusException.class, () -> service.replaceDepartments(admin, 2L, request));
        when(repository.currentScopesForUpdate(2L, future)).thenReturn(java.util.List.of());
        when(repository.departmentOptions(2L)).thenReturn(java.util.List.of(new DepartmentOption(3, "A", true, false)));
        when(repository.overlaps(2L, 3L, future, null, -1L)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.replaceDepartments(admin, 2L, request));
        verify(repository, never()).grant(anyLong(), anyLong(), any(), any(), anyLong());
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
        when(repository.byId(10L)).thenReturn(existing(future, null, null));
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
        when(repository.byId(10L)).thenReturn(existing(future, null, null), existing(future.plusDays(1), null, null));
        var req = new UpdateRequest(future.plusDays(1), null, "Reschedule");
        service.update(admin, 10, req);
        verify(repository).audit(eq(1L), eq("HR_SCOPE_UPDATED"), eq(10L), eq("Reschedule"), any(), any());
    }

    @Test
    void doNotReactivateRevokedScope() {
        when(repository.byId(10L)).thenReturn(existing(future, null, future));
        when(repository.byIdForUpdate(10L)).thenReturn(existing(future, null, future));
        assertThrows(ResponseStatusException.class,
                () -> service.update(admin, 10, new UpdateRequest(future, null, "Again")));
    }

    @Test
    void revokeWritesAudit() {
        when(repository.byIdForUpdate(10L)).thenReturn(existing(future, null, null));
        when(repository.revoke(10L, 1L)).thenReturn(1);
        when(repository.byId(10L)).thenReturn(existing(future, null, null), existing(future, null, future.plusDays(1)));
        when(repository.utcNow()).thenReturn(future);
        service.revoke(admin, 10, new RevokeRequest("Change assignment"));
        verify(repository).audit(eq(1L), eq("HR_SCOPE_REVOKED"), eq(10L), any(), any(), any());
    }

    @Test
    void cannotRevokeFinalCurrentScopeOfActiveHr() {
        var current = existing(future.minusDays(1), null, null);
        when(repository.byId(10L)).thenReturn(current);
        when(repository.byIdForUpdate(10L)).thenReturn(current);
        when(repository.utcNow()).thenReturn(future);
        when(repository.isActiveHrAccount(2L)).thenReturn(true);
        when(repository.activeDepartments(2L)).thenReturn(java.util.List.of(new ActiveDepartment(3, "Đào tạo")));
        assertThrows(ResponseStatusException.class, () -> service.revoke(admin, 10, new RevokeRequest("Thay đổi")));
        verify(repository, never()).revoke(anyLong(), anyLong());
    }

    @Test
    void doesNotHideAnUnselectedFutureAssignment() {
        when(repository.isHrAccount(2L)).thenReturn(true);
        when(repository.utcNow()).thenReturn(future);
        when(repository.futureDepartmentIds(2L, future)).thenReturn(java.util.List.of(4L));
        var request = new ReplaceDepartmentsRequest(java.util.Set.of(), java.util.Set.of(3L), "Thay đổi");
        assertThrows(ResponseStatusException.class, () -> service.replaceDepartments(admin, 2, request));
        verify(repository, never()).grant(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void existingInactiveDepartmentCanBePreservedAlongsideAnActiveOne() {
        when(repository.isHrAccount(2L)).thenReturn(true);
        when(repository.utcNow()).thenReturn(future);
        when(repository.currentScopesForUpdate(2L, future)).thenReturn(java.util.List.of(existing(future.minusDays(1), null, null)));
        when(repository.departmentOptions(2L)).thenReturn(java.util.List.of(
                new DepartmentOption(3, "Ngừng hoạt động", false, true),
                new DepartmentOption(4, "Đào tạo", true, false)));
        when(repository.grant(2L, 4L, future, null, 1L)).thenReturn(11L);
        service.replaceDepartments(admin, 2, new ReplaceDepartmentsRequest(java.util.Set.of(3L), java.util.Set.of(3L, 4L), "Phân công"));
        verify(repository, never()).revoke(anyLong(), anyLong());
        verify(repository).grant(2L, 4L, future, null, 1L);
    }
}
