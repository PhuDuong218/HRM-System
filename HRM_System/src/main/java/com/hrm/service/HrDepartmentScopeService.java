package com.hrm.service;

import com.hrm.dto.HrScopeDTOs.*;
import com.hrm.repository.HrDepartmentScopeRepository;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDateTime;
import java.util.List;
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

    @Transactional
    public ScopeView grant(AuthenticatedAccount actor, GrantRequest request) {
        long by = admin(actor);
        dates(request.effectiveFrom(), request.effectiveTo());
        if (!repository.isHrAccount(request.hrAccountId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản được cấp phải là HR");
        if (!repository.departmentExists(request.departmentId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phòng ban không tồn tại");
        repository.lockPair(request.hrAccountId(), request.departmentId());
        noOverlap(request.hrAccountId(), request.departmentId(), request.effectiveFrom(), request.effectiveTo(), -1);
        long id = repository.grant(request.hrAccountId(), request.departmentId(),
                request.effectiveFrom(), request.effectiveTo(), by);
        repository.audit(by, "HR_SCOPE_GRANTED", id, request.reason(), null, repository.asJson(id));
        return repository.byId(id);
    }

    @Transactional
    public ScopeView update(AuthenticatedAccount actor, long id, UpdateRequest request) {
        long by = admin(actor);
        dates(request.effectiveFrom(), request.effectiveTo());
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
        ScopeView current = existing(id);
        repository.lockPair(current.hrAccountId(), current.departmentId());
        String before = repository.asJson(id);
        if (repository.revoke(id, by) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể thu hồi phân công");
        repository.audit(by, "HR_SCOPE_REVOKED", id, request.reason(), before, repository.asJson(id));
        return repository.byId(id);
    }
}
