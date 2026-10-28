package com.hrm.service.provider;

import com.hrm.dto.EmployeeSalaryProfileDTO;
import java.time.LocalDate;
import java.util.List;

/**
 * P-06: Interface cung cấp thông tin lương hợp đồng và phụ cấp
 */
public interface SalaryContractProvider {

    /**
     * Lấy hồ sơ lương & phụ cấp của một nhân viên trong kỳ
     */
    EmployeeSalaryProfileDTO getSalaryProfile(Long employeeId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Lấy danh sách hồ sơ lương của tất cả nhân viên đang làm việc trong kỳ
     */
    List<EmployeeSalaryProfileDTO> getAllActiveSalaryProfiles(LocalDate periodStart, LocalDate periodEnd);
}