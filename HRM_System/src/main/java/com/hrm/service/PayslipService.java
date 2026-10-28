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
     * P-15: Xuất giao diện HTML chuẩn in ấn PDF (Bấm Ctrl + P lưu file PDF)
     */
    public String renderPayslipHtml(Long payslipId) {
        PayslipDetailDTO p = getPayslipDetail(payslipId);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Phiếu lương - ").append(p.getPayslipNumber()).append("</title>");
        sb.append("<style>");
        sb.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 40px; color: #333; }");
        sb.append(".header { text-align: center; border-bottom: 2px solid #2B579A; padding-bottom: 10px; }");
        sb.append(".info { margin: 20px 0; line-height: 1.8; }");
        sb.append("table { width: 100%; border-collapse: collapse; margin-top: 20px; }");
        sb.append("th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }");
        sb.append("th { background-color: #f2f2f2; color: #2B579A; }");
        sb.append(".total-row { font-weight: bold; background-color: #fafafa; }");
        sb.append(".net-salary { font-size: 18px; color: #d9534f; font-weight: bold; }");
        sb.append("@media print { .no-print { display: none; } }");
        sb.append("</style></head><body>");

        sb.append("<div class='no-print' style='margin-bottom: 20px;'>");
        sb.append("<button onclick='window.print()' style='padding: 8px 16px; background-color: #2B579A; color: white; border: none; border-radius: 4px; cursor: pointer;'>In phiếu lương (Lưu PDF)</button>");
        sb.append("</div>");

        sb.append("<div class='header'>");
        sb.append("<h2>PHIẾU LƯƠNG NHÂN VIÊN</h2>");
        sb.append("<p>Mã số phiếu: <strong>").append(p.getPayslipNumber()).append("</strong> | Ngày phát hành: ").append(p.getIssuedAt()).append("</p>");
        sb.append("</div>");

        sb.append("<div class='info'>");
        sb.append("<p>Mã nhân viên: <strong>NV-").append(p.getEmployeeId()).append("</strong> &nbsp;|&nbsp; Kỳ lương: <strong>").append(p.getPeriodCode()).append("</strong> (Phiên bản ").append(p.getVersionNo()).append(")</p>");
        sb.append("</div>");

        sb.append("<table>");
        sb.append("<thead><tr><th>STT</th><th>Khoản mục</th><th>Phân loại</th><th>Số tiền (VNĐ)</th><th>Diễn giải</th></tr></thead><tbody>");

        int stt = 1;
        for (var line : p.getLines()) {
            sb.append("<tr>");
            sb.append("<td>").append(stt++).append("</td>");
            sb.append("<td>").append(line.getName()).append("</td>");
            sb.append("<td>").append("ADD".equalsIgnoreCase(line.getDirection()) ? "<span style='color:green'>+ Thu nhập</span>" : "<span style='color:red'>- Khấu trừ</span>").append("</td>");
            sb.append("<td>").append(line.getAmount()).append("</td>");
            sb.append("<td>").append(line.getExplanation() != null ? line.getExplanation() : "").append("</td>");
            sb.append("</tr>");
        }

        sb.append("<tr class='total-row'><td colspan='3'>Tổng thu nhập (Gross):</td><td colspan='2' style='color:green;'>").append(p.getTotalGross()).append(" VNĐ</td></tr>");
        sb.append("<tr class='total-row'><td colspan='3'>Tổng các khoản khấu trừ:</td><td colspan='2' style='color:red;'>").append(p.getTotalDeduction()).append(" VNĐ</td></tr>");
        sb.append("<tr class='total-row net-salary'><td colspan='3'>THỰC NHẬN (NET):</td><td colspan='2' class='net-salary'>").append(p.getNetSalary()).append(" VNĐ</td></tr>");

        sb.append("</tbody></table>");
        sb.append("<div style='margin-top: 40px; text-align: right;'><p>Người lập biểu / Kế toán trưởng</p></div>");
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