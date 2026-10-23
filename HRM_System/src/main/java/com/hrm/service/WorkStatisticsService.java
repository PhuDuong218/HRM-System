package com.hrm.service;

import com.hrm.dto.WorkStatisticsResponse;
import com.hrm.repository.WorkStatisticsRepository;
import com.hrm.repository.projection.WorkStatisticsRow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class WorkStatisticsService {

    private final WorkStatisticsRepository repository;

    public WorkStatisticsService(WorkStatisticsRepository repository) {
        this.repository = repository;
    }

    public WorkStatisticsResponse summarize(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate và toDate là bắt buộc");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate không được sau toDate");
        }

        WorkStatisticsRow row = repository.summarize(fromDate, toDate)
                .orElseThrow(() -> new IllegalStateException("Không thể tổng hợp thống kê công"));

        return new WorkStatisticsResponse(
                fromDate,
                toDate,
                valueOrZero(row.getConfirmedWorkDays()),
                valueOrZero(row.getConfirmedAbsentDays()),
                valueOrZero(row.getApprovedLeaveRequests()),
                decimalOrZero(row.getApprovedLeaveHours()),
                valueOrZero(row.getApprovedOvertimeRequests()),
                decimalOrZero(row.getApprovedOvertimeHours()));
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal decimalOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
