package com.hrm.service;

import com.hrm.dto.ContractStatisticsResponse;
import com.hrm.repository.ContractStatisticsRepository;
import com.hrm.repository.projection.ContractStatisticsRow;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ContractStatisticsService {

    private final ContractStatisticsRepository repository;

    public ContractStatisticsService(ContractStatisticsRepository repository) {
        this.repository = repository;
    }

    public ContractStatisticsResponse summarize(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate reportDate,
            int expiringWithinDays,
            Long departmentId) {
        if (fromDate == null || toDate == null || reportDate == null) {
            throw new IllegalArgumentException("fromDate, toDate và reportDate là bắt buộc");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate không được sau toDate");
        }
        if (expiringWithinDays < 0) {
            throw new IllegalArgumentException("expiringWithinDays không được âm");
        }

        ContractStatisticsRow row = repository.summarize(
                        fromDate, toDate, reportDate, expiringWithinDays, departmentId)
                .orElseThrow(() -> new IllegalStateException("Không thể tổng hợp thống kê hợp đồng"));

        return new ContractStatisticsResponse(
                fromDate,
                toDate,
                reportDate,
                departmentId,
                expiringWithinDays,
                valueOrZero(row.getContractCount()),
                valueOrZero(row.getEffectiveCount()),
                valueOrZero(row.getExpiringCount()));
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }
}
