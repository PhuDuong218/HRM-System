package com.hrm.dto;

import java.time.LocalDate;
import java.util.List;

public record ResignationReadinessDTO(long requestId,long employeeId,String employeeCode,String employeeName,
        LocalDate lastWorkingDate,boolean canApprove,String approvalMessage,List<Issue> issues) {
    public record Record(long id,String label,String from,String to) {}
    public record Issue(String code,String title,long count,String instruction,String route,List<Record> records) {}
}
