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
    public WorkStatisticsResponse summarize(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate và toDate là bắt buộc");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate không được sau toDate");
        }

        // The exclusive end must also fit SQL Server's datetime2 range.
        if (fromDate.getYear() < 1 || toDate.isAfter(LAST_REPORT_DATE)) {
            throw new IllegalArgumentException("Kỳ thống kê phải nằm từ 0001-01-01 đến 9999-12-30");
        }

        LocalDateTime startInclusive = fromDate.atStartOfDay();
        LocalDateTime endExclusive = toDate.plusDays(1).atStartOfDay();
        WorkStatisticsRow row = repository.summarizeAttendance(fromDate, toDate)
                .orElseThrow(() -> new IllegalStateException("Không thể tổng hợp thống kê công"));

        List<WorkIntervalRow> leaveIntervals = repository.findApprovedLeaveIntervals(startInclusive, endExclusive);
        List<OvertimeIntervalRow> overtimeIntervals = repository.findConfirmedOvertimeIntervals(startInclusive, endExclusive);
        BigDecimal leaveMillis = BigDecimal.ZERO;
        for (WorkIntervalRow interval : leaveIntervals) {
            leaveMillis = leaveMillis.add(overlapMillis(interval, startInclusive, endExclusive));
        }

        BigDecimal overtimeMillis = BigDecimal.ZERO;
        for (OvertimeIntervalRow interval : overtimeIntervals) {
            BigDecimal fullMillis = elapsedMillis(interval.getStartsAt(), interval.getEndsAt());
            BigDecimal breakMillis = MILLIS_PER_MINUTE.multiply(BigDecimal.valueOf(interval.getBreakMinutes()));
            BigDecimal netMillis = fullMillis.subtract(breakMillis).max(BigDecimal.ZERO);
            // No break timestamps are stored: allocate net time by the overlap ratio.
            BigDecimal allocatedMillis = netMillis.multiply(overlapMillis(interval, startInclusive, endExclusive))
                    .divide(fullMillis, MathContext.DECIMAL128);
            overtimeMillis = overtimeMillis.add(allocatedMillis);
        }

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
        return value == null ? 0 : value;
    }

    private BigDecimal overlapMillis(WorkIntervalRow interval, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        LocalDateTime start = interval.getStartsAt().isBefore(startInclusive) ? startInclusive : interval.getStartsAt();
        LocalDateTime end = interval.getEndsAt().isAfter(endExclusive) ? endExclusive : interval.getEndsAt();
        return start.isBefore(end) ? elapsedMillis(start, end) : BigDecimal.ZERO;
    }

    private BigDecimal elapsedMillis(LocalDateTime start, LocalDateTime end) {
        return BigDecimal.valueOf(Duration.between(start, end).toMillis());
    }

    private BigDecimal toHours(BigDecimal millis) {
        return millis.divide(MILLIS_PER_HOUR, 4, RoundingMode.HALF_UP);
    }
}
