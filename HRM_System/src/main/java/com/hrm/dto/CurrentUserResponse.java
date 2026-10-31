package com.hrm.dto;

import java.time.LocalDate;

public record CurrentUserResponse(
        String username, String role, String employeeCode, String fullName,
        LocalDate birthDate, String email, String phone, String address,
        LocalDate hireDate, String employmentStatus, String departmentName, String positionName) {
}
