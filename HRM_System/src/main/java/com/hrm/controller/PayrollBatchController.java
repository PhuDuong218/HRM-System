package com.hrm.controller;

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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/payroll-batches")
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

    // 1. Lấy tất cả các đợt bảng lương
    @GetMapping
    public ResponseEntity<List<PayrollBatchDTO>> getAll() {
        return ResponseEntity.ok(batchService.getAllBatches());
    }

    // 2. Lấy các đợt bảng lương theo kỳ
    @GetMapping("/period/{periodId}")
    public ResponseEntity<List<PayrollBatchDTO>> getByPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(batchService.getBatchesByPeriod(periodId));
    }

    // 3. P-05: API tạo bản nháp bảng lương
    @PostMapping("/draft")
    public ResponseEntity<PayrollBatchDTO> createDraft(@Valid @RequestBody CreateDraftBatchRequestDTO request) {
        PayrollBatchDTO created = batchService.createDraftBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // 4. P-08 -> P-11: Bấm nút tính toán lương cho đợt nháp
    @PostMapping("/{batchId}/calculate")
    public ResponseEntity<String> calculatePayroll(@PathVariable Long batchId) {
        return ResponseEntity.ok(executionService.calculateBatch(batchId));
    }

    // 5. Xem chi tiết bảng lương sau khi tính (Gross, Khấu trừ, Net)
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