package com.hrm.service.provider.impl;

import com.hrm.dto.EmployeeSalaryProfileDTO;
import com.hrm.dto.EmployeeSalaryProfileDTO.AllowanceItemDTO;
import com.hrm.service.provider.SalaryContractProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class MockSalaryContractProvider implements SalaryContractProvider {

    @Override
    public EmployeeSalaryProfileDTO getSalaryProfile(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        EmployeeSalaryProfileDTO profile = new EmployeeSalaryProfileDTO();
        profile.setEmployeeId(employeeId);
        profile.setEmployeeCode("NV-" + employeeId);
        profile.setFullName("Nhân viên Mẫu " + employeeId);
        profile.setBaseSalary(new BigDecimal("15000000")); // Lương cơ bản mẫu: 15.000.000đ
        profile.setPayBasis("THANG");

        List<AllowanceItemDTO> allowances = new ArrayList<>();
        allowances.add(new AllowanceItemDTO("AN_TRUA", "Phụ cấp ăn trưa", new BigDecimal("730000"), "THEO_KY"));
        allowances.add(new AllowanceItemDTO("XANG_XE", "Phụ cấp đi lại", new BigDecimal("500000"), "THEO_KY"));
        profile.setAllowances(allowances);

        return profile;
    }

    @Override
    public List<EmployeeSalaryProfileDTO> getAllActiveSalaryProfiles(LocalDate periodStart, LocalDate periodEnd) {
        List<EmployeeSalaryProfileDTO> list = new ArrayList<>();
        // Giả lập 2 nhân viên mẫu (A và B)
        list.add(getSalaryProfile(1L, periodStart, periodEnd));
        list.add(getSalaryProfile(2L, periodStart, periodEnd));
        return list;
    }
}