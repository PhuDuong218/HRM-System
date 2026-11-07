package com.hrm.controller;

import com.hrm.dto.PayslipDetailDTO;
import com.hrm.service.PayslipService;
import com.hrm.service.PersonnelScopeService;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping({"/payslips", "/api/payslips"})
public class PayslipController {

    private final PayslipService payslipService;
    private final PersonnelScopeService scope;

    public PayslipController(PayslipService payslipService, PersonnelScopeService scope) {
        this.payslipService = payslipService;
        this.scope = scope;
    }

    // P-15: API xem danh sách phiếu lương cá nhân của tôi
    @GetMapping("/my-payslips")
    public ResponseEntity<List<PayslipDetailDTO>> getMyPayslips(@RequestParam(required = false) Long employeeId) {
        var actor = currentActor();
        if (actor.employeeId() == null || (employeeId != null && !Objects.equals(employeeId, actor.employeeId()))) throw denied();
        return ResponseEntity.ok(payslipService.getMyPayslips(actor.employeeId()));
    }

    // P-15: Xem chi tiết 1 phiếu lương dạng JSON
    @GetMapping("/{payslipId}")
    public ResponseEntity<PayslipDetailDTO> getPayslipDetail(@PathVariable Long payslipId) {
        var detail = payslipService.getPayslipDetail(payslipId);
        authorize(detail.getEmployeeId());
        return ResponseEntity.ok(detail);
    }

    // P-15: Xem giao diện phiếu lương HTML chuẩn in ấn / lưu file PDF (Ctrl + P)
    @GetMapping(value = "/{payslipId}/html", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> viewPayslipHtml(@PathVariable Long payslipId) {
        authorize(payslipService.getPayslipDetail(payslipId).getEmployeeId());
        return ResponseEntity.ok(payslipService.renderPayslipHtml(payslipId));
    }

    private AuthenticatedAccount currentActor() {
        var actor = PersonnelScopeService.current();
        if (actor == null) throw denied();
        return actor;
    }
    private void authorize(Long employeeId) {
        var actor = currentActor();
        if (employeeId != null && Objects.equals(actor.employeeId(), employeeId)) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean payrollAccess = authentication.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("PAYROLL_VIEW") || a.getAuthority().equals("PAYROLL_MANAGE"));
        if (!payrollAccess || employeeId == null) throw denied();
        if ("ADMIN".equals(actor.roleCode())) return;
        if ("HR".equals(actor.roleCode())) { scope.managedEmployee(employeeId); return; }
        throw denied();
    }
    private AccessDeniedException denied() { return new AccessDeniedException("Không được xem phiếu lương này"); }
    // P-15: Xem trước và in phiếu lương theo ID nhân viên
    @GetMapping(value = "/preview/employee/{employeeId}/html", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> previewPayslipByEmployee(@PathVariable Long employeeId) {
        authorize(employeeId);
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
