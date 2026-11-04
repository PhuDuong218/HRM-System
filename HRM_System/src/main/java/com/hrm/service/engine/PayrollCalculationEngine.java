package com.hrm.service.engine;

import com.hrm.constant.PayrollConstants;
import com.hrm.dto.AttendanceSummaryDTO;
import com.hrm.dto.EmployeeSalaryProfileDTO;
import com.hrm.dto.EmployeeSalaryProfileDTO.AllowanceItemDTO;
import com.hrm.model.PayrollLine;
import com.hrm.model.SalaryComponent;
import com.hrm.repository.SalaryComponentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class PayrollCalculationEngine {

    private final SalaryComponentRepository componentRepository;

    public PayrollCalculationEngine(SalaryComponentRepository componentRepository) {
        this.componentRepository = componentRepository;
    }

    /**
     * Tính toán toàn bộ các dòng lương của một nhân viên
     */
    public List<PayrollLine> calculatePayrollLines(Long payrollDetailId,
                                                   EmployeeSalaryProfileDTO salaryProfile,
                                                   AttendanceSummaryDTO attendance) {
        List<PayrollLine> lines = new ArrayList<>();
        AtomicInteger lineCounter = new AtomicInteger(1);

        // Lấy danh mục thành phần lương từ CSDL làm snapshot
        Map<String, SalaryComponent> componentMap = componentRepository.findAll().stream()
                .collect(Collectors.toMap(SalaryComponent::getComponentCode, c -> c));

        // 1. P-08: LƯƠNG THEO CÔNG (Lương Gross cơ bản)
        BigDecimal baseSalary = salaryProfile.getBaseSalary();
        BigDecimal standardDays = attendance.getStandardWorkDays().compareTo(BigDecimal.ZERO) > 0 
                ? attendance.getStandardWorkDays() 
                : new BigDecimal(PayrollConstants.DEFAULT_STANDARD_WORK_DAYS);

        // Tổng ngày công được trả lương = ngày đi làm thực tế + ngày nghỉ phép có lương
        BigDecimal paidDays = attendance.getActualWorkDays().add(attendance.getPaidLeaveDays());
        BigDecimal dailyRate = baseSalary.divide(standardDays, 4, RoundingMode.HALF_UP);
        BigDecimal workSalary = dailyRate.multiply(paidDays).setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);

        SalaryComponent cLuongCong = componentMap.getOrDefault("LUONG_CONG", new SalaryComponent("LUONG_CONG", "Lương ngày công", null, true));
        lines.add(createLine(payrollDetailId, cLuongCong, lineCounter.getAndIncrement(),
                paidDays, dailyRate, BigDecimal.ONE, workSalary,
                "Lương theo công: " + paidDays + "/" + standardDays + " ngày"));

        // 2. P-09: TĂNG CA (OT)
        BigDecimal otHours = attendance.getOvertimeHours();
        BigDecimal otSalary = BigDecimal.ZERO;
        if (otHours != null && otHours.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal hourlyRate = dailyRate.divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP);
            BigDecimal otMultiplier = new BigDecimal("1.5"); // Hệ số OT ngày thường: 1.5
            otSalary = hourlyRate.multiply(otHours).multiply(otMultiplier)
                    .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);

            SalaryComponent cTangCa = componentMap.getOrDefault("TANG_CA", new SalaryComponent("TANG_CA", "Lương làm thêm giờ (OT)", null, true));
            lines.add(createLine(payrollDetailId, cTangCa, lineCounter.getAndIncrement(),
                    otHours, hourlyRate, otMultiplier, otSalary,
                    "Làm thêm giờ: " + otHours + " giờ x hệ số 1.5"));
        }

        // 3. P-09: PHỤ CẤP
        BigDecimal totalAllowance = BigDecimal.ZERO;
        BigDecimal taxExemptAllowance = BigDecimal.ZERO; // Phụ cấp được miễn thuế (ăn trưa tối đa 730k)

        if (salaryProfile.getAllowances() != null) {
            for (AllowanceItemDTO allowance : salaryProfile.getAllowances()) {
                BigDecimal amount = allowance.getAmount().setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
                totalAllowance = totalAllowance.add(amount);

                if ("AN_TRUA".equalsIgnoreCase(allowance.getAllowanceCode())) {
                    taxExemptAllowance = taxExemptAllowance.add(amount);
                }

                SalaryComponent cPhuCap = componentMap.getOrDefault("PHU_CAP", new SalaryComponent("PHU_CAP", allowance.getAllowanceName(), null, true));
                lines.add(createLine(payrollDetailId, cPhuCap, lineCounter.getAndIncrement(),
                        BigDecimal.ONE, amount, BigDecimal.ONE, amount,
                        allowance.getAllowanceName() + " (" + allowance.getCalculationBasis() + ")"));
            }
        }

        // 4. P-10: TRÍCH ĐÓNG BẢO HIỂM BẮT BUỘC (10.5% lương cơ bản)
        BigDecimal insuranceDeduction = baseSalary.multiply(PayrollConstants.TOTAL_INSURANCE_RATE)
                .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);

        SalaryComponent cKhauTru = componentMap.getOrDefault("KHAU_TRU", new SalaryComponent("KHAU_TRU", "Bảo hiểm bắt buộc (BHXH, BHYT, BHTN)", null, true));
        lines.add(createLine(payrollDetailId, cKhauTru, lineCounter.getAndIncrement(),
                BigDecimal.ONE, insuranceDeduction, BigDecimal.ONE, insuranceDeduction,
                "Trích đóng bảo hiểm 10.5% trên lương cơ bản"));

        // 5. P-10: THUẾ THU NHẬP CÁ NHÂN (TNCN) LŨY TIẾN TỪNG PHẦN
        // Thu nhập chịu thuế = Lương công + Lương OT + Phụ cấp - Khoản miễn thuế (ăn trưa)
        BigDecimal grossIncome = workSalary.add(otSalary).add(totalAllowance);
        // The project specification caps the total meal exemption per period,
        // including when the profile contains more than one meal allowance line.
        BigDecimal taxableIncome = grossIncome.subtract(taxExemptAllowance.min(new BigDecimal("730000")));

        // Thu nhập tính thuế = Thu nhập chịu thuế - Bảo hiểm - Giảm trừ bản thân (11tr)
        BigDecimal assessableIncome = taxableIncome.subtract(insuranceDeduction).subtract(PayrollConstants.GIAM_TRU_BAN_THAN);

        BigDecimal pitAmount = BigDecimal.ZERO;
        if (assessableIncome.compareTo(BigDecimal.ZERO) > 0) {
            pitAmount = calculateProgressiveTax(assessableIncome);
        }

        if (pitAmount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(createLine(payrollDetailId, cKhauTru, lineCounter.getAndIncrement(),
                    BigDecimal.ONE, pitAmount, BigDecimal.ONE, pitAmount,
                    "Thuế TNCN tạm tính theo biểu thuế lũy tiến từng phần"));
        }

        return lines;
    }

    /**
     * Tính thuế TNCN lũy tiến từng phần theo luật Việt Nam
     */
    private BigDecimal calculateProgressiveTax(BigDecimal income) {
        BigDecimal tax = BigDecimal.ZERO;
        double inc = income.doubleValue();

        if (inc <= 5000000) {
            tax = BigDecimal.valueOf(inc * 0.05);
        } else if (inc <= 10000000) {
            tax = BigDecimal.valueOf((inc - 5000000) * 0.10 + 250000);
        } else if (inc <= 18000000) {
            tax = BigDecimal.valueOf((inc - 10000000) * 0.15 + 750000);
        } else if (inc <= 32000000) {
            tax = BigDecimal.valueOf((inc - 18000000) * 0.20 + 1950000);
        } else {
            tax = BigDecimal.valueOf((inc - 32000000) * 0.25 + 4750000);
        }

        return tax.setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
    }

    private PayrollLine createLine(Long detailId, SalaryComponent comp, int lineNo,
                                   BigDecimal qty, BigDecimal rate, BigDecimal multi,
                                   BigDecimal amount, String explanation) {
        PayrollLine line = new PayrollLine();
        line.setPayrollDetailId(detailId);
        line.setComponentId(comp.getComponentId() != null ? comp.getComponentId() : 1L);
        line.setLineNo(lineNo);
        line.setComponentCodeSnapshot(comp.getComponentCode());
        line.setComponentNameSnapshot(comp.getComponentName());
        line.setDirectionSnapshot(comp.getDirection() != null ? comp.getDirection().name() : "ADD");
        line.setQuantity(qty);
        line.setUnitRate(rate);
        line.setMultiplier(multi);
        line.setAmount(amount);
        line.setExplanation(explanation);
        return line;
    }
}
