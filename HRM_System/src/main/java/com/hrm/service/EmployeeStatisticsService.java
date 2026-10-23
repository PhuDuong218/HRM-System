package com.hrm.service;

import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.repository.EmployeeStatisticsRepository;
import org.springframework.stereotype.Service;

@Service
public class EmployeeStatisticsService {

    private final EmployeeStatisticsRepository repository;

    public EmployeeStatisticsService(EmployeeStatisticsRepository repository) {
        this.repository = repository;
    }

    public EmployeeStatisticsResponse getEmployeeStatistics() {
        return new EmployeeStatisticsResponse(
                repository.countByDepartment(),
                repository.countByPosition(),
                repository.countByEmploymentStatus(),
                repository.countByQualification());
    }
}
