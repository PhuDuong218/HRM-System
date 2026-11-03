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
import com.hrm.service.PayrollExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping({"/payroll-batches", "/api/payroll-batches"})
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
    public ResponseEntity<List<PayrollBatchDTO>> getAll() {
        return ResponseEntity.ok(batchService.getAllBatches());
    }

    @GetMapping("/period/{periodId}")
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
    public ResponseEntity<String> calculatePayroll(@PathVariable Long batchId) {
        return ResponseEntity.ok(executionService.calculateBatch(batchId));
    }

    // P-12: Chốt bảng lương (lấy accountId từ Token JWT, fallback về 1L nếu test cục bộ)
    @PostMapping("/{batchId}/lock")
    public ResponseEntity<PayrollBatchDTO> lockBatch(
            @PathVariable Long batchId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {

        Long lockerAccountId = (actor != null && actor.accountId() != null) ? actor.accountId() : 1L;
        return ResponseEntity.ok(batchService.lockBatch(batchId, lockerAccountId));
    }

    // P-12 & P-14: Công bố bảng lương và tự động phát hành phiếu lương
    @PostMapping("/{batchId}/publish")
    public ResponseEntity<PayrollBatchDTO> publishBatch(
            @PathVariable Long batchId,
            @RequestParam(required = false) Long actorAccountId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {

        Long publisherId = actorAccountId != null ? actorAccountId : (actor != null ? actor.accountId() : 1L);
        return ResponseEntity.ok(batchService.publishBatch(batchId, publisherId));
    }

    // P-13: Tạo phiên bản điều chỉnh có lý do
    @PostMapping("/{batchId}/adjust")
    public ResponseEntity<PayrollBatchDTO> createAdjustment(
            @PathVariable Long batchId,
            @Valid @RequestBody AdjustmentBatchRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.createAdjustmentBatch(batchId, request));
    }

    @GetMapping("/{batchId}/details")
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
}