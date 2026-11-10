package com.hrm.dto;

import java.math.BigDecimal;
import java.time.*;
import com.hrm.enums.*;

public record DecisionResponse(Long decisionId, Long employeeId, String decisionNumber,
        DecisionType decisionType, String content, LocalDate issuedDate, Long issuedBy,
        DecisionApprovalStatus approvalStatus, Long approvedBy, LocalDateTime approvedAt,
        DecisionPayrollEffect payrollEffect, BigDecimal amount, LocalDate applyDate,
        DisciplineForm disciplineForm, LocalDate violationDate, Integer salaryIncreaseDelayMonths,String reviewBlocker) {
    public DecisionResponse(Long decisionId,Long employeeId,String decisionNumber,DecisionType decisionType,String content,LocalDate issuedDate,Long issuedBy,DecisionApprovalStatus approvalStatus,Long approvedBy,LocalDateTime approvedAt,DecisionPayrollEffect payrollEffect,BigDecimal amount,LocalDate applyDate,DisciplineForm disciplineForm,LocalDate violationDate,Integer salaryIncreaseDelayMonths){
        this(decisionId,employeeId,decisionNumber,decisionType,content,issuedDate,issuedBy,approvalStatus,approvedBy,approvedAt,payrollEffect,amount,applyDate,disciplineForm,violationDate,salaryIncreaseDelayMonths,null);
    }
}
