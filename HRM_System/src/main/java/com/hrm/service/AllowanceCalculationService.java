package com.hrm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.hrm.dto.*;
import com.hrm.enums.AllowanceCalculationBasis;
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
    private final String periodPolicy;
    private final String policyVersion;

    public AllowanceCalculationService(EmployeeRepository employees, EmployeeAllowanceRepository allowances,
            AllowancePeriodReader periods, AllowanceWorkSource workSource,
            @Value("${hrm.allowance.period-policy:UNCONFIRMED}") String periodPolicy,
            @Value("${hrm.allowance.policy-version:UNCONFIRMED}") String policyVersion) {
        this.employees = employees;
        this.allowances = allowances;
        this.periods = periods;
        this.workSource = workSource;
        this.periodPolicy = periodPolicy;
        this.policyVersion = policyVersion;
    }

    @Transactional
    public AllowanceCalculationResult calculate(Long employeeId, Long periodId) {
        employees.findByIdForUpdate(employeeId)
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
        BigDecimal total = BigDecimal.ZERO;
        for (var a : rows) {
            LocalDate from = max(a.getEffectiveFrom(), period.from());
            LocalDate to = a.getEffectiveTo() == null ? period.to() : min(a.getEffectiveTo(), period.to());
            BigDecimal units = BigDecimal.ZERO;
            String source, version;
            if (a.getCalculationBasis() == AllowanceCalculationBasis.THEO_KY) {
                if (!"FULL_COVERAGE_ONLY".equals(periodPolicy) || policyVersion == null || policyVersion.isBlank()
                        || "UNCONFIRMED".equals(policyVersion)) {
                    throw bad("THEO_KY chưa có chính sách được xác nhận");
                }
                if (!from.equals(period.from()) || !to.equals(period.to())) {
                    throw bad("Chính sách hiện chỉ hỗ trợ khoản phủ cả kỳ; chưa chốt cách tính một phần kỳ");
                }
                units = BigDecimal.ONE;
                source = "payroll_periods/" + period.periodId();
                version = policyVersion;
            } else {
                int count = Math.toIntExact(ChronoUnit.DAYS.between(from, to)) + 1;
                for (int i = 0; i < count; i++) {
                    var day = days.get(from.plusDays(i));
                    units = units.add(
                            a.getCalculationBasis() == AllowanceCalculationBasis.THEO_NGAY_CONG ? day.eligibleDays()
                                    : day.eligibleHours());
                }
                source = work.sourceReference();
                version = work.policyVersion();
            }
            BigDecimal raw = a.getAmount().multiply(units);
            result.add(new AllowanceLine(a.getEmployeeAllowanceId(), a.getAllowanceType().getAllowanceTypeId(),
                    a.getAllowanceType().getTypeCode(), from, to, a.getCalculationBasis(), a.getAmount(), units, raw,
                    source, version));
            total = total.add(raw);
        }
        return new AllowanceCalculationResult(employeeId, period, List.copyOf(result), total, "UNROUNDED");
    }

    private void verifyAssignments(List<EmployeeAllowance> rows, AllowancePeriod p) {
        for (int i = 0; i < rows.size(); i++) {
            var a = rows.get(i);
            if (a.getAmount() == null || a.getAmount().signum() < 0 || a.getCalculationBasis() == null)
                throw bad("Dữ liệu phụ cấp không hợp lệ");
            if (a.getConditionNote() != null && !a.getConditionNote().isBlank()) {
                throw bad(
                        "Khoản ID=" + a.getEmployeeAllowanceId() + " có điều kiện văn bản chưa được kiểm tra tự động");
            }
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