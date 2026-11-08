package com.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import com.hrm.dto.*;
import com.hrm.exception.BusinessException;
import com.hrm.repository.ContractWarningRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class ContractWarningService {
    private final ContractWarningRepository repository;
    private final Clock clock;

    public ContractWarningService(ContractWarningRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResponse<ExpiringContractResponse> expiring(int days, int page, int size) {
        validatePage(page, size);
        if (days < 1 || days > 365) throw bad("days phải từ 1 đến 365");
        LocalDate today = LocalDate.now(clock), until = today.plusDays(days);
        long total = repository.countExpiring(today, until);
        return new PageResponse<>(repository.findExpiring(today, until, (long) page * size, size),
                page, size, total, pages(total, size));
    }

    public PageResponse<EmployeeWithoutActiveContractResponse> withoutActive(int page, int size) {
        validatePage(page, size);
        LocalDate today = LocalDate.now(clock);
        long total = repository.countWithoutActive(today);
        return new PageResponse<>(repository.findWithoutActive(today, (long) page * size, size),
                page, size, total, pages(total, size));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad("page >= 0; size từ 1 đến 100");
    }

    private int pages(long total, int size) {
        return (int) Math.ceil((double) total / size);
    }

    private BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
