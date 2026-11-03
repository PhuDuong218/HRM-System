package com.hrm.controller;

import com.hrm.dto.PayslipDetailDTO;
import com.hrm.service.PayslipService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/payslips", "/api/payslips"})
public class PayslipController {

    private final PayslipService payslipService;

    public PayslipController(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    // P-15: API xem danh sách phiếu lương cá nhân của tôi
    @GetMapping("/my-payslips")
    public ResponseEntity<List<PayslipDetailDTO>> getMyPayslips(@RequestParam(defaultValue = "1") Long employeeId) {
        return ResponseEntity.ok(payslipService.getMyPayslips(employeeId));
    }

    // P-15: Xem chi tiết 1 phiếu lương dạng JSON
    @GetMapping("/{payslipId}")
    public ResponseEntity<PayslipDetailDTO> getPayslipDetail(@PathVariable Long payslipId) {
        return ResponseEntity.ok(payslipService.getPayslipDetail(payslipId));
    }

    // P-15: Xem giao diện phiếu lương HTML chuẩn in ấn / lưu file PDF (Ctrl + P)
    @GetMapping(value = "/{payslipId}/html", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> viewPayslipHtml(@PathVariable Long payslipId) {
        return ResponseEntity.ok(payslipService.renderPayslipHtml(payslipId));
    }

    // P-15: Xem trước và in phiếu lương theo ID nhân viên
    @GetMapping(value = "/preview/employee/{employeeId}/html", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> previewPayslipByEmployee(@PathVariable Long employeeId) {
        List<PayslipDetailDTO> myPayslips = payslipService.getMyPayslips(employeeId);
        if (myPayslips == null || myPayslips.isEmpty()) {
            return ResponseEntity.ok("""
                <div style='font-family:Arial,sans-serif;text-align:center;padding:50px;color:#64748b;'>
                    <h3>Chưa tìm thấy phiếu lương đã công bố của nhân sự này</h3>
                    <p>Phiếu lương chỉ được phát hành sau khi bảng lương của kỳ đã được <b>CHỐT</b> và <b>CÔNG BỐ</b>.</p>
                </div>
            """);
        }
        Long targetPayslipId = myPayslips.get(0).getPayslipId();
        return ResponseEntity.ok(payslipService.renderPayslipHtml(targetPayslipId));
    }
}