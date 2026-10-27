package com.hrm.duong;

import java.util.*;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class EmployeeDecisionServiceTest {
    EmployeeDecisionRepository repo;EmployeeRepository employees;DecisionActorProvider actors;DecisionAuditService audit;PayrollHistoryGuard history;EmployeeDecisionService service;EmployeeDecision row;
    @BeforeEach void setup(){repo=mock(EmployeeDecisionRepository.class);employees=mock(EmployeeRepository.class);actors=mock(DecisionActorProvider.class);audit=mock(DecisionAuditService.class);history=mock(PayrollHistoryGuard.class);row=decision(3,DecisionPayrollEffect.ADD);when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(row.getEmployee()));when(actors.requireManager()).thenReturn(new DecisionActor(20L,200L));when(repo.findById(3L)).thenReturn(Optional.of(row));when(repo.findEmployeeIdById(3L)).thenReturn(Optional.of(1L));when(repo.findByIdForUpdate(3L)).thenReturn(Optional.of(row));when(repo.saveAndFlush(any())).thenAnswer(x->{var d=(EmployeeDecision)x.getArgument(0);if(d.getDecisionId()==null)id(d,"decisionId",4);return d;});service=service("TEST-ONLY-v1");}
    EmployeeDecisionService service(String version){return new EmployeeDecisionService(repo,employees,actors,audit,history,new DecisionPayrollPolicy(version),CLOCK);}
    @Test void createIsAlwaysPendingWithServerActor(){var r=service.create(decisionRequest(DecisionPayrollEffect.NONE));assertEquals(DecisionApprovalStatus.CHO_DUYET,r.approvalStatus());assertEquals(20L,r.issuedBy());assertNull(r.approvedBy());assertNull(r.approvedAt());verify(audit).record(eq(20L),eq(4L),eq("DECISION_CREATE"),anyString(),isNull(),any());}
    @Test void duplicateNumberIs409(){when(repo.existsByDecisionNumber("QD_NEW")).thenReturn(true);status(409,()->service.create(decisionRequest(DecisionPayrollEffect.NONE)));verify(repo,never()).saveAndFlush(any());}
    @Test void noneMustNotHaveMoneyOrApplyDate(){var r=decisionRequest(DecisionPayrollEffect.ADD);status(400,()->service.create(new DecisionRequest(1L,"NONE",r.decisionType(),r.content(),TODAY,DecisionPayrollEffect.NONE,r.amount(),r.applyDate(),"Test")));}
    @Test void moneyRequiresPositiveAmountAndDate(){var r=decisionRequest(DecisionPayrollEffect.ADD);status(400,()->service.create(new DecisionRequest(1L,"BAD",r.decisionType(),r.content(),TODAY,r.payrollEffect(),money("0"),TODAY,"Test")));status(400,()->service.create(new DecisionRequest(1L,"BAD",r.decisionType(),r.content(),TODAY,r.payrollEffect(),r.amount(),null,"Test")));}
    @Test void applyDateCannotPrecedeIssue(){var r=decisionRequest(DecisionPayrollEffect.ADD);status(400,()->service.create(new DecisionRequest(1L,"BAD",r.decisionType(),r.content(),TODAY,r.payrollEffect(),r.amount(),TODAY.minusDays(1),"Test")));}
    @Test void issuerCannotApproveOwnDecision(){when(actors.requireManager()).thenReturn(new DecisionActor(10L,100L));status(403,()->service.approve(3L,new DecisionActionRequest("Test")));verify(repo,never()).saveAndFlush(any());}
    @Test void recipientCannotReviewOwnDecision(){when(actors.requireManager()).thenReturn(new DecisionActor(20L,1L));status(403,()->service.reject(3L,new DecisionActionRequest("Test")));}
    @Test void unconfirmedMoneyPolicyDoesNotChangeApprovalFields(){status(409,()->service("UNCONFIRMED").approve(3L,new DecisionActionRequest("Test")));assertEquals(DecisionApprovalStatus.CHO_DUYET,row.getApprovalStatus());assertNull(row.getApprovedBy());assertNull(row.getApprovedAt());}
    @Test void closedPayrollPreventsApproval(){doThrow(new com.hrm.exception.BusinessException(org.springframework.http.HttpStatus.CONFLICT,"closed")).when(history).assertMutable(1L,TODAY,TODAY);status(409,()->service.approve(3L,new DecisionActionRequest("Test")));assertNull(row.getApprovedBy());}
    @Test void approveSetsStatusActorAndTimestampTogether(){var r=service.approve(3L,new DecisionActionRequest("Test"));assertEquals(DecisionApprovalStatus.DA_DUYET,r.approvalStatus());assertEquals(20L,r.approvedBy());assertEquals(java.time.LocalDateTime.now(CLOCK),r.approvedAt());verify(audit).record(eq(20L),eq(3L),eq("DECISION_APPROVE"),anyString(),any(),any());}
    @Test void rejectStillRecordsReviewerAndDoesNotRequireMoneyPolicy(){var r=service("UNCONFIRMED").reject(3L,new DecisionActionRequest("Test"));assertEquals(DecisionApprovalStatus.TU_CHOI,r.approvalStatus());assertNotNull(r.approvedBy());assertNotNull(r.approvedAt());verifyNoInteractions(history);}
    @ParameterizedTest @EnumSource(value=DecisionApprovalStatus.class,names={"DA_DUYET","TU_CHOI"})
    void reviewedRecordCannotBeChangedOrReviewedAgain(DecisionApprovalStatus status){row.setApprovalStatus(status);status(409,()->service.approve(3L,new DecisionActionRequest("Test")));status(409,()->service.reject(3L,new DecisionActionRequest("Test")));status(409,()->service.update(3L,decisionRequest(DecisionPayrollEffect.ADD)));status(409,()->service.delete(3L,"Test"));}
    @Test void cannotChangeNumberOrEmployee(){status(409,()->service.update(3L,decisionRequest(DecisionPayrollEffect.ADD)));verify(repo,never()).saveAndFlush(any());}
    @Test void pendingDeleteHasAudit(){service.delete(3L,"Test");verify(repo).delete(row);verify(audit).record(eq(20L),eq(3L),eq("DECISION_DELETE"),eq("Test"),any(),isNull());}
    @Test void missingActorDoesNotReadRepositories(){when(actors.requireManager()).thenThrow(new com.hrm.exception.BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"not configured"));status(503,()->service.get(3L));verify(repo,never()).findById(any());}
}
