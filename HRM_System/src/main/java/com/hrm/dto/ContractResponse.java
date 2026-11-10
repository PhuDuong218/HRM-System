package com.hrm.dto;

import java.time.LocalDate;
import com.hrm.enums.ContractStatus;

public record ContractResponse(
    Long contractId,
    Long employeeId,
    String employeeCode,
    String employeeName,
    Long contractTypeId,
    String contractTypeName,
    String contractNumber,
    LocalDate signedDate,
    LocalDate startDate,
    LocalDate endDate,
    ContractStatus status,
    com.hrm.enums.ContractTermKind termKind, com.hrm.enums.ProbationGroup probationGroup,
    com.hrm.enums.TerminationReasonType reasonType
) {}
