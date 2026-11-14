package com.hrm.controller;

import com.hrm.dto.AdjustmentBatchRequestDTO;
import com.hrm.dto.CreateDraftBatchRequestDTO;
import com.hrm.dto.PayrollBatchDTO;
import com.hrm.dto.PayrollDetailViewDTO;
import com.hrm.dto.PayrollEnhancementDTOs.DepartmentPayrollCostDTO;
import com.hrm.dto.PayrollEnhancementDTOs.EmployeePayrollHistoryItemDTO;
import com.hrm.dto.PayrollEnhancementDTOs.EmployeePayrollWorkDetailDTO;
import com.hrm.dto.PayrollEnhancementDTOs.PayrollReadinessReportDTO;
import com.hrm.dto.PayrollEnhancementDTOs.PeriodAllowanceOverviewDTO;
import com.hrm.exception.BusinessException;
import com.hrm.model.Employee;
import com.hrm.model.PayrollBatch;
import com.hrm.model.PayrollDetail;
import com.hrm.model.PayrollLine;
import com.hrm.model.PayrollPeriod;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PayrollBatchRepository;
import com.hrm.repository.PayrollDetailRepository;
import com.hrm.repository.PayrollLineRepository;
import com.hrm.repository.PayrollPeriodRepository;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.DraftPayrollService;
import com.hrm.service.PayrollBatchService;
import com.hrm.service.PayrollExecutionService;
import com.hrm.service.PayrollReadinessGuard;
import com.hrm.service.PersonnelScopeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping({ "/payroll-batches", "/api/payroll-batches" })
@PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAuthority('PAYROLL_MANAGE')")
public class PayrollBatchController {

    private final EmployeeRepository employeeRepository;
    private final PayrollBatchService batchService;
    private final DraftPayrollService draftPayroll;
    private final PayrollExecutionService executionService;
    private final PayrollDetailRepository detailRepository;
    private final PayrollLineRepository lineRepository;
    private final PersonnelScopeService personnelScope;

    @Autowired(required = false)
    private PayrollBatchRepository batchRepository;

    @Autowired(required = false)
    private PayrollPeriodRepository periodRepository;

    @Autowired(required = false)
    private PayrollReadinessGuard readinessGuard;

    @Autowired(required = false)
    private JdbcTemplate jdbc;

    @Autowired
    public PayrollBatchController(PayrollBatchService batchService,
            DraftPayrollService draftPayroll,
            PayrollExecutionService executionService,
            PayrollDetailRepository detailRepository,
            PayrollLineRepository lineRepository,
            PersonnelScopeService personnelScope,
            EmployeeRepository employeeRepository) {
        this.batchService = batchService;
        this.draftPayroll = draftPayroll;
        this.executionService = executionService;
        this.detailRepository = detailRepository;
        this.lineRepository = lineRepository;
        this.personnelScope = personnelScope;
        this.employeeRepository = employeeRepository;
    }

    public PayrollBatchController(PayrollBatchService batchService,
            DraftPayrollService draftPayroll,
            PayrollDetailRepository detailRepository,
            PayrollLineRepository lineRepository,
            PersonnelScopeService personnelScope,
            EmployeeRepository employeeRepository) {
        this(batchService, draftPayroll, null, detailRepository, lineRepository, personnelScope, employeeRepository);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<List<PayrollBatchDTO>> getAll() {
        return ResponseEntity.ok(batchService.getAllBatches());
    }

    @GetMapping({ "/period/{periodId}", "/periods/{periodId}/batches" })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<List<PayrollBatchDTO>> getByPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(batchService.getBatchesByPeriod(periodId));
    }

    // Tra cứu đợt lương mới nhất của kỳ đang chọn
    @GetMapping("/period/{periodId}/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<PayrollBatchDTO> getCurrentBatch(@PathVariable Long periodId) {
        List<PayrollBatchDTO> list = batchService.getBatchesByPeriod(periodId);
        if (list == null || list.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PayrollBatchDTO current = list.stream()
                .max(Comparator.comparingInt(PayrollBatchDTO::getVersionNo))
                .orElse(list.get(0));
        return ResponseEntity.ok(current);
    }

    // 1. Xem chi tiết minh chứng ngày công (check-in, check-out, số giờ làm, đi trễ, lý do) của nhân viên
    @GetMapping("/{batchId}/employees/{employeeId}/work-details")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public EmployeePayrollWorkDetailDTO getEmployeeWorkDetails(
            @PathVariable Long batchId,
            @PathVariable Long employeeId) {
        return executionService.getEmployeeWorkDetails(batchId, employeeId);
    }

    // 2. Xem danh sách phụ cấp của tháng đó & tổng hợp theo phòng ban
    @GetMapping("/periods/{periodId}/allowances")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public PeriodAllowanceOverviewDTO getPeriodAllowances(
            @PathVariable Long periodId,
            @RequestParam(required = false) Long departmentId) {
        return executionService.getPeriodAllowances(periodId, departmentId);
    }

    // 3. Bộ lọc & lịch sử lương nhân viên qua các kỳ
    @GetMapping("/history/employees")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public List<EmployeePayrollHistoryItemDTO> getEmployeePayrollHistory(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return executionService.getEmployeePayrollHistory(employeeId, departmentId, keyword, status);
    }

    // 4. Thống kê chi phí tiền lương theo phòng ban trong kỳ
    @GetMapping("/periods/{periodId}/department-costs")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public List<DepartmentPayrollCostDTO> getDepartmentCostSummary(@PathVariable Long periodId) {
        return executionService.getDepartmentCostSummary(periodId);
    }

    // 5. Kiểm tra điều kiện sẵn sàng tính/chốt lương (liệt kê rõ nhân viên & mã đơn vướng mắc)
    @GetMapping("/{batchId}/readiness")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public PayrollReadinessReportDTO checkBatchReadiness(@PathVariable Long batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy đợt lương #" + batchId));
        PayrollPeriod period = periodRepository.findById(batch.getPeriodId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy kỳ lương #" + batch.getPeriodId()));
        return readinessGuard.inspect(batch, period);
    }

    @PostMapping("/draft")
    public ResponseEntity<PayrollBatchDTO> createDraft(@Valid @RequestBody CreateDraftBatchRequestDTO request) {
        PayrollBatchDTO created = batchService.createDraftBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<String> calculatePayroll(Long batchId) {
        return calculatePayroll(batchId, false);
    }

    @PostMapping("/{batchId}/calculate")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<String> calculatePayroll(
            @PathVariable Long batchId,
            @RequestParam(required = false, defaultValue = "false") boolean forceDemo) {
        if (executionService != null) {
            return ResponseEntity.ok(executionService.calculateBatch(batchId, forceDemo));
        }
        return ResponseEntity.ok(draftPayroll.calculate(batchId));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<PayrollBatchDTO> lockBatch(Long batchId, Long actorAccountId) {
        return lockBatch(batchId, actorAccountId, false);
    }

    @PostMapping("/{batchId}/lock")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<PayrollBatchDTO> lockBatch(
            @PathVariable Long batchId,
            @RequestParam(required = false) Long actorAccountId,
            @RequestParam(required = false, defaultValue = "false") boolean forceDemo) {
        if (forceDemo) {
            return ResponseEntity.ok(batchService.lockBatch(batchId, currentActorAccountId(), true));
        }
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
        if (executionService != null && jdbc != null && batchRepository != null) {
            PayrollBatch currentBatch = batchRepository.findById(batchId).orElse(null);
            if (currentBatch != null && currentBatch.getStatus() == com.hrm.enums.BatchStatus.NHAP) {
                Integer lineCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.payroll_lines pl
                    JOIN dbo.payroll_details pd ON pd.payroll_detail_id = pl.payroll_detail_id
                    WHERE pd.batch_id = ?
                """, Integer.class, batchId);
                if (lineCount != null && lineCount == 0) {
                    try {
                        executionService.calculateBatch(batchId, true);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        List<PayrollDetail> details = detailRepository.findByBatchId(batchId);
        boolean provisional = !details.isEmpty() && detailRepository.hasProvisionalDraft(batchId);
        List<PayrollDetailViewDTO> result = new ArrayList<>();
        Set<Long> allowedEmployees = PersonnelScopeService.isHr() ? new HashSet<>(personnelScope.employees()) : null;

        Set<Long> missingSalaryIds = new LinkedHashSet<>(detailRepository.findEmployeesMissingSalary(batchId));
        if (allowedEmployees != null) {
            missingSalaryIds.retainAll(allowedEmployees);
        }

        Set<Long> pendingIds = new LinkedHashSet<>(detailRepository.findDraftEmployeesWithConfirmedAttendance(batchId));
        pendingIds.addAll(missingSalaryIds);
        if (allowedEmployees != null) {
            pendingIds.retainAll(allowedEmployees);
        }

        Set<Long> empIds = new HashSet<>(pendingIds);
        details.forEach(detail -> empIds.add(detail.getEmployeeId()));
        Map<Long, Employee> employeeMap = employeeRepository.findAllById(empIds).stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));

        // Tải thêm thông tin phòng ban, lương cơ bản và payslip_id từ CSDL (nếu có JdbcTemplate)
        Map<Long, String> deptByEmp = new HashMap<>();
        Map<Long, BigDecimal> baseSalaryByEmp = new HashMap<>();
        Map<Long, Long> payslipByDetail = new HashMap<>();
        if (jdbc != null && batchRepository != null && periodRepository != null) {
            PayrollBatch batch = batchRepository.findById(batchId).orElse(null);
            PayrollPeriod period = (batch != null) ? periodRepository.findById(batch.getPeriodId()).orElse(null) : null;
            LocalDate pStart = period != null ? period.getPeriodStart() : LocalDate.now().withDayOfMonth(1);
            LocalDate pEnd = period != null ? period.getPeriodEnd() : LocalDate.now();

            jdbc.query("""
                SELECT e.employee_id,
                       COALESCE(d.department_name, N'Chưa phân công') AS dept_name,
                       st.base_salary
                FROM dbo.employees e
                OUTER APPLY (
                    SELECT TOP 1 a.department_id
                    FROM dbo.employee_assignments a
                    WHERE a.employee_id = e.employee_id
                      AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                    ORDER BY a.effective_from DESC, a.assignment_id DESC
                ) a
                LEFT JOIN dbo.departments d ON d.department_id = a.department_id
                OUTER APPLY (
                    SELECT TOP 1 t.base_salary
                    FROM dbo.contracts c
                    JOIN dbo.contract_salary_terms t ON t.contract_id = c.contract_id
                    WHERE c.employee_id = e.employee_id
                      AND c.status IN ('DA_KY', 'DA_KET_THUC', 'HET_HAN')
                      AND t.effective_from <= ? AND (t.effective_to IS NULL OR t.effective_to >= ?)
                    ORDER BY t.effective_from DESC, t.salary_term_id DESC
                ) st
            """, rs -> {
                long eid = rs.getLong("employee_id");
                deptByEmp.put(eid, rs.getString("dept_name"));
                BigDecimal bs = rs.getBigDecimal("base_salary");
                if (bs != null) {
                    baseSalaryByEmp.put(eid, bs);
                }
            }, pEnd, pStart, pEnd, pStart);

            jdbc.query("""
                SELECT ps.payroll_detail_id, ps.payslip_id
                FROM dbo.payslips ps
                JOIN dbo.payroll_details pd ON pd.payroll_detail_id = ps.payroll_detail_id
                WHERE pd.batch_id = ?
            """, rs -> {
                payslipByDetail.put(rs.getLong("payroll_detail_id"), rs.getLong("payslip_id"));
            }, batchId);
        }

        for (PayrollDetail d : details) {
            if (allowedEmployees != null && !allowedEmployees.contains(d.getEmployeeId())) {
                continue;
            }

            List<PayrollLine> lines = lineRepository.findByPayrollDetailIdOrderByLineNoAsc(d.getPayrollDetailId());
            if (lines.isEmpty() && missingSalaryIds.contains(d.getEmployeeId())) {
                continue;
            }
            if (lines.isEmpty() && pendingIds.contains(d.getEmployeeId())) {
                continue;
            }
            pendingIds.remove(d.getEmployeeId());

            PayrollDetailViewDTO dto = new PayrollDetailViewDTO();
            dto.setPayrollDetailId(d.getPayrollDetailId());
            dto.setEmployeeId(d.getEmployeeId());

            Employee employee = employeeMap.get(d.getEmployeeId());
            dto.setEmployeeName(employee != null ? employee.getFullName() : "Nhân viên #" + d.getEmployeeId());
            if (employee != null) {
                dto.setEmployeeCode(employee.getEmployeeCode());
            }
            dto.setDepartmentName(deptByEmp.getOrDefault(d.getEmployeeId(), "Chưa phân công"));
            dto.setBaseSalary(baseSalaryByEmp.get(d.getEmployeeId()));
            dto.setPayslipId(payslipByDetail.get(d.getPayrollDetailId()));

            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal deduct = BigDecimal.ZERO;
            BigDecimal workDays = null;
            List<PayrollDetailViewDTO.PayrollLineDTO> lineDTOs = new ArrayList<>();

            for (PayrollLine l : lines) {
                if ("LUONG_CONG".equalsIgnoreCase(l.getComponentCodeSnapshot()) && l.getQuantity() != null) {
                    workDays = l.getQuantity();
                }
                lineDTOs.add(new PayrollDetailViewDTO.PayrollLineDTO(
                        l.getComponentCodeSnapshot(),
                        l.getComponentNameSnapshot(),
                        l.getDirectionSnapshot(),
                        l.getAmount(),
                        l.getExplanation()));

                if ("DEDUCT".equalsIgnoreCase(l.getDirectionSnapshot())) {
                    deduct = deduct.add(l.getAmount());
                } else {
                    gross = gross.add(l.getAmount());
                }
            }

            for (var line : lineDTOs) {
                line.describeDeductionShare(gross);
            }

            dto.setWorkDays(workDays);
            dto.setTotalGross(gross);
            dto.setTotalDeduction(deduct);
            dto.setNetSalary(gross.subtract(deduct));
            dto.setLines(lineDTOs);
            dto.setProvisional(provisional);
            result.add(dto);
        }

        for (Long employeeId : pendingIds) {
            Employee employee = employeeMap.get(employeeId);
            if (employee == null) {
                continue;
            }
            PayrollDetailViewDTO dto = new PayrollDetailViewDTO();
            dto.setEmployeeId(employeeId);
            dto.setEmployeeName(employee.getFullName());
            dto.setEmployeeCode(employee.getEmployeeCode());
            dto.setDepartmentName(deptByEmp.getOrDefault(employeeId, "Chưa phân công"));
            dto.setBaseSalary(baseSalaryByEmp.get(employeeId));
            dto.setMissingSalary(missingSalaryIds.contains(employeeId));
            dto.setLines(List.of());
            result.add(dto);
        }

        result.sort(Comparator.comparing(
                (PayrollDetailViewDTO dto) -> dto.getEmployeeCode() != null ? dto.getEmployeeCode() : "",
                Comparator.naturalOrder()));

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
