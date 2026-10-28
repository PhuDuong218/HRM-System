
package com.hrm.service;

import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.repository.EmployeeStatisticsRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class EmployeeStatisticsService {

    private final EmployeeStatisticsRepository repository;

    public EmployeeStatisticsService(EmployeeStatisticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public EmployeeStatisticsResponse getEmployeeStatistics() {

        LocalDate asOfDate = LocalDate.now(
                ZoneId.of("Asia/Ho_Chi_Minh"));

        return new EmployeeStatisticsResponse(
                repository.countByDepartment(asOfDate),
                repository.countByPosition(asOfDate),
                repository.countByEmploymentStatus(),
                repository.countByQualification());
    }
}
