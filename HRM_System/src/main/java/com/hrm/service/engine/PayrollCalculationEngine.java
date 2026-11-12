package com.hrm.service.engine;

import com.hrm.constant.PayrollConstants;
import com.hrm.dto.AttendanceSummaryDTO;
import com.hrm.dto.EmployeeSalaryProfileDTO;
import com.hrm.dto.PayrollEnhancementDTOs.DailyWorkEvidenceDTO;
import com.hrm.model.PayrollLine;
import com.hrm.model.SalaryComponent;
import com.hrm.repository.SalaryComponentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class PayrollCalculationEngine {

    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("8");
    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
    private static final BigDecimal LUNCH_TAX_EXEMPT_CAP = new BigDecimal("730000");

    private final SalaryComponentRepository componentRepository;

    public PayrollCalculationEngine(SalaryComponentRepository componentRepository) {
        this.componentRepository = componentRepository;
    }

    /**
     * Giữ tương thích với các bài kiểm thử JUnit 3 tham số hiện có.
     */
    public List<PayrollLine> calculatePayrollLines(
            Long payrollDetailId,
            EmployeeSalaryProfileDTO profile,
            AttendanceSummaryDTO attendance) {
        return calculatePayrollLines(
                payrollDetailId,
                profile,
                attendance,
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    /**
     * Tính toàn bộ thu nhập, phụ cấp, phạt đi trễ theo giờ, nghỉ không phép, cắt cơm trưa khi trễ > 1/2 ngày, BH và thuế TNCN.
     */
    public List<PayrollLine> calculatePayrollLines(
            Long payrollDetailId,
            EmployeeSalaryProfileDTO profile,
            AttendanceSummaryDTO attendance,
            List<DailyWorkEvidenceDTO> dailyRecords,
            BigDecimal unexcusedAbsenceDays,
            BigDecimal approvedBonusAmount,
            BigDecimal approvedDisciplineDeduction) {

        List<PayrollLine> lines = new ArrayList<>();
        List<DailyWorkEvidenceDTO> safeDailyRecords = (dailyRecords != null) ? dailyRecords : List.of();
        int lineNo = 1;

        BigDecimal baseSalary = profile.getBaseSalary() != null ? profile.getBaseSalary() : BigDecimal.ZERO;
        BigDecimal standardDays = (attendance.getStandardWorkDays() != null && attendance.getStandardWorkDays().compareTo(BigDecimal.ZERO) > 0)
                ? attendance.getStandardWorkDays()
                : BigDecimal.valueOf(PayrollConstants.DEFAULT_STANDARD_WORK_DAYS);

        BigDecimal actualWorkDays = attendance.getActualWorkDays() != null ? attendance.getActualWorkDays() : BigDecimal.ZERO;
        BigDecimal paidLeaveDays = attendance.getPaidLeaveDays() != null ? attendance.getPaidLeaveDays() : BigDecimal.ZERO;
        BigDecimal overtimeHours = attendance.getOvertimeHours() != null ? attendance.getOvertimeHours() : BigDecimal.ZERO;
        BigDecimal unexcusedDays = unexcusedAbsenceDays != null ? unexcusedAbsenceDays : BigDecimal.ZERO;

        BigDecimal dailyRate = baseSalary.divide(standardDays, 4, RoundingMode.HALF_UP);
        BigDecimal hourlyRate = dailyRate.divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP);

        // 1. LƯƠNG THEO CÔNG
        BigDecimal baseWorkDays = actualWorkDays.add(paidLeaveDays).add(unexcusedDays);
        if (baseWorkDays.compareTo(standardDays) > 0) {
            baseWorkDays = standardDays;
        }
        BigDecimal workSalary = dailyRate.multiply(baseWorkDays)
                .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);

        lines.add(buildLine(
                payrollDetailId, lineNo++, "LUONG_CONG", "Lương theo ngày công", "ADD",
                baseWorkDays, dailyRate.setScale(0, RoundingMode.HALF_UP), BigDecimal.ONE, workSalary,
                String.format("Công thực tế: %s ngày + Nghỉ phép hưởng lương: %s ngày (Công chuẩn: %s ngày)",
                        actualWorkDays.stripTrailingZeros().toPlainString(),
                        paidLeaveDays.stripTrailingZeros().toPlainString(),
                        standardDays.stripTrailingZeros().toPlainString())
        ));

        // 2. TIỀN TĂNG CA (OT)
        BigDecimal overtimePay = BigDecimal.ZERO;
        if (overtimeHours.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal otMultiplier = new BigDecimal("1.5");
            overtimePay = hourlyRate.multiply(overtimeHours).multiply(otMultiplier)
                    .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "TANG_CA", "Tiền tăng ca (OT)", "ADD",
                    overtimeHours, hourlyRate.setScale(0, RoundingMode.HALF_UP), otMultiplier, overtimePay,
                    String.format("Tăng ca đã duyệt: %s giờ x Hệ số 1.5", overtimeHours.stripTrailingZeros().toPlainString())
            ));
        }

        // 3. PHỤ CẤP (Trễ quá 1/2 ngày làm việc -> trừ phụ cấp cơm trưa ngày đó)
        List<DailyWorkEvidenceDTO> forfeitedLunchRecords = safeDailyRecords.stream()
                .filter(DailyWorkEvidenceDTO::lunchAllowanceForfeited)
                .toList();
        int forfeitedLunchDays = forfeitedLunchRecords.size();
        String forfeitedDatesText = forfeitedLunchRecords.stream()
                .map(d -> d.workDate() + " (trễ " + d.lateMinutes() + "p)")
                .collect(Collectors.joining(", "));

        BigDecimal totalAllowance = BigDecimal.ZERO;
        BigDecimal taxExemptLunchAllowance = BigDecimal.ZERO;

        if (profile.getAllowances() != null) {
            for (EmployeeSalaryProfileDTO.AllowanceItemDTO item : profile.getAllowances()) {
                BigDecimal rawAmt = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                String code = item.getAllowanceCode() != null ? item.getAllowanceCode().toUpperCase(Locale.ROOT) : "";
                String name = item.getAllowanceName() != null ? item.getAllowanceName() : "Phụ cấp";
                String basis = item.getCalculationBasis() != null ? item.getCalculationBasis() : "THEO_KY";

                boolean isLunch = code.contains("AN_TRUA") || code.contains("COM_TRUA")
                        || name.toLowerCase(Locale.ROOT).contains("ăn trưa")
                        || name.toLowerCase(Locale.ROOT).contains("cơm trưa");

                BigDecimal calculatedAllowance;
                StringBuilder note = new StringBuilder(name);

                if ("THEO_NGAY_CONG".equalsIgnoreCase(basis)) {
                    BigDecimal eligibleDays = actualWorkDays;
                    if (isLunch && forfeitedLunchDays > 0) {
                        eligibleDays = eligibleDays.subtract(BigDecimal.valueOf(forfeitedLunchDays)).max(BigDecimal.ZERO);
                        note.append(String.format(" — Cắt %d ngày cơm trưa do đi trễ quá 1/2 ca (%s)",
                                forfeitedLunchDays, forfeitedDatesText));
                    } else {
                        note.append(String.format(" (%s ngày công x %s đ)",
                                eligibleDays.stripTrailingZeros().toPlainString(), rawAmt.setScale(0, RoundingMode.HALF_UP)));
                    }
                    calculatedAllowance = rawAmt.multiply(eligibleDays)
                            .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
                } else {
                    calculatedAllowance = rawAmt.setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
                    if (isLunch && forfeitedLunchDays > 0 && standardDays.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal dailyLunchValue = rawAmt.divide(standardDays, 4, RoundingMode.HALF_UP);
                        BigDecimal lunchPenalty = dailyLunchValue.multiply(BigDecimal.valueOf(forfeitedLunchDays))
                                .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
                        calculatedAllowance = calculatedAllowance.subtract(lunchPenalty).max(BigDecimal.ZERO);
                        note.append(String.format(" — Trừ %s đ (%d ngày trễ > 1/2 ca: %s)",
                                lunchPenalty.toPlainString(), forfeitedLunchDays, forfeitedDatesText));
                    } else {
                        note.append(" (Cố định theo kỳ)");
                    }
                }

                if (calculatedAllowance.compareTo(BigDecimal.ZERO) > 0) {
                    totalAllowance = totalAllowance.add(calculatedAllowance);
                    if (isLunch) {
                        taxExemptLunchAllowance = taxExemptLunchAllowance.add(calculatedAllowance).min(LUNCH_TAX_EXEMPT_CAP);
                    }
                    lines.add(buildLine(
                            payrollDetailId, lineNo++, "PHU_CAP", name, "ADD",
                            BigDecimal.ONE, calculatedAllowance, BigDecimal.ONE, calculatedAllowance, note.toString()
                    ));
                }
            }
        }

        // 4. KHEN THƯỞNG ĐÃ DUYỆT TRONG KỲ
        BigDecimal bonusPay = (approvedBonusAmount != null && approvedBonusAmount.compareTo(BigDecimal.ZERO) > 0)
                ? approvedBonusAmount.setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE)
                : BigDecimal.ZERO;
        if (bonusPay.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "THUONG", "Tiền thưởng theo quyết định", "ADD",
                    BigDecimal.ONE, bonusPay, BigDecimal.ONE, bonusPay,
                    "Tổng các quyết định khen thưởng đã duyệt có hiệu lực trong kỳ"
            ));
        }

        BigDecimal totalGross = workSalary.add(overtimePay).add(totalAllowance).add(bonusPay);

        // 5. PHẠT ĐI TRỄ (Trễ 1 tiếng trừ 1 tiếng, trễ 2 tiếng trừ 2 tiếng)
        long totalLateMinutes = safeDailyRecords.stream()
                .mapToLong(DailyWorkEvidenceDTO::lateMinutes)
                .sum();
        if (totalLateMinutes > 0) {
            BigDecimal lateHours = BigDecimal.valueOf(totalLateMinutes)
                    .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
            BigDecimal exactLateHours = BigDecimal.valueOf(totalLateMinutes)
                    .divide(MINUTES_PER_HOUR, 6, RoundingMode.HALF_UP);
            BigDecimal latePenalty = hourlyRate.multiply(exactLateHours)
                    .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);

            if (latePenalty.compareTo(BigDecimal.ZERO) > 0) {
                String lateDaysDetail = safeDailyRecords.stream()
                        .filter(d -> d.lateMinutes() > 0)
                        .map(d -> d.workDate() + ": trễ " + d.lateMinutes() + "p")
                        .collect(Collectors.joining("; "));

                lines.add(buildLine(
                        payrollDetailId, lineNo++, "KHAU_TRU", "Khấu trừ đi trễ", "DEDUCT",
                        lateHours, hourlyRate.setScale(0, RoundingMode.HALF_UP), BigDecimal.ONE, latePenalty,
                        String.format("Tổng thời gian đi trễ: %d phút (%s giờ) — Trừ tương ứng theo giờ công [%s]",
                                totalLateMinutes, lateHours.stripTrailingZeros().toPlainString(), lateDaysDetail)
                ));
            }
        }

        // 6. KHẤU TRỪ NGHỈ KHÔNG PHÉP
        if (unexcusedDays.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal unexcusedPenalty = dailyRate.multiply(unexcusedDays)
                    .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
            if (unexcusedPenalty.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(buildLine(
                        payrollDetailId, lineNo++, "KHAU_TRU", "Khấu trừ nghỉ không phép", "DEDUCT",
                        unexcusedDays, dailyRate.setScale(0, RoundingMode.HALF_UP), BigDecimal.ONE, unexcusedPenalty,
                        String.format("Nghỉ vắng mặt không phép: %s ngày x %s đ/ngày",
                                unexcusedDays.stripTrailingZeros().toPlainString(),
                                dailyRate.setScale(0, RoundingMode.HALF_UP).toPlainString())
                ));
            }
        }

        // 7. KHẤU TRỪ KỶ LUẬT / BỒI THƯỜNG
        if (approvedDisciplineDeduction != null && approvedDisciplineDeduction.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discDeduct = approvedDisciplineDeduction.setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "KHAU_TRU", "Khấu trừ kỷ luật / bồi thường", "DEDUCT",
                    BigDecimal.ONE, discDeduct, BigDecimal.ONE, discDeduct,
                    "Khấu trừ theo quyết định kỷ luật / bồi thường đã được phê duyệt"
            ));
        }

        // 8. TRÍCH ĐÓNG BẢO HIỂM BẮT BUỘC (10.5%)
        BigDecimal insuranceAmount = baseSalary.multiply(PayrollConstants.TOTAL_INSURANCE_RATE)
                .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
        if (insuranceAmount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "BAO_HIEM", "Bảo hiểm bắt buộc (BHXH 8%, BHYT 1.5%, BHTN 1%)", "DEDUCT",
                    BigDecimal.ONE, insuranceAmount, BigDecimal.ONE, insuranceAmount,
                    "10.5% x Lương cơ bản hợp đồng (" + baseSalary.setScale(0, RoundingMode.HALF_UP).toPlainString() + " đ)"
            ));
        }

        // 9. THUẾ THU NHẬP CÁ NHÂN (TNCN)
        BigDecimal taxableIncome = totalGross
                .subtract(taxExemptLunchAllowance)
                .subtract(insuranceAmount)
                .subtract(PayrollConstants.GIAM_TRU_BAN_THAN);

        BigDecimal pitTax = calculateProgressiveTax(taxableIncome);
        if (pitTax.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "THUE_TNCN", "Thuế thu nhập cá nhân (TNCN)", "DEDUCT",
                    BigDecimal.ONE, pitTax, BigDecimal.ONE, pitTax,
                    "Tính theo biểu thuế lũy tiến trên thu nhập tính thuế: " + taxableIncome.setScale(0, RoundingMode.HALF_UP).toPlainString() + " đ"
            ));
        }

        return lines;
    }

    private BigDecimal calculateProgressiveTax(BigDecimal taxableIncome) {
        if (taxableIncome == null || taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double inc = taxableIncome.doubleValue();
        double tax;
        if (inc <= 5_000_000) {
            tax = inc * 0.05;
        } else if (inc <= 10_000_000) {
            tax = 250_000 + (inc - 5_000_000) * 0.10;
        } else if (inc <= 18_000_000) {
            tax = 750_000 + (inc - 10_000_000) * 0.15;
        } else if (inc <= 32_000_000) {
            tax = 1_950_000 + (inc - 18_000_000) * 0.20;
        } else if (inc <= 52_000_000) {
            tax = 4_750_000 + (inc - 32_000_000) * 0.25;
        } else if (inc <= 80_000_000) {
            tax = 9_750_000 + (inc - 52_000_000) * 0.30;
        } else {
            tax = 18_150_000 + (inc - 80_000_000) * 0.35;
        }
        return BigDecimal.valueOf(tax).setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
    }

    private PayrollLine buildLine(
            Long payrollDetailId, int lineNo, String code, String name, String direction,
            BigDecimal quantity, BigDecimal unitRate, BigDecimal multiplier, BigDecimal amount, String explanation) {

        SalaryComponent comp = componentRepository.findByComponentCode(code)
                .orElseGet(() -> componentRepository.findAll().stream()
                        .filter(c -> c.getDirection() != null && direction.equalsIgnoreCase(c.getDirection().name()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Thiếu cấu hình danh mục salary_components cho mã: " + code)));

        PayrollLine line = new PayrollLine();
        line.setPayrollDetailId(payrollDetailId);
        line.setComponentId(comp.getComponentId());
        line.setLineNo(lineNo);
        line.setComponentCodeSnapshot(code);
        line.setComponentNameSnapshot(name);
        line.setDirectionSnapshot(direction);
        line.setQuantity(quantity);
        line.setUnitRate(unitRate);
        line.setMultiplier(multiplier);
        line.setAmount(amount.max(BigDecimal.ZERO));
        line.setExplanation(explanation);
        return line;
    }
}