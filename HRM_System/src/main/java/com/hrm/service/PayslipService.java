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

    public PayslipService(PayslipRepository payslipRepository,
                          PayrollBatchRepository batchRepository,
                          PayrollDetailRepository detailRepository,
                          PayrollLineRepository lineRepository,
                          PayrollPeriodRepository periodRepository) {
        this.payslipRepository = payslipRepository;
        this.batchRepository = batchRepository;
        this.detailRepository = detailRepository;
        this.lineRepository = lineRepository;
        this.periodRepository = periodRepository;
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

    /**
     * P-15: Xem chi tiết 1 phiếu lương
     */
    public PayslipDetailDTO getPayslipDetail(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu lương ID: " + payslipId));

        PayrollDetail detail = detailRepository.findById(payslip.getPayrollDetailId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chi tiết lương gắn với phiếu!"));

        PayrollBatch batch = batchRepository.findById(detail.getBatchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đợt bảng lương!"));

        return buildPayslipDetail(payslip, detail, batch);
    }

    /**
     * P-15: Xuất giao diện HTML chuẩn in ấn / Lưu file PDF (Ctrl + P)
     * Toàn bộ cột tiền và các dòng tổng được căn lề phải (right-aligned) với font số chuẩn kế toán.
     */
    public String renderPayslipHtml(Long payslipId) {
        PayslipDetailDTO p = getPayslipDetail(payslipId);
        java.text.DecimalFormat fmt = new java.text.DecimalFormat("#,##0");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Phiếu lương - ").append(p.getPayslipNumber()).append("</title>");
        sb.append("<style>");
        sb.append("body { font-family: 'Segoe UI', Arial, sans-serif; margin: 36px auto; max-width: 820px; color: #1e293b; background: #fff; }");
        sb.append(".header { text-align: center; border-bottom: 2px solid #004581; padding-bottom: 14px; margin-bottom: 20px; }");
        sb.append(".header h2 { margin: 0 0 6px 0; color: #004581; font-size: 22px; letter-spacing: 0.5px; }");
        sb.append(".header p { margin: 0; color: #64748b; font-size: 13px; }");
        sb.append(".info-card { display: flex; justify-content: space-between; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 18px; margin-bottom: 20px; font-size: 14px; }");
        sb.append(".info-card div { line-height: 1.6; }");
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
        sb.append("<p>Mã số phiếu: <strong>").append(p.getPayslipNumber()).append("</strong> | Ngày lập: ").append(p.getIssuedAt()).append("</p>");
        sb.append("</div>");

        sb.append("<div class='info-card'>");
        sb.append("<div>Mã nhân sự: <strong>NV-").append(p.getEmployeeId()).append("</strong><br>Kỳ làm việc: <strong>").append(p.getPeriodCode()).append("</strong></div>");
        sb.append("<div style='text-align: right;'>Đợt công bố: <strong>Phiên bản ").append(p.getVersionNo()).append("</strong><br>Trạng thái: <strong style='color:#16a34a;'>Đã phê duyệt</strong></div>");
        sb.append("</div>");

        sb.append("<table>");
        sb.append("<thead><tr><th class='text-center' style='width: 45px;'>STT</th><th>Khoản mục thanh toán</th><th style='width: 120px;'>Phân loại</th><th class='text-right' style='width: 150px;'>Số tiền (VNĐ)</th><th>Diễn giải</th></tr></thead><tbody>");

        int stt = 1;
        for (var line : p.getLines()) {
            boolean isAdd = "ADD".equalsIgnoreCase(line.getDirection());
            sb.append("<tr>");
            sb.append("<td class='text-center'>").append(stt++).append("</td>");
            sb.append("<td style='font-weight:600;'>").append(line.getName()).append("</td>");
            sb.append("<td>").append(isAdd ? "<span style='color:#16a34a;'>+ Thu nhập</span>" : "<span style='color:#dc2626;'>- Khấu trừ</span>").append("</td>");
            sb.append("<td class='money-col' style='color:").append(isAdd ? "#0f766e" : "#b91c1c").append(";'>")
              .append(isAdd ? "+" : "-").append(fmt.format(line.getAmount())).append(" đ</td>");
            sb.append("<td style='color:#64748b; font-size:12px;'>").append(line.getExplanation() != null ? line.getExplanation() : "—").append("</td>");
            sb.append("</tr>");
        }

        sb.append("<tr class='total-row'><td colspan='3' class='text-right'>Tổng thu nhập trước thuế (Gross):</td><td class='money-col' style='color:#16a34a;'>").append(fmt.format(p.getTotalGross())).append(" đ</td><td></td></tr>");
        sb.append("<tr class='total-row'><td colspan='3' class='text-right'>Tổng trích đóng BHXH, BHYT & Thuế TNCN:</td><td class='money-col' style='color:#dc2626;'>-").append(fmt.format(p.getTotalDeduction())).append(" đ</td><td></td></tr>");
        sb.append("<tr class='total-row net-salary'><td colspan='3' class='text-right' style='font-size:14.5px;'>THỰC LĨNH CHUYỂN KHOẢN (NET):</td><td class='money-col' style='font-size:16px; color:#004581;'>").append(fmt.format(p.getNetSalary())).append(" đ</td><td></td></tr>");

        sb.append("</tbody></table>");

        sb.append("<div class='footer-sign'>");
        sb.append("<div><strong>Người nhận tiền</strong><br><span style='color:#94a3b8; font-size:11px;'>(Ký và ghi rõ họ tên)</span></div>");
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
        dto.setTotalGross(gross);
        dto.setTotalDeduction(deduct);
        dto.setNetSalary(gross.subtract(deduct));
        dto.setLines(lineDTOs);
        return dto;
    }
}