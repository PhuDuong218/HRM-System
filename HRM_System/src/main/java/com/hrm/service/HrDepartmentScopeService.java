package com.hrm.service;

import com.hrm.dto.HrScopeDTOs.*;
import com.hrm.repository.HrDepartmentScopeRepository;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrDepartmentScopeService {
    private final HrDepartmentScopeRepository repository;

    public HrDepartmentScopeService(HrDepartmentScopeRepository repository) {
        this.repository = repository;
    }

    private long admin(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || !"ADMIN".equals(actor.roleCode())
                || !repository.isActiveAdmin(actor.accountId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ ADMIN đang hoạt động được quản lý phạm vi HR");
        return actor.accountId();
    }

    private void dates(LocalDateTime from, LocalDateTime to) {
        if (from == null || (to != null && !to.isAfter(from)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng hiệu lực UTC không hợp lệ");
    }

    private void noOverlap(long hr, long department, LocalDateTime from, LocalDateTime to, long id) {
        if (repository.overlaps(hr, department, from, to, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phân công HR chồng lấn");
    }

    private ScopeView existing(long id) {
        ScopeView current = repository.byIdForUpdate(id);
        if (current == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công");
        if (current.revokedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phân công đã thu hồi không được kích hoạt lại");
        return current;
    }

    @Transactional(readOnly = true)
    public List<ScopeView> all(AuthenticatedAccount actor) {
        admin(actor);
        return repository.all();
    }

    @Transactional(readOnly = true)
    public List<ActiveDepartment> activeDepartments(AuthenticatedAccount actor, long accountId) {
        admin(actor);
        if (accountId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản không hợp lệ");
        return repository.activeDepartments(accountId);
    }

    @Transactional
    public ScopeView grant(AuthenticatedAccount actor, GrantRequest request) {
        long by = admin(actor);
        dates(request.effectiveFrom(), request.effectiveTo());
        if (!repository.isHrAccount(request.hrAccountId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản được cấp phải là HR");
        if (!repository.departmentExists(request.departmentId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phòng ban không tồn tại");
        repository.lockAccount(request.hrAccountId());
        repository.lockPair(request.hrAccountId(), request.departmentId());
        noOverlap(request.hrAccountId(), request.departmentId(), request.effectiveFrom(), request.effectiveTo(), -1);
        long id = repository.grant(request.hrAccountId(), request.departmentId(),
                request.effectiveFrom(), request.effectiveTo(), by);
        repository.audit(by, "HR_SCOPE_GRANTED", id, request.reason(), null, repository.asJson(id));
        return repository.byId(id);
    }

    @Transactional(readOnly = true)
    public List<DepartmentOption> departmentOptions(AuthenticatedAccount actor, long accountId) {
        admin(actor);
        if (accountId <= 0 || !repository.accountExists(accountId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy tài khoản");
        return repository.departmentOptions(accountId);
    }

    /** Lock in the same order as a scope change before the caller updates the account row. */
    @Transactional
    public void lockAccountForUpdate(AuthenticatedAccount actor, long accountId) {
        admin(actor);
        if (accountId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản không hợp lệ");
        repository.lockAccount(accountId);
    }

    @Transactional
    public List<ActiveDepartment> replaceDepartments(AuthenticatedAccount actor, long accountId, ReplaceDepartmentsRequest request) {
        long by = admin(actor);
        if (accountId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản không hợp lệ");
        if (request == null || request.expectedDepartmentIds() == null || request.departmentIds() == null
                || request.departmentIds().isEmpty() || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 1000 || request.departmentIds().stream().anyMatch(id -> id == null || id <= 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ít nhất một phòng ban hợp lệ");
        repository.lockAccount(accountId);
        if (!repository.isHrAccount(accountId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản phải có vai trò HR");
        LocalDateTime now = repository.utcNow();
        var current = repository.currentScopesForUpdate(accountId, now);
        Set<Long> currentIds = current.stream().map(ScopeView::departmentId).collect(Collectors.toSet());
        if (!currentIds.equals(request.expectedDepartmentIds()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phạm vi đã thay đổi. Vui lòng tải lại.");
        if (repository.futureDepartmentIds(accountId, now).stream().anyMatch(id -> !request.departmentIds().contains(id)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Có phân công phòng ban trong tương lai chưa được chọn. Vui lòng xử lý phân công đó trước.");
        var activeIds = repository.departmentOptions(accountId).stream().filter(DepartmentOption::active)
                .map(DepartmentOption::departmentId).collect(Collectors.toSet());
        if (request.departmentIds().stream().noneMatch(activeIds::contains)
                || request.departmentIds().stream().anyMatch(id -> !activeIds.contains(id) && !currentIds.contains(id)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chọn ít nhất một phòng ban đang hoạt động; không thể thêm phòng ban đã ngừng sử dụng");
        for (long id : request.departmentIds().stream().sorted().toList()) {
            repository.lockPair(accountId, id);
            if (currentIds.contains(id)) continue;
            if (repository.overlaps(accountId, id, now, null, -1L))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Có phân công tương lai bị chồng lấn. Vui lòng kiểm tra lại.");
            long scopeId = repository.grant(accountId, id, now, null, by);
            repository.audit(by, "HR_SCOPE_GRANTED", scopeId, request.reason().trim(), null, repository.asJson(scopeId));
        }
        for (var scope : current) {
            if (request.departmentIds().contains(scope.departmentId())) continue;
            repository.lockPair(accountId, scope.departmentId());
            String before = repository.asJson(scope.scopeId());
            if (repository.revoke(scope.scopeId(), by) != 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể cập nhật phạm vi. Vui lòng tải lại.");
            repository.audit(by, "HR_SCOPE_REVOKED", scope.scopeId(), request.reason().trim(), before, repository.asJson(scope.scopeId()));
        }
        return repository.activeDepartments(accountId);
    }

    @Transactional
    public ScopeView update(AuthenticatedAccount actor, long id, UpdateRequest request) {
        long by = admin(actor);
        dates(request.effectiveFrom(), request.effectiveTo());
        ScopeView snapshot = repository.byId(id);
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công");
        repository.lockAccount(snapshot.hrAccountId());
        ScopeView current = existing(id);
        repository.lockPair(current.hrAccountId(), current.departmentId());
        // Compare with database UTC, never with client clock.
        LocalDateTime nowUtc = repository.utcNow();
        if (!current.effectiveFrom().isAfter(nowUtc)
                && !current.effectiveFrom().equals(request.effectiveFrom()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không được đổi ngày bắt đầu khi phân công đã hiệu lực");
        // If already expired, changing the end would implicitly reinstate a past grant.
        if (current.effectiveTo() != null && !current.effectiveTo().isAfter(nowUtc))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phân công đã hết hạn; hãy cấp phân công mới");
        if (repository.isActiveHrAccount(current.hrAccountId()) && !current.effectiveFrom().isAfter(nowUtc)
                && request.effectiveTo() != null && !request.effectiveTo().isAfter(nowUtc)
                && repository.activeDepartments(current.hrAccountId()).size() <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "HR phải có ít nhất một phòng ban quản lý");
        noOverlap(current.hrAccountId(), current.departmentId(), request.effectiveFrom(), request.effectiveTo(), id);
        String before = repository.asJson(id);
        if (repository.change(id, request.effectiveFrom(), request.effectiveTo(), by) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể cập nhật phân công");
        repository.audit(by, "HR_SCOPE_UPDATED", id, request.reason(), before, repository.asJson(id));
        return repository.byId(id);
    }

    @Transactional
    public ScopeView revoke(AuthenticatedAccount actor, long id, RevokeRequest request) {
        long by = admin(actor);
        ScopeView snapshot = repository.byId(id);
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công");
        repository.lockAccount(snapshot.hrAccountId());
        ScopeView current = existing(id);
        repository.lockPair(current.hrAccountId(), current.departmentId());
        LocalDateTime now = repository.utcNow();
        if (repository.isActiveHrAccount(current.hrAccountId()) && !current.effectiveFrom().isAfter(now)
                && (current.effectiveTo() == null || current.effectiveTo().isAfter(now))
                && repository.activeDepartments(current.hrAccountId()).size() <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "HR phải có ít nhất một phòng ban quản lý");
        String before = repository.asJson(id);
        if (repository.revoke(id, by) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể thu hồi phân công");
        repository.audit(by, "HR_SCOPE_REVOKED", id, request.reason(), before, repository.asJson(id));
        return repository.byId(id);
    }
}
