package com.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.hrm.dto.*;
import com.hrm.enums.AllowanceCalculationBasis;
import com.hrm.enums.AllowanceAmountType;
import com.hrm.exception.BusinessException;
import com.hrm.model.EmployeeAllowance;
import com.hrm.repository.EmployeeAllowanceRepository;
import com.hrm.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllowanceCalculationService {
    private final EmployeeRepository employees;
    private final EmployeeAllowanceRepository allowances;
    private final AllowancePeriodReader periods;
    private final AllowanceWorkSource workSource;
    private final SalaryRateService salaryRates;
    private final String periodPolicy;
    private final String policyVersion;

    public AllowanceCalculationService(EmployeeRepository employees, EmployeeAllowanceRepository allowances,
            AllowancePeriodReader periods, AllowanceWorkSource workSource, SalaryRateService salaryRates,
            @Value("${hrm.allowance.period-policy:CALENDAR_DAY_PRORATION}") String periodPolicy,
            @Value("${hrm.allowance.policy-version:HRM-2026.1}") String policyVersion) {
        this.employees = employees;
        this.allowances = allowances;
        this.periods = periods;
        this.workSource = workSource;
        this.salaryRates = salaryRates;
        this.periodPolicy = periodPolicy;
        this.policyVersion = policyVersion;
    }

    @Transactional
    public AllowanceCalculationResult calculate(Long employeeId, Long periodId) {
        return calculate(employeeId, periodId, false);
    }

    AllowanceCalculationResult calculateForDraft(Long employeeId, Long periodId) {
        return calculate(employeeId, periodId, true);
    }

    private AllowanceCalculationResult calculate(Long employeeId, Long periodId, boolean draft) {
        var employee = employees.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không có nhân viên"));
        var period = periods.get(periodId);
        if (period.to().isBefore(period.from()) || ChronoUnit.DAYS.between(period.from(), period.to()) > 365) {
            throw bad("Kỳ tính phải từ 1 đến 366 ngày");
        }
        var rows = allowances.findIntersecting(employeeId, period.from(), period.to());
        verifyAssignments(rows, period);
        boolean needsWork = rows.stream().anyMatch(a -> a.getCalculationBasis() != AllowanceCalculationBasis.THEO_KY);
        AllowanceWorkData work = needsWork ? workSource.load(employeeId, period.from(), period.to()) : null;
        Map<LocalDate, EligibleAllowanceDay> days = needsWork ? validateWork(work, employeeId, period) : Map.of();
        List<AllowanceLine> result = new ArrayList<>();
        Map<LocalDate, SalaryRateSegment> salaryReferences = new HashMap<>();
        int periodDays = inclusiveDays(period.from(), period.to());
        BigDecimal divisor = BigDecimal.valueOf(periodDays);
        // A shared denominator keeps the final HALF_UP exact, even when individual fractions repeat.
        BigDecimal totalNumerator = BigDecimal.ZERO;
        for (var a : rows) {
            LocalDate from = max(a.getEffectiveFrom(), period.from());
            LocalDate to = a.getEffectiveTo() == null ? period.to() : min(a.getEffectiveTo(), period.to());
            boolean calendarProration = a.getCalculationBasis() == AllowanceCalculationBasis.THEO_KY;
            if (calendarProration) {
                if (employee.getHireDate() != null) from = max(from, employee.getHireDate());
                if (employee.getTerminationDate() != null) to = min(to, employee.getTerminationDate());
                if (to.isBefore(from)) continue;
                if (!draft && (!"CALENDAR_DAY_PRORATION".equals(periodPolicy) || policyVersion == null
                        || policyVersion.isBlank() || "UNCONFIRMED".equalsIgnoreCase(policyVersion.trim()))) {
                    throw bad("THEO_KY chưa có chính sách được xác nhận");
                }
            }
            int effectiveDays = inclusiveDays(from, to);
            SalaryRateSegment salaryReference = null;
            BigDecimal unitAmount = a.getAmount();
            if (a.getAmountType() == AllowanceAmountType.PERCENT_BASE_SALARY) {
                salaryReference = salaryReferences.computeIfAbsent(a.getEffectiveFrom(),
                        date -> salaryRates.getRateAt(employeeId, date));
                unitAmount = salaryReference.baseSalary().multiply(a.getAmount()).movePointLeft(2);
            }
            BigDecimal units = BigDecimal.ZERO;
            BigDecimal raw;
            BigDecimal numerator;
            String source, version;
            if (calendarProration) {
                var coveredDays = BigDecimal.valueOf(effectiveDays);
                units = decimalRatio(coveredDays, divisor);
                numerator = unitAmount.multiply(coveredDays);
                raw = decimalRatio(numerator, divisor);
                source = "payroll_periods/" + period.periodId();
                version = draft ? "DRAFT-CALENDAR-DAY-PRORATION" : policyVersion;
            } else {
                for (int i = 0; i < effectiveDays; i++) {
                    var day = days.get(from.plusDays(i));
                    units = units.add(
                            a.getCalculationBasis() == AllowanceCalculationBasis.THEO_NGAY_CONG ? day.eligibleDays()
                                    : day.eligibleHours());
                }
                source = work.sourceReference();
                version = work.policyVersion();
                raw = unitAmount.multiply(units);
                numerator = raw.multiply(divisor);
            }
            result.add(new AllowanceLine(a.getEmployeeAllowanceId(), a.getAllowanceType().getAllowanceTypeId(),
                    a.getAllowanceType().getTypeCode(), from, to, a.getCalculationBasis(), unitAmount, units, raw,
                    source, version, a.getAllowanceType().isTaxable(), a.getAllowanceType().isCountsForSocialInsurance(),
                    effectiveDays, periodDays, a.getAmountType(), a.getAmount(), salaryReference));
            totalNumerator = totalNumerator.add(numerator);
        }
        return new AllowanceCalculationResult(employeeId, period, List.copyOf(result),
                decimalRatio(totalNumerator, divisor), "TOTAL_HALF_UP_VND",
                totalNumerator.divide(divisor, 0, RoundingMode.HALF_UP));
    }

    private int inclusiveDays(LocalDate from, LocalDate to) {
        return Math.toIntExact(ChronoUnit.DAYS.between(from, to)) + 1;
    }

    private BigDecimal decimalRatio(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, Math.max(16, numerator.scale()), RoundingMode.HALF_UP);
    }

    private void verifyAssignments(List<EmployeeAllowance> rows, AllowancePeriod p) {
        for (int i = 0; i < rows.size(); i++) {
            var a = rows.get(i);
            if (a.getAmount() == null || a.getAmount().signum() < 0 || a.getCalculationBasis() == null
                    || a.getAmountType() == null || (a.getAmountType() == AllowanceAmountType.PERCENT_BASE_SALARY
                    && a.getAmount().compareTo(BigDecimal.valueOf(100)) > 0))
                throw bad("Dữ liệu phụ cấp không hợp lệ");
            for (int j = 0; j < i; j++) {
                var b = rows.get(j);
                if (a.getAllowanceType().getAllowanceTypeId().equals(b.getAllowanceType().getAllowanceTypeId()) &&
                        !max(max(a.getEffectiveFrom(), b.getEffectiveFrom()), p.from()).isAfter(
                                min(min(a.getEffectiveTo() == null ? p.to() : a.getEffectiveTo(),
                                        b.getEffectiveTo() == null ? p.to() : b.getEffectiveTo()), p.to()))) {
                    throw bad("Nguồn có khoản cùng loại bị chồng thời gian");
                }
            }
        }
    }

    private Map<LocalDate, EligibleAllowanceDay> validateWork(AllowanceWorkData w, Long employeeId, AllowancePeriod p) {
        if (w == null || !employeeId.equals(w.employeeId()) || !p.from().equals(w.from()) || !p.to().equals(w.to()) ||
                w.days() == null || w.sourceReference() == null || w.sourceReference().isBlank() ||
                w.policyVersion() == null || w.policyVersion().isBlank())
            throw bad("Nguồn công sai phạm vi hoặc thiếu căn cứ");
        Map<LocalDate, EligibleAllowanceDay> map = new HashMap<>();
        for (var d : w.days()) {
            if (d == null || d.date() == null || d.date().isBefore(p.from()) || d.date().isAfter(p.to()) ||
                    d.eligibleDays() == null || d.eligibleHours() == null || d.eligibleDays().signum() < 0 ||
                    d.eligibleHours().signum() < 0 || d.eligibleDays().compareTo(BigDecimal.ONE) > 0 ||
                    d.eligibleHours().compareTo(new BigDecimal("24")) > 0 || map.putIfAbsent(d.date(), d) != null) {
                throw bad("Nguồn công có ngày trùng/ngoài kỳ hoặc số công/giờ không hợp lệ");
            }
        }
        if (map.size() != ChronoUnit.DAYS.between(p.from(), p.to()) + 1)
            throw bad("Nguồn công thiếu ngày; ngày không hưởng phải trả rõ 0");
        return map;
    }

    private LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private BusinessException bad(String m) {
        return new BusinessException(HttpStatus.CONFLICT, m);
    }
}
