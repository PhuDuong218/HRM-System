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
    private static final BigDecimal MINUTES_PER_DAY = new BigDecimal("480");
    private static final BigDecimal LUNCH_TAX_EXEMPT_CAP = new BigDecimal("730000");
    private static final BigDecimal DEFAULT_DAILY_LUNCH_RATE = new BigDecimal("30000");

    private final SalaryComponentRepository componentRepository;

    public PayrollCalculationEngine(SalaryComponentRepository componentRepository) {
        this.componentRepository = componentRepository;
    }

    /**
     * Giữ tương thích với các bài kiểm thử JUnit 3 tham số hiện có (PayrollMealExemptionTest).
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
                BigDecimal.ZERO,
                null,
                null
        );
    }

    /**
     * Giữ tương thích với hàm 7 tham số.
     */
    public List<PayrollLine> calculatePayrollLines(
            Long payrollDetailId,
            EmployeeSalaryProfileDTO profile,
            AttendanceSummaryDTO attendance,
            List<DailyWorkEvidenceDTO> dailyRecords,
            BigDecimal unexcusedAbsenceDays,
            BigDecimal approvedBonusAmount,
            BigDecimal approvedDisciplineDeduction) {
        return calculatePayrollLines(
                payrollDetailId,
                profile,
                attendance,
                dailyRecords,
                unexcusedAbsenceDays,
                approvedBonusAmount,
                approvedDisciplineDeduction,
                null,
                null
        );
    }

    /**
     * Engine tính lương chuẩn nghiệp vụ & luật định mới nhất:
     * - Phạt đi trễ / về sớm chính xác đến từng phút (1 giờ = 60 phút trừ 1 giờ lương; 2 giờ = 120 phút trừ 2 giờ lương; N phút trừ N phút lương).
     * - Đi trễ quá 1/2 ngày làm việc (> 240 phút với ca 8 tiếng): cắt phụ cấp cơm trưa của ngày đó.
     * - Nghỉ không phép: trừ nguyên ngày công theo đơn giá ngày công chuẩn.
     * - Bảo hiểm bắt buộc: 10.5% (BHXH 8%, BHYT 1.5%, BHTN 1%).
     * - Giảm trừ gia cảnh bản thân: 11.000.000 đ/tháng; miễn thuế tiền ăn giữa ca tối đa 730.000 đ/tháng; biểu thuế TNCN lũy tiến 7 bậc.
     */
    public List<PayrollLine> calculatePayrollLines(
            Long payrollDetailId,
            EmployeeSalaryProfileDTO profile,
            AttendanceSummaryDTO attendance,
            List<DailyWorkEvidenceDTO> dailyRecords,
            BigDecimal unexcusedAbsenceDays,
            BigDecimal approvedBonusAmount,
            BigDecimal approvedDisciplineDeduction,
            String bonusDecisionReason,
            String disciplineDecisionReason) {

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

        // Đơn giá ngày / giờ / phút với độ chính xác cao
        BigDecimal dailyRate = baseSalary.divide(standardDays, 8, RoundingMode.HALF_UP);
        BigDecimal hourlyRate = dailyRate.divide(HOURS_PER_DAY, 8, RoundingMode.HALF_UP);
        BigDecimal minuteRate = dailyRate.divide(MINUTES_PER_DAY, 8, RoundingMode.HALF_UP);

        // 1. LƯƠNG THEO NGÀY CÔNG
        BigDecimal baseWorkDays = actualWorkDays.add(paidLeaveDays).add(unexcusedDays);
        if (baseWorkDays.compareTo(standardDays) > 0) {
            baseWorkDays = standardDays;
        }
        BigDecimal workSalary = (baseWorkDays.compareTo(standardDays) == 0)
                ? baseSalary.setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE)
                : dailyRate.multiply(baseWorkDays).setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);

        String workExplanation = String.format(
                "Công đi làm thực tế: %s ngày + Nghỉ phép có lương: %s ngày (Công chuẩn: %s ngày; Đơn giá: %s đ/ngày%s)",
                actualWorkDays.stripTrailingZeros().toPlainString(),
                paidLeaveDays.stripTrailingZeros().toPlainString(),
                standardDays.stripTrailingZeros().toPlainString(),
                dailyRate.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                unexcusedDays.compareTo(BigDecimal.ZERO) > 0
                        ? "; tách riêng khấu trừ " + unexcusedDays.stripTrailingZeros().toPlainString() + " ngày nghỉ không phép ở mục Khấu trừ"
                        : ""
        );

        lines.add(buildLine(
                payrollDetailId, lineNo++, "LUONG_CONG", "Lương theo ngày công", "ADD",
                baseWorkDays, dailyRate.setScale(0, RoundingMode.HALF_UP), BigDecimal.ONE, workSalary,
                workExplanation
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
                    String.format("Làm thêm giờ đã duyệt & xác nhận: %s giờ × %s đ/giờ × Hệ số 150%%",
                            overtimeHours.stripTrailingZeros().toPlainString(),
                            hourlyRate.setScale(0, RoundingMode.HALF_UP).toPlainString())
            ));
        }

        // 3. PHỤ CẤP THEO HỢP ĐỒNG / CHÍNH SÁCH (VÀ XÁC ĐỊNH PHỤ CẤP ĂN TRƯA ĐỂ TÍNH MIỄN THUẾ & CẮT CƠM KHI TRỄ > 1/2 NGÀY)
        List<DailyWorkEvidenceDTO> forfeitedLunchRecords = safeDailyRecords.stream()
                .filter(DailyWorkEvidenceDTO::lunchAllowanceForfeited)
                .toList();
        int forfeitedLunchDays = forfeitedLunchRecords.size();
        String forfeitedDatesText = forfeitedLunchRecords.stream()
                .map(d -> d.workDate() + " (trễ " + d.lateMinutes() + " phút)")
                .collect(Collectors.joining(", "));

        BigDecimal totalAllowance = BigDecimal.ZERO;
        BigDecimal totalRawLunchAllowance = BigDecimal.ZERO;

        if (profile.getAllowances() != null) {
            for (EmployeeSalaryProfileDTO.AllowanceItemDTO item : profile.getAllowances()) {
                BigDecimal rawAmt = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                String code = item.getAllowanceCode() != null ? item.getAllowanceCode().toUpperCase(Locale.ROOT) : "";
                String name = item.getAllowanceName() != null ? item.getAllowanceName() : "Phụ cấp";
                String nameLower = name.toLowerCase(Locale.ROOT);
                String basis = item.getCalculationBasis() != null ? item.getCalculationBasis() : "THEO_KY";

                boolean isLunch = code.contains("AN_TRUA") || code.contains("COM_TRUA")
                        || code.contains("MEAL") || code.contains("AN_CA") || code.contains("TIEN_AN")
                        || nameLower.contains("ăn trưa") || nameLower.contains("cơm trưa")
                        || nameLower.contains("ăn ca") || nameLower.contains("tiền ăn");

                BigDecimal calculatedAllowance;
                StringBuilder note = new StringBuilder(name);

                if ("THEO_NGAY_CONG".equalsIgnoreCase(basis)) {
                    BigDecimal eligibleDays = actualWorkDays;
                    note.append(String.format(" (%s ngày công × %s đ/ngày)",
                            eligibleDays.stripTrailingZeros().toPlainString(),
                            rawAmt.setScale(0, RoundingMode.HALF_UP).toPlainString()));
                    calculatedAllowance = rawAmt.multiply(eligibleDays)
                            .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
                } else {
                    calculatedAllowance = rawAmt.setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
                    note.append(" (Định mức theo kỳ lương");
                    if (isLunch) {
                        note.append("; miễn thuế TNCN tối đa 730.000 đ/tháng");
                    }
                    note.append(")");
                }

                if (isLunch && forfeitedLunchDays > 0) {
                    note.append(String.format(" — Lưu ý: có %d ngày đi trễ > 1/2 ca (%s) bị trừ tiền cơm trưa tại mục Khấu trừ",
                            forfeitedLunchDays, forfeitedDatesText));
                }

                if (calculatedAllowance.compareTo(BigDecimal.ZERO) > 0) {
                    totalAllowance = totalAllowance.add(calculatedAllowance);
                    if (isLunch) {
                        totalRawLunchAllowance = totalRawLunchAllowance.add(calculatedAllowance);
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
            String bonusNote = (bonusDecisionReason != null && !bonusDecisionReason.isBlank())
                    ? bonusDecisionReason
                    : "Tổng các quyết định khen thưởng đã phê duyệt có hiệu lực trong kỳ lương";
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "THUONG", "Tiền thưởng theo quyết định", "ADD",
                    BigDecimal.ONE, bonusPay, BigDecimal.ONE, bonusPay, bonusNote
            ));
        }

        BigDecimal totalGross = workSalary.add(overtimePay).add(totalAllowance).add(bonusPay);

        // 5. KHẤU TRỪ ĐI TRỄ / VỀ SỚM CHÍNH XÁC ĐẾN TỪNG PHÚT
        // Quy tắc: Trễ 1 tiếng (60 phút) trừ 1 tiếng lương; trễ 2 tiếng (120 phút) trừ 2 tiếng lương; trễ N phút trừ đúng N phút lương
        long totalLateMinutes = safeDailyRecords.stream()
                .mapToLong(DailyWorkEvidenceDTO::lateMinutes)
                .sum();
        long totalEarlyMinutes = safeDailyRecords.stream()
                .mapToLong(DailyWorkEvidenceDTO::earlyLeaveMinutes)
                .sum();
        long totalViolationMinutes = totalLateMinutes + totalEarlyMinutes;

        BigDecimal latePenalty = BigDecimal.ZERO;
        if (totalViolationMinutes > 0) {
            BigDecimal violationHours = BigDecimal.valueOf(totalViolationMinutes)
                    .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
            latePenalty = minuteRate.multiply(BigDecimal.valueOf(totalViolationMinutes))
                    .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);

            if (latePenalty.compareTo(BigDecimal.ZERO) > 0) {
                String violationDaysDetail = safeDailyRecords.stream()
                        .filter(d -> d.lateMinutes() > 0 || d.earlyLeaveMinutes() > 0)
                        .map(d -> {
                            List<String> parts = new ArrayList<>();
                            if (d.lateMinutes() > 0) {
                                parts.add("trễ " + d.lateMinutes() + "p");
                            }
                            if (d.earlyLeaveMinutes() > 0) {
                                parts.add("về sớm " + d.earlyLeaveMinutes() + "p");
                            }
                            return d.workDate() + " (" + String.join(", ", parts) + ")";
                        })
                        .collect(Collectors.joining("; "));

                long hoursPart = totalViolationMinutes / 60;
                long minsPart = totalViolationMinutes % 60;
                String humanDuration = hoursPart > 0
                        ? String.format("%d phút (%d giờ %d phút)", totalViolationMinutes, hoursPart, minsPart)
                        : String.format("%d phút", totalViolationMinutes);

                lines.add(buildLine(
                        payrollDetailId, lineNo++, "KHAU_TRU", "Phạt đi trễ / về sớm (tính theo từng phút)", "DEDUCT",
                        BigDecimal.valueOf(totalViolationMinutes),
                        minuteRate.setScale(0, RoundingMode.HALF_UP),
                        BigDecimal.ONE,
                        latePenalty,
                        String.format(
                                "Tổng vi phạm giờ công: %s (Đi trễ: %d phút, Về sớm: %d phút, tương đương %s giờ) × Đơn giá %s đ/phút (%s đ/giờ). Chi tiết ngày: [%s]",
                                humanDuration,
                                totalLateMinutes,
                                totalEarlyMinutes,
                                violationHours.stripTrailingZeros().toPlainString(),
                                minuteRate.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                                hourlyRate.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                                violationDaysDetail
                        )
                ));
            }
        }

        // 6. CẮT PHỤ CẤP CƠM TRƯA KHI ĐI TRỄ QUÁ 1/2 NGÀY LÀM VIỆC (> 240 PHÚT)
        BigDecimal lunchForfeitPenalty = BigDecimal.ZERO;
        if (forfeitedLunchDays > 0) {
            BigDecimal dailyLunchRate = (totalRawLunchAllowance.compareTo(BigDecimal.ZERO) > 0 && standardDays.compareTo(BigDecimal.ZERO) > 0)
                    ? totalRawLunchAllowance.divide(standardDays, 4, RoundingMode.HALF_UP)
                    : DEFAULT_DAILY_LUNCH_RATE;
            lunchForfeitPenalty = dailyLunchRate.multiply(BigDecimal.valueOf(forfeitedLunchDays))
                    .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);

            if (lunchForfeitPenalty.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(buildLine(
                        payrollDetailId, lineNo++, "KHAU_TRU", "Trừ phụ cấp cơm trưa (đi trễ > 1/2 ngày)", "DEDUCT",
                        BigDecimal.valueOf(forfeitedLunchDays),
                        dailyLunchRate.setScale(0, RoundingMode.HALF_UP),
                        BigDecimal.ONE,
                        lunchForfeitPenalty,
                        String.format(
                                "Cắt %d ngày phụ cấp cơm trưa do đi trễ quá 1/2 ca làm việc (> 240 phút): [%s] × Đơn giá cơm %s đ/ngày",
                                forfeitedLunchDays,
                                forfeitedDatesText,
                                dailyLunchRate.setScale(0, RoundingMode.HALF_UP).toPlainString()
                        )
                ));
            }
        }

        // 7. KHẤU TRỪ NGHỈ KHÔNG PHÉP (TRỪ NGUYÊN NGÀY LƯƠNG)
        BigDecimal unexcusedPenalty = BigDecimal.ZERO;
        if (unexcusedDays.compareTo(BigDecimal.ZERO) > 0) {
            unexcusedPenalty = dailyRate.multiply(unexcusedDays)
                    .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
            if (unexcusedPenalty.compareTo(BigDecimal.ZERO) > 0) {
                String absentDates = safeDailyRecords.stream()
                        .filter(d -> "VANG_KHONG_PHEP".equalsIgnoreCase(d.attendanceResult()))
                        .map(d -> String.valueOf(d.workDate()))
                        .collect(Collectors.joining(", "));
                String absentDateSuffix = absentDates.isEmpty() ? "" : " — Ngày vắng: [" + absentDates + "]";

                lines.add(buildLine(
                        payrollDetailId, lineNo++, "KHAU_TRU", "Khấu trừ nghỉ không phép", "DEDUCT",
                        unexcusedDays, dailyRate.setScale(0, RoundingMode.HALF_UP), BigDecimal.ONE, unexcusedPenalty,
                        String.format("Nghỉ vắng mặt không phép: %s ngày × %s đ/ngày%s",
                                unexcusedDays.stripTrailingZeros().toPlainString(),
                                dailyRate.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                                absentDateSuffix)
                ));
            }
        }

        // 8. KHẤU TRỪ KỶ LUẬT / BỒI THƯỜNG THEO QUYẾT ĐỊNH
        if (approvedDisciplineDeduction != null && approvedDisciplineDeduction.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discDeduct = approvedDisciplineDeduction.setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
            String discNote = (disciplineDecisionReason != null && !disciplineDecisionReason.isBlank())
                    ? disciplineDecisionReason
                    : "Khấu trừ theo quyết định kỷ luật / bồi thường trách nhiệm vật chất đã được phê duyệt";
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "KHAU_TRU", "Khấu trừ kỷ luật / bồi thường", "DEDUCT",
                    BigDecimal.ONE, discDeduct, BigDecimal.ONE, discDeduct, discNote
            ));
        }

        // 9. TRÍCH ĐÓNG BẢO HIỂM BẮT BUỘC (10.5% = BHXH 8% + BHYT 1.5% + BHTN 1%)
        BigDecimal insuranceAmount = baseSalary.multiply(PayrollConstants.TOTAL_INSURANCE_RATE)
                .setScale(PayrollConstants.MONEY_DECIMAL_SCALE, PayrollConstants.DEFAULT_ROUNDING_MODE);
        if (insuranceAmount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "BAO_HIEM", "Bảo hiểm bắt buộc (BHXH 8%, BHYT 1.5%, BHTN 1%)", "DEDUCT",
                    BigDecimal.ONE, insuranceAmount, BigDecimal.ONE, insuranceAmount,
                    String.format("Trích đóng 10.5%% (BHXH 8%% + BHYT 1.5%% + BHTN 1%%) × Lương cơ bản hợp đồng %s đ",
                            baseSalary.setScale(0, RoundingMode.HALF_UP).toPlainString())
            ));
        }

        // 10. THUẾ THU NHẬP CÁ NHÂN (TNCN) THEO LUẬT ĐỊNH
        // Thu nhập chịu thuế = Tổng thu nhập thực nhận (sau khi trừ khoản bị cắt do trễ/vắng/cắt cơm) - phần ăn ca miễn thuế (tối đa 730.000đ)
        BigDecimal effectiveLunchAllowance = totalRawLunchAllowance.subtract(lunchForfeitPenalty).max(BigDecimal.ZERO);
        BigDecimal taxExemptLunchAllowance = effectiveLunchAllowance.min(LUNCH_TAX_EXEMPT_CAP);
        BigDecimal effectiveGrossForTax = totalGross
                .subtract(latePenalty)
                .subtract(lunchForfeitPenalty)
                .subtract(unexcusedPenalty)
                .max(BigDecimal.ZERO);

        BigDecimal taxableIncome = effectiveGrossForTax
                .subtract(taxExemptLunchAllowance)
                .subtract(insuranceAmount)
                .subtract(PayrollConstants.GIAM_TRU_BAN_THAN);

        BigDecimal pitTax = calculateProgressiveTax(taxableIncome);
        if (pitTax.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(
                    payrollDetailId, lineNo++, "THUE_TNCN", "Thuế thu nhập cá nhân (TNCN)", "DEDUCT",
                    BigDecimal.ONE, pitTax, BigDecimal.ONE, pitTax,
                    String.format(
                            "Thuế TNCN lũy tiến 7 bậc (Giảm trừ gia cảnh bản thân: 11.000.000 đ; Miễn thuế ăn ca: %s đ; BH bắt buộc: %s đ; Thu nhập tính thuế: %s đ)",
                            taxExemptLunchAllowance.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                            insuranceAmount.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                            taxableIncome.setScale(0, RoundingMode.HALF_UP).toPlainString()
                    )
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
