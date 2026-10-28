package com.hrm.controller;

import com.hrm.dto.PayslipDetailDTO;
import com.hrm.service.PayslipService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payslips")
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
}