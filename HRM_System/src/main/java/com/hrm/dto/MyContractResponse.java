package com.hrm.dto;

import com.hrm.enums.ContractStatus;
import com.hrm.enums.ContractTermKind;
import com.hrm.enums.ProbationGroup;
import com.hrm.enums.TerminationReasonType;
import java.time.LocalDate;

public record MyContractResponse(
        Long contractId, String contractNumber, Long contractTypeId, String contractTypeName,
        ContractTermKind termKind, ProbationGroup probationGroup, LocalDate signedDate,
        LocalDate startDate, LocalDate endDate, ContractStatus status, TerminationReasonType reasonType) {}
