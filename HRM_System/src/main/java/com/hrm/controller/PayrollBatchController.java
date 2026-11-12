package com.hrm.controller;

import com.hrm.dto.AdjustmentBatchRequestDTO;
import com.hrm.dto.CreateDraftBatchRequestDTO;
import com.hrm.dto.PayrollBatchDTO;
import com.hrm.dto.PayrollDetailViewDTO;
import com.hrm.model.PayrollDetail;
import com.hrm.model.PayrollLine;
import com.hrm.repository.PayrollDetailRepository;
import com.hrm.repository.PayrollLineRepository;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.PayrollBatchService;
import com.hrm.service.DraftPayrollService;
import com.hrm.service.PersonnelScopeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@RestController
@RequestMapping({"/payroll-batches", "/api/payroll-batches"})
@PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAuthority('PAYROLL_MANAGE')")
public class PayrollBatchController {

    private final com.hrm.repository.EmployeeRepository employeeRepository;
    private final PayrollBatchService batchService;
    private final DraftPayrollService draftPayroll;
    private final PayrollDetailRepository detailRepository;
    private final PayrollLineRepository lineRepository;
    private final PersonnelScopeService personnelScope;

    public PayrollBatchController(PayrollBatchService batchService,
                              DraftPayrollService draftPayroll,
                              PayrollDetailRepository detailRepository,
                              PayrollLineRepository lineRepository,
                              PersonnelScopeService personnelScope,
                              com.hrm.repository.EmployeeRepository employeeRepository) {
    this.batchService = batchService;
    this.draftPayroll = draftPayroll;
    this.detailRepository = detailRepository;
    this.lineRepository = lineRepository;
    this.personnelScope = personnelScope;
    this.employeeRepository = employeeRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<List<PayrollBatchDTO>> getAll() {
        return ResponseEntity.ok(batchService.getAllBatches());
    }

    @GetMapping("/period/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<List<PayrollBatchDTO>> getByPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(batchService.getBatchesByPeriod(periodId));
    }

    // Tra cứu đợt lương mới nhất của kỳ đang chọn
    @GetMapping("/period/{periodId}/current")
    public ResponseEntity<PayrollBatchDTO> getCurrentBatch(@PathVariable Long periodId) {
        List<PayrollBatchDTO> list = batchService.getBatchesByPeriod(periodId);
        if (list == null || list.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PayrollBatchDTO current = list.stream()
                .max((a, b) -> Integer.compare(a.getVersionNo(), b.getVersionNo()))
                .orElse(list.get(0));
        return ResponseEntity.ok(current);
    }

    @PostMapping("/draft")
    public ResponseEntity<PayrollBatchDTO> createDraft(@Valid @RequestBody CreateDraftBatchRequestDTO request) {
        PayrollBatchDTO created = batchService.createDraftBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{batchId}/calculate")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<String> calculatePayroll(@PathVariable Long batchId) {
        return ResponseEntity.ok(draftPayroll.calculate(batchId));
    }

    // Keep the legacy query parameter compatible; the authenticated actor is authoritative.
    @PostMapping("/{batchId}/lock")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<PayrollBatchDTO> lockBatch(@PathVariable Long batchId,
                                                    @RequestParam(required = false) Long actorAccountId) {
        return ResponseEntity.ok(batchService.lockBatch(batchId, currentActorAccountId()));
    }

    // P-12 & P-14: Công bố bảng lương và tự động phát hành phiếu lương
    @PostMapping("/{batchId}/publish")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<PayrollBatchDTO> publishBatch(@PathVariable Long batchId,
                                                       @RequestParam(required = false) Long actorAccountId) {
        return ResponseEntity.ok(batchService.publishBatch(batchId, currentActorAccountId()));
    }

    // P-13: Tạo phiên bản điều chỉnh có lý do
    @PostMapping("/{batchId}/adjust")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<PayrollBatchDTO> createAdjustment(@PathVariable Long batchId,
                                                            @Valid @RequestBody AdjustmentBatchRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.createAdjustmentBatch(batchId, request));
    }

    @GetMapping("/{batchId}/details")
@PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
public ResponseEntity<List<PayrollDetailViewDTO>> getBatchDetails(@PathVariable Long batchId) {
    List<PayrollDetail> details = detailRepository.findByBatchId(batchId);
    boolean provisional = !details.isEmpty() && detailRepository.hasProvisionalDraft(batchId);
    List<PayrollDetailViewDTO> result = new ArrayList<>();
    Set<Long> allowedEmployees = PersonnelScopeService.isHr() ? new HashSet<>(personnelScope.employees()) : null;

    Set<Long> missingSalaryIds = new java.util.LinkedHashSet<>(detailRepository.findEmployeesMissingSalary(batchId));
    if (allowedEmployees != null) missingSalaryIds.retainAll(allowedEmployees);

    // Lấy danh sách ID nhân viên để truy vấn thông tin một lượt
    Set<Long> pendingIds = new java.util.LinkedHashSet<>(detailRepository.findDraftEmployeesWithConfirmedAttendance(batchId));
    pendingIds.addAll(missingSalaryIds);
    if (allowedEmployees != null) pendingIds.retainAll(allowedEmployees);

    Set<Long> empIds = new HashSet<>(pendingIds);
    details.forEach(detail -> empIds.add(detail.getEmployeeId()));
    var employeeMap = employeeRepository.findAllById(empIds).stream()
            .collect(java.util.stream.Collectors.toMap(e -> e.getEmployeeId(), e -> e));

    for (PayrollDetail d : details) {
        if (allowedEmployees != null && !allowedEmployees.contains(d.getEmployeeId())) continue;
        if (missingSalaryIds.contains(d.getEmployeeId())) continue;
        PayrollDetailViewDTO dto = new PayrollDetailViewDTO();
        dto.setPayrollDetailId(d.getPayrollDetailId());
        dto.setEmployeeId(d.getEmployeeId());
        var employee = employeeMap.get(d.getEmployeeId());
        if (employee != null) {
            dto.setEmployeeName(employee.getFullName());
            dto.setEmployeeCode(employee.getEmployeeCode());
        }

        List<PayrollLine> lines = lineRepository.findByPayrollDetailIdOrderByLineNoAsc(d.getPayrollDetailId());
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal deduct = BigDecimal.ZERO;
        List<PayrollDetailViewDTO.PayrollLineDTO> lineDTOs = new ArrayList<>();

        for (PayrollLine l : lines) {
            lineDTOs.add(new PayrollDetailViewDTO.PayrollLineDTO(
                    l.getComponentCodeSnapshot(),
                    l.getComponentNameSnapshot(),
                    l.getDirectionSnapshot(),
                    l.getAmount(),
                    l.getExplanation()
            ));

            if ("DEDUCT".equalsIgnoreCase(l.getDirectionSnapshot())) {
                deduct = deduct.add(l.getAmount());
            } else {
                gross = gross.add(l.getAmount());
            }
        }

        if (lines.isEmpty() && pendingIds.contains(d.getEmployeeId())) continue;
        pendingIds.remove(d.getEmployeeId());
        for (var line : lineDTOs) line.describeDeductionShare(gross);
        dto.setTotalGross(gross);
        dto.setTotalDeduction(deduct);
        dto.setNetSalary(gross.subtract(deduct));
        dto.setLines(lineDTOs);
        dto.setProvisional(provisional);
        result.add(dto);
    }

    for (Long employeeId : pendingIds) {
        var employee = employeeMap.get(employeeId);
        if (employee == null) continue;
        var dto = new PayrollDetailViewDTO();
        dto.setEmployeeId(employeeId);
        dto.setEmployeeName(employee.getFullName());
        dto.setEmployeeCode(employee.getEmployeeCode());
        dto.setMissingSalary(missingSalaryIds.contains(employeeId));
        dto.setLines(List.of());
        result.add(dto);
    }

    return ResponseEntity.ok(result);
}

    private Long currentActorAccountId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedAccount actor
                && actor.accountId() != null) {
            return actor.accountId();
        }
        throw new AccessDeniedException("Không xác định được người thực hiện từ phiên đăng nhập");
    }
}
