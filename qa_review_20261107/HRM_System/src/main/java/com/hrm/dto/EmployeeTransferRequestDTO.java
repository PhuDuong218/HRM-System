package com.hrm.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record EmployeeTransferRequestDTO(

        @NotNull(message = "Phòng ban không được để trống")
        @jakarta.validation.constraints.Positive(message = "ID phòng ban phải lớn hơn 0")
        Long departmentId,

        @NotNull(message = "Chức vụ không được để trống")
        @jakarta.validation.constraints.Positive(message = "ID chức vụ phải lớn hơn 0")
        Long positionId,

        @NotNull(message = "Ngày điều chuyển không được để trống")
        LocalDate effectiveFrom,
        @jakarta.validation.constraints.Size(max=1000) String reason

) {
    public EmployeeTransferRequestDTO(Long departmentId,Long positionId,LocalDate effectiveFrom) { this(departmentId,positionId,effectiveFrom,null); }
}
