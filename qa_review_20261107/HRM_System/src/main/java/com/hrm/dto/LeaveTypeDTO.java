package com.hrm.dto;

public record LeaveTypeDTO(
        Long leaveTypeId,
        String typeCode,
        String typeName,
        Boolean isPaid,
        Boolean isActive
) {
}