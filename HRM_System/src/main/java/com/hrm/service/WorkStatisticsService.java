
package com.hrm.service;

import com.hrm.dto.WorkStatisticsResponse;
import com.hrm.repository.WorkStatisticsRepository;
import com.hrm.repository.projection.OvertimeIntervalRow;
import com.hrm.repository.projection.WorkIntervalRow;
import com.hrm.repository.projection.WorkStatisticsRow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkStatisticsService {

    private static final BigDecimal MILLIS_PER_MINUTE = BigDecimal.valueOf(60_000);

    private static final BigDecimal MILLIS_PER_HOUR = BigDecimal.valueOf(3_600_000);

    private static final LocalDate LAST_REPORT_DATE = LocalDate.of(9999, 12, 30);

    private final WorkStatisticsRepository repository;

    public WorkStatisticsService(WorkStatisticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WorkStatisticsResponse summarize(
            LocalDate fromDate,
            LocalDate toDate) {

        // 1. Kiem tra tham so
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "fromDate và toDate là bắt buộc");
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate không được sau toDate");
        }

        // 2. Gioi han ngay hop le cho SQL Server datetime2
        if (fromDate.getYear() < 1
                || toDate.isAfter(LAST_REPORT_DATE)) {

            throw new IllegalArgumentException(
                    "Kỳ thống kê phải nằm từ 0001-01-01 đến 9999-12-30");
        }

        // 3. Xac dinh khoang bao cao [start, end)
        LocalDateTime startInclusive = fromDate.atStartOfDay();
        LocalDateTime endExclusive = toDate.plusDays(1).atStartOfDay();

        // 4. Thong ke cham cong da xac nhan
        WorkStatisticsRow row = repository
                .summarizeAttendance(fromDate, toDate)
                .orElseThrow(() -> new IllegalStateException(
                        "Không thể tổng hợp thống kê công"));

        // 5. Lay cac khoang nghi phep duoc duyet
        List<WorkIntervalRow> leaveIntervals = repository.findApprovedLeaveIntervals(
                startInclusive,
                endExclusive);

        BigDecimal leaveMillis = BigDecimal.ZERO;

        for (WorkIntervalRow interval : leaveIntervals) {
            leaveMillis = leaveMillis.add(
                    overlapMillis(
                            interval.getStartsAt(),
                            interval.getEndsAt(),
                            startInclusive,
                            endExclusive));
        }

        // 6. Lay cac khoang OT da xac nhan
        List<OvertimeIntervalRow> overtimeIntervals = repository.findConfirmedOvertimeIntervals(
                startInclusive,
                endExclusive);

        BigDecimal overtimeMillis = BigDecimal.ZERO;

        for (OvertimeIntervalRow interval : overtimeIntervals) {

            LocalDateTime actualStart = interval.getStartsAt();
            LocalDateTime actualEnd = interval.getEndsAt();

            BigDecimal fullMillis = elapsedMillis(actualStart, actualEnd);

            // Bo qua khoang thoi gian khong hop le
            if (fullMillis.signum() <= 0) {
                continue;
            }

            long breakMinutes = interval.getBreakMinutes() == null
                    ? 0L
                    : interval.getBreakMinutes().longValue();

            BigDecimal breakMillis = MILLIS_PER_MINUTE
                    .multiply(BigDecimal.valueOf(breakMinutes));

            // Gio lam OT thuan
            BigDecimal netMillis = fullMillis
                    .subtract(breakMillis)
                    .max(BigDecimal.ZERO);

            // Thoi gian OT giao voi ky bao cao
            BigDecimal overlap = overlapMillis(
                    actualStart,
                    actualEnd,
                    startInclusive,
                    endExclusive);

            // Phan bo gio thuan theo ty le giao nhau
            BigDecimal allocatedMillis = netMillis
                    .multiply(overlap)
                    .divide(fullMillis, MathContext.DECIMAL128);

            overtimeMillis = overtimeMillis.add(allocatedMillis);
        }

        // 7. Tra ket qua thong ke
        return new WorkStatisticsResponse(
                fromDate,
                toDate,
                valueOrZero(row.getConfirmedWorkDays()),
                valueOrZero(row.getConfirmedAbsentDays()),
                leaveIntervals.size(),
                toHours(leaveMillis),
                overtimeIntervals.size(),
                toHours(overtimeMillis));
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    // Tinh phan giao nhau giua hai khoang thoi gian
    private BigDecimal overlapMillis(
            LocalDateTime intervalStart,
            LocalDateTime intervalEnd,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {

        if (intervalStart == null || intervalEnd == null) {
            return BigDecimal.ZERO;
        }

        LocalDateTime start = intervalStart.isBefore(startInclusive)
                ? startInclusive
                : intervalStart;

        LocalDateTime end = intervalEnd.isAfter(endExclusive)
                ? endExclusive
                : intervalEnd;

        return start.isBefore(end)
                ? elapsedMillis(start, end)
                : BigDecimal.ZERO;
    }

    // Tinh tong mili giay cua khoang thoi gian
    private BigDecimal elapsedMillis(
            LocalDateTime start,
            LocalDateTime end) {

        return BigDecimal.valueOf(
                Duration.between(start, end).toMillis());
    }

    // Doi mili giay sang gio
    private BigDecimal toHours(BigDecimal millis) {
        return millis.divide(
                MILLIS_PER_HOUR,
                4,
                RoundingMode.HALF_UP);
    }
}
