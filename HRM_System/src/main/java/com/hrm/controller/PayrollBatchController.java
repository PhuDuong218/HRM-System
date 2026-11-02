package com.hrm.controller;

import com.hrm.dto.AdjustmentBatchRequestDTO;
import com.hrm.dto.CreateDraftBatchRequestDTO;
import com.hrm.dto.PayrollBatchDTO;
import com.hrm.dto.PayrollDetailViewDTO;
import com.hrm.model.PayrollDetail;
import com.hrm.model.PayrollLine;
import com.hrm.repository.PayrollDetailRepository;
import com.hrm.repository.PayrollLineRepository;
import com.hrm.service.PayrollBatchService;
import com.hrm.service.PayrollExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/payroll-batches")
@PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAuthority('PAYROLL_MANAGE')")
public class PayrollBatchController {

    private final PayrollBatchService batchService;
    private final PayrollExecutionService executionService;
    private final PayrollDetailRepository detailRepository;
    private final PayrollLineRepository lineRepository;

    public PayrollBatchController(PayrollBatchService batchService,
                                  PayrollExecutionService executionService,
                                  PayrollDetailRepository detailRepository,
                                  PayrollLineRepository lineRepository) {
        this.batchService = batchService;
        this.executionService = executionService;
        this.detailRepository = detailRepository;
        this.lineRepository = lineRepository;
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

    @PostMapping("/draft")
    public ResponseEntity<PayrollBatchDTO> createDraft(@Valid @RequestBody CreateDraftBatchRequestDTO request) {
        PayrollBatchDTO created = batchService.createDraftBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{batchId}/calculate")
    public ResponseEntity<String> calculatePayroll(@PathVariable Long batchId) {
        return ResponseEntity.ok(executionService.calculateBatch(batchId));
    }

    // Keep the legacy query parameter compatible; the authenticated actor is authoritative.
    @PostMapping("/{batchId}/lock")
    public ResponseEntity<PayrollBatchDTO> lockBatch(@PathVariable Long batchId,
                                                    @RequestParam(required = false) Long actorAccountId) {
        return ResponseEntity.ok(batchService.lockBatch(batchId, currentActorAccountId()));
    }

    // P-12 & P-14: Công bố bảng lương và tự động phát hành phiếu lương
    @PostMapping("/{batchId}/publish")
    public ResponseEntity<PayrollBatchDTO> publishBatch(@PathVariable Long batchId,
                                                       @RequestParam(required = false) Long actorAccountId) {
        return ResponseEntity.ok(batchService.publishBatch(batchId, currentActorAccountId()));
    }

    // P-13: Tạo phiên bản điều chỉnh có lý do
    @PostMapping("/{batchId}/adjust")
    public ResponseEntity<PayrollBatchDTO> createAdjustment(@PathVariable Long batchId,
                                                            @Valid @RequestBody AdjustmentBatchRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.createAdjustmentBatch(batchId, request));
    }

    @GetMapping("/{batchId}/details")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<List<PayrollDetailViewDTO>> getBatchDetails(@PathVariable Long batchId) {
        List<PayrollDetail> details = detailRepository.findByBatchId(batchId);
        List<PayrollDetailViewDTO> result = new ArrayList<>();

        for (PayrollDetail d : details) {
            PayrollDetailViewDTO dto = new PayrollDetailViewDTO();
            dto.setPayrollDetailId(d.getPayrollDetailId());
            dto.setEmployeeId(d.getEmployeeId());

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

            dto.setTotalGross(gross);
            dto.setTotalDeduction(deduct);
            dto.setNetSalary(gross.subtract(deduct));
            dto.setLines(lineDTOs);
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
