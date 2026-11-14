package com.hrm.service;

import com.hrm.dto.PayrollDetailViewDTO;
import com.hrm.dto.PayslipDTO;
import com.hrm.dto.PayslipDetailDTO;
import com.hrm.enums.BatchStatus;
import com.hrm.model.PayrollBatch;
import com.hrm.model.PayrollDetail;
import com.hrm.model.PayrollLine;
import com.hrm.model.PayrollPeriod;
import com.hrm.model.Payslip;
import com.hrm.repository.PayrollBatchRepository;
import com.hrm.repository.PayrollDetailRepository;
import com.hrm.repository.PayrollLineRepository;
import com.hrm.repository.PayrollPeriodRepository;
import com.hrm.repository.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final PayrollBatchRepository batchRepository;
    private final PayrollDetailRepository detailRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollPeriodRepository periodRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public PayslipService(PayslipRepository payslipRepository,
                          PayrollBatchRepository batchRepository,
                          PayrollDetailRepository detailRepository,
                          PayrollLineRepository lineRepository,
                          PayrollPeriodRepository periodRepository,
                          org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.payslipRepository = payslipRepository;
        this.batchRepository = batchRepository;
        this.detailRepository = detailRepository;
        this.lineRepository = lineRepository;
        this.periodRepository = periodRepository;
        this.jdbc = jdbc;
    }

    /**
     * P-14: Tự động phát hành phiếu lương khi bảng lương được công bố (publishedAt != null)
     */
    @Transactional
    public List<PayslipDTO> generatePayslipsForBatch(Long batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bảng lương ID: " + batchId));

        if (batch.getStatus() != BatchStatus.DA_CHOT || batch.getPublishedAt() == null) {
            throw new RuntimeException("Chỉ được phát hành phiếu lương khi bảng lương đã chốt và công bố!");
        }

        PayrollPeriod period = periodRepository.findById(batch.getPeriodId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ lương ID: " + batch.getPeriodId()));

        List<PayrollDetail> details = detailRepository.findByBatchId(batchId);
        List<PayslipDTO> result = new ArrayList<>();

        for (PayrollDetail d : details) {
            if (!payslipRepository.existsByPayrollDetailId(d.getPayrollDetailId())) {
                String cleanPeriodCode = period.getPeriodCode().replace("-", "");
                String payslipNo = "PS-" + cleanPeriodCode + "-NV" + d.getEmployeeId() + "-V" + batch.getVersionNo();

                Payslip payslip = new Payslip();
                payslip.setPayrollDetailId(d.getPayrollDetailId());
                payslip.setPayslipNumber(payslipNo);
                payslip.setIssuedAt(LocalDateTime.now());
                payslip.setCreatedAt(LocalDateTime.now());

                Payslip saved = payslipRepository.save(payslip);
                result.add(new PayslipDTO(saved.getPayslipId(), saved.getPayrollDetailId(), saved.getPayslipNumber(), saved.getIssuedAt(), saved.getFileReference()));
            }
        }

        return result;
    }

    /**
     * P-15: Lấy danh sách phiếu lương cá nhân của một nhân viên (Chỉ lấy các phiếu từ batch đã công bố)
     */
    public List<PayslipDetailDTO> getMyPayslips(Long employeeId) {
        List<PayrollBatch> publishedBatches = batchRepository.findAll().stream()
                .filter(b -> b.getStatus() == BatchStatus.DA_CHOT && b.getPublishedAt() != null)
                .toList();

        List<PayslipDetailDTO> list = new ArrayList<>();
        for (PayrollBatch b : publishedBatches) {
            var detailOpt = detailRepository.findByBatchIdAndEmployeeId(b.getBatchId(), employeeId);
            if (detailOpt.isPresent()) {
                PayrollDetail d = detailOpt.get();
                var payslipOpt = payslipRepository.findByPayrollDetailId(d.getPayrollDetailId());
                if (payslipOpt.isPresent()) {
                    list.add(buildPayslipDetail(payslipOpt.get(), d, b));
                }
            }
        }
        return list;
    }

    public Long publishedPayslipId(Long payrollDetailId) {
        return payslipRepository.findByPayrollDetailId(payrollDetailId)
            .map(Payslip::getPayslipId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Chưa có phiếu lương đã phát hành cho bản ghi này."));
    }

    public PayslipDetailDTO getPayslipDetail(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu lương ID: " + payslipId));

        PayrollDetail detail = detailRepository.findById(payslip.getPayrollDetailId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chi tiết lương gắn với phiếu!"));

        PayrollBatch batch = batchRepository.findById(detail.getBatchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đợt bảng lương!"));

        if (batch.getStatus() != BatchStatus.DA_CHOT || batch.getPublishedAt() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Phiếu lương chưa được công bố.");
        }
        return buildPayslipDetail(payslip, detail, batch);
    }

    public String renderPayslipHtml(Long payslipId) {
        PayslipDetailDTO p = getPayslipDetail(payslipId);
        java.text.DecimalFormat fmt = new java.text.DecimalFormat("#,##0");

        String empCode = (p.getEmployeeCode() != null && !p.getEmployeeCode().isBlank()) ? p.getEmployeeCode() : ("NV-" + p.getEmployeeId());
        String empName = (p.getEmployeeName() != null && !p.getEmployeeName().isBlank()) ? p.getEmployeeName() : ("Nhân viên #" + p.getEmployeeId());
        String deptName = (p.getDepartmentName() != null && !p.getDepartmentName().isBlank()) ? p.getDepartmentName() : "—";
        String posName = (p.getPositionName() != null && !p.getPositionName().isBlank()) ? p.getPositionName() : "Nhân viên";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Phiếu lương - ").append(p.getPayslipNumber()).append("</title>");
        sb.append("<style>");
        sb.append("body { font-family: 'Segoe UI', Arial, sans-serif; margin: 36px auto; max-width: 840px; color: #1e293b; background: #fff; }");
        sb.append(".header { text-align: center; border-bottom: 2px solid #004581; padding-bottom: 14px; margin-bottom: 20px; }");
        sb.append(".header h2 { margin: 0 0 6px 0; color: #004581; font-size: 22px; letter-spacing: 0.5px; }");
        sb.append(".header p { margin: 0; color: #64748b; font-size: 13px; }");
        sb.append(".info-card { display: flex; justify-content: space-between; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px 18px; margin-bottom: 20px; font-size: 14px; }");
        sb.append(".info-card div { line-height: 1.7; }");
        sb.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }");
        sb.append("th, td { border: 1px solid #cbd5e1; padding: 10px 12px; }");
        sb.append("th { background-color: #f1f5f9; color: #004581; font-weight: 700; text-align: left; }");
        sb.append(".text-center { text-align: center; }");
        sb.append(".text-right { text-align: right; }");
        sb.append(".money-col { text-align: right; font-variant-numeric: tabular-nums; font-family: 'Consolas', 'Courier New', monospace; font-size: 13.5px; font-weight: 600; }");
        sb.append(".total-row { font-weight: bold; background-color: #f8fafc; }");
        sb.append(".net-salary { font-size: 16px; color: #004581; background-color: #eff6ff; font-weight: 750; }");
        sb.append(".btn-print { padding: 9px 18px; background-color: #004581; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 13px; }");
        sb.append(".footer-sign { display: flex; justify-content: space-between; margin-top: 45px; text-align: center; font-size: 13px; }");
        sb.append(".footer-sign div { width: 220px; }");
        sb.append("@media print { .no-print { display: none; } body { margin: 0; } }");
        sb.append("</style></head><body>");

        sb.append("<div class='no-print' style='margin-bottom: 20px; display: flex; justify-content: flex-end;'>");
        sb.append("<button class='btn-print' onclick='window.print()'>🖨 In phiếu lương / Lưu PDF</button>");
        sb.append("</div>");

        sb.append("<div class='header'>");
        sb.append("<h2>TRUNG TÂM GIÁO DỤC - PHIẾU THANH TOÁN TIỀN LƯƠNG</h2>");
        sb.append("<p>Mã số phiếu: <strong>").append(p.getPayslipNumber()).append("</strong> | Ngày phát hành: ").append(p.getIssuedAt()).append("</p>");
        sb.append("</div>");

        sb.append("<div class='info-card'>");
        sb.append("<div>Họ và tên nhân viên: <strong>").append(empName).append("</strong> (Mã NV: <strong>").append(empCode).append("</strong>)<br>")
          .append("Phòng ban: <strong>").append(deptName).append("</strong> · Chức vụ: <strong>").append(posName).append("</strong></div>");
        sb.append("<div style='text-align: right;'>Kỳ làm việc: <strong>").append(p.getPeriodCode()).append("</strong> (Đợt V").append(p.getVersionNo()).append(")<br>")
          .append("Trạng thái: <strong style='color:#16a34a;'>Đã chốt & Công bố</strong></div>");
        sb.append("</div>");

        sb.append("<table>");
        sb.append("<thead><tr><th class='text-center' style='width: 45px;'>STT</th><th>Khoản mục thanh toán</th><th style='width: 120px;'>Phân loại</th><th class='text-right' style='width: 150px;'>Số tiền (VNĐ)</th><th>Diễn giải & Lý do chi tiết</th></tr></thead><tbody>");

        int stt = 1;
        for (var line : p.getLines()) {
            boolean isAdd = "ADD".equalsIgnoreCase(line.getDirection());
            sb.append("<tr>");
            sb.append("<td class='text-center'>").append(stt++).append("</td>");
            sb.append("<td style='font-weight:600;'>").append(line.getName()).append("</td>");
            sb.append("<td>").append(isAdd ? "<span style='color:#16a34a;'>+ Thu nhập</span>" : "<span style='color:#dc2626;'>- Khấu trừ</span>").append("</td>");
            sb.append("<td class='money-col' style='color:").append(isAdd ? "#0f766e" : "#b91c1c").append(";'>")
              .append(isAdd ? "+" : "-").append(fmt.format(line.getAmount())).append(" đ</td>");
            sb.append("<td style='color:#475569; font-size:12px;'>").append(line.getExplanation() != null ? line.getExplanation() : "—").append(line.getDeductionShareText() == null ? "" : " · " + line.getDeductionShareText()).append("</td>");
            sb.append("</tr>");
        }

        sb.append("<tr class='total-row'><td colspan='3' class='text-right'>Tổng thu nhập (Gross):</td><td class='money-col' style='color:#16a34a;'>").append(fmt.format(p.getTotalGross())).append(" đ</td><td></td></tr>");
        sb.append("<tr class='total-row'><td colspan='3' class='text-right'>Tổng khấu trừ (BHXH, BHYT, BHTN, Thuế TNCN & Phạt):</td><td class='money-col' style='color:#dc2626;'>-").append(fmt.format(p.getTotalDeduction())).append(" đ</td><td></td></tr>");
        sb.append("<tr class='total-row net-salary'><td colspan='3' class='text-right' style='font-size:14.5px;'>THỰC LĨNH CHUYỂN KHOẢN (NET):</td><td class='money-col' style='font-size:16px; color:#004581;'>").append(fmt.format(p.getNetSalary())).append(" đ</td><td></td></tr>");

        sb.append("</tbody></table>");

        sb.append("<div class='footer-sign'>");
        sb.append("<div><strong>Người nhận tiền</strong><br><span style='color:#94a3b8; font-size:11px;'>(").append(empName).append(")</span></div>");
        sb.append("<div><strong>Kế toán tiền lương</strong><br><span style='color:#94a3b8; font-size:11px;'>(Xác nhận lập biểu)</span></div>");
        sb.append("</div>");

        sb.append("</body></html>");

        return sb.toString();
    }

    private PayslipDetailDTO buildPayslipDetail(Payslip payslip, PayrollDetail detail, PayrollBatch batch) {
        PayrollPeriod period = periodRepository.findById(batch.getPeriodId()).orElse(null);
        List<PayrollLine> lines = lineRepository.findByPayrollDetailIdOrderByLineNoAsc(detail.getPayrollDetailId());

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal deduct = BigDecimal.ZERO;
        List<PayrollDetailViewDTO.PayrollLineDTO> lineDTOs = new ArrayList<>();

        for (PayrollLine l : lines) {
            lineDTOs.add(new PayrollDetailViewDTO.PayrollLineDTO(
                    l.getComponentCodeSnapshot(), l.getComponentNameSnapshot(),
                    l.getDirectionSnapshot(), l.getAmount(), l.getExplanation()
            ));
            if ("DEDUCT".equalsIgnoreCase(l.getDirectionSnapshot())) {
                deduct = deduct.add(l.getAmount());
            } else {
                gross = gross.add(l.getAmount());
            }
        }

        PayslipDetailDTO dto = new PayslipDetailDTO();
        dto.setPayslipId(payslip.getPayslipId());
        dto.setPayslipNumber(payslip.getPayslipNumber());
        dto.setIssuedAt(payslip.getIssuedAt());
        dto.setEmployeeId(detail.getEmployeeId());
        dto.setPeriodCode(period != null ? period.getPeriodCode() : "");
        dto.setVersionNo(batch.getVersionNo());
        for (var line : lineDTOs) line.describeDeductionShare(gross);
        dto.setTotalGross(gross);
        dto.setTotalDeduction(deduct);
        dto.setNetSalary(gross.subtract(deduct));
        dto.setLines(lineDTOs);
        if (period != null && jdbc != null) {
            loadPayslipMetadata(dto, detail.getPayrollDetailId(), detail.getEmployeeId(),
                period.getPeriodStart(), period.getPeriodEnd());
        }
        return dto;
    }

    private void loadPayslipMetadata(PayslipDetailDTO dto, Long detailId, Long employeeId,
                                     java.time.LocalDate start, java.time.LocalDate end) {
        jdbc.query("""
            SELECT
                e.employee_code,
                e.full_name,
                (SELECT TOP (1) d.department_name
                 FROM dbo.employee_assignments a JOIN dbo.departments d ON d.department_id=a.department_id
                 WHERE a.employee_id=? AND a.effective_from<=?
                   AND (a.effective_to IS NULL OR a.effective_to>?)
                 ORDER BY a.effective_from DESC, a.assignment_id DESC) AS department_name,
                (SELECT TOP (1) p.position_name
                 FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id
                 WHERE a.employee_id=? AND a.effective_from<=?
                   AND (a.effective_to IS NULL OR a.effective_to>?)
                 ORDER BY a.effective_from DESC, a.assignment_id DESC) AS position_name,
                COALESCE(TRY_CONVERT(decimal(10,4), JSON_VALUE(s.snapshot_json, '$.actualWorkDays')),
                    (SELECT COUNT(DISTINCT a.work_date)
                     FROM dbo.attendance_records a
                     JOIN dbo.work_schedules w ON w.schedule_id=a.schedule_id
                     WHERE a.employee_id=? AND a.work_date BETWEEN ? AND ?
                       AND a.verification_status='DA_XAC_NHAN' AND a.attendance_result='DI_LAM'
                       AND a.actual_start IS NOT NULL AND a.actual_end IS NOT NULL
                       AND a.invalidated_at IS NULL AND w.cancelled_at IS NULL)) AS actual_days,
                TRY_CONVERT(decimal(10,4), COALESCE(JSON_VALUE(s.snapshot_json, '$.standardWorkDays'),
                    JSON_VALUE(s.snapshot_json, '$.standardDays'))) AS standard_days
            FROM dbo.employees e
            OUTER APPLY (
                SELECT TOP (1) CASE WHEN ISJSON(input_snapshot_json)=1 THEN input_snapshot_json END AS snapshot_json
                FROM dbo.payroll_snapshots
                WHERE payroll_detail_id=?
                ORDER BY captured_at DESC
            ) s
            WHERE e.employee_id=?
            """, rs -> {
                dto.setEmployeeCode(rs.getString("employee_code"));
                dto.setEmployeeName(rs.getString("full_name"));
                dto.setDepartmentName(rs.getString("department_name"));
                dto.setPositionName(rs.getString("position_name"));
                dto.setActualWorkDays(rs.getBigDecimal("actual_days"));
                dto.setStandardWorkDays(rs.getBigDecimal("standard_days"));
            }, employeeId, end, start, employeeId, end, start, employeeId, start, end, detailId, employeeId);
    }
}
