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
    DecisionRequest discipline(DisciplineForm form, Integer months, java.time.LocalDate violation, DecisionPayrollEffect effect) {
        return new DecisionRequest(1L,"QD_NEW",DecisionType.KY_LUAT,"Test",TODAY,effect,null,null,"Test",form,violation,months);
    }
    @ParameterizedTest @EnumSource(DisciplineForm.class)
    void disciplineRequiresNoMoneyAndPersistsDetails(DisciplineForm form) {
        var r=service.create(discipline(form,form==DisciplineForm.KEO_DAI_NANG_LUONG?6:null,TODAY,DecisionPayrollEffect.NONE));
        assertEquals(form,r.disciplineForm());assertEquals(TODAY,r.violationDate());assertNull(r.amount());
    }
    @Test void disciplineCannotAddOrDeduct() {
        for(var effect:List.of(DecisionPayrollEffect.ADD,DecisionPayrollEffect.DEDUCT))
            status(400,()->service.create(discipline(DisciplineForm.KHIEN_TRACH,null,TODAY,effect)));
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void disciplineRequiresFormAndViolationDate() {
        status(400,()->service.create(discipline(null,null,TODAY,DecisionPayrollEffect.NONE)));
        status(400,()->service.create(discipline(DisciplineForm.KHIEN_TRACH,null,null,DecisionPayrollEffect.NONE)));
    }
    @Test void delayMustBeOneToSixMonthsAndOnlyForDelayForm() {
        for(Integer months:Arrays.asList(null,0,7)) status(400,()->service.create(discipline(DisciplineForm.KEO_DAI_NANG_LUONG,months,TODAY,DecisionPayrollEffect.NONE)));
        assertEquals(1,service.create(discipline(DisciplineForm.KEO_DAI_NANG_LUONG,1,TODAY,DecisionPayrollEffect.NONE)).salaryIncreaseDelayMonths());
        status(400,()->service.create(discipline(DisciplineForm.CACH_CHUC,1,TODAY,DecisionPayrollEffect.NONE)));
    }
    @Test void rewardCannotDeductAndCompensationRequiresDeduct() {
        status(400,()->service.create(new DecisionRequest(1L,"NEW",DecisionType.KHEN_THUONG,"Test",TODAY,DecisionPayrollEffect.DEDUCT,money("10"),TODAY,"Test",null,null,null)));
        for(var effect:List.of(DecisionPayrollEffect.NONE,DecisionPayrollEffect.ADD))
            status(400,()->service.create(new DecisionRequest(1L,"NEW",DecisionType.BOI_THUONG,"Test",TODAY,effect,money("10"),TODAY,"Test",null,null,null)));
        assertEquals(DecisionType.BOI_THUONG,service.create(decisionRequest(DecisionPayrollEffect.DEDUCT)).decisionType());
    }
    @Test void updateAndReviewAlsoEnforceNewRules() {
        var bad=new DecisionRequest(1L,"QD3",DecisionType.KY_LUAT,"Test",TODAY,DecisionPayrollEffect.DEDUCT,money("10"),TODAY,"Test",DisciplineForm.KHIEN_TRACH,TODAY,null);
        status(400,()->service.update(3L,bad));
        row.setDecisionType(DecisionType.KY_LUAT);
        status(400,()->service.approve(3L,new DecisionActionRequest("Test")));
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void compensationRequiresPositiveMoneyAndApplyDate() {
        for(var amount:Arrays.asList(null,money("0"),money("-1")))
            status(400,()->service.create(new DecisionRequest(1L,"NEW",DecisionType.BOI_THUONG,"Test",TODAY,DecisionPayrollEffect.DEDUCT,amount,TODAY,"Test",null,null,null)));
        status(400,()->service.create(new DecisionRequest(1L,"NEW",DecisionType.BOI_THUONG,"Test",TODAY,DecisionPayrollEffect.DEDUCT,money("1"),null,"Test",null,null,null)));
    }
    @Test void rewardAmountComesFromConfiguredLevel() {
        var policy = new DecisionPayrollPolicy("TEST-ONLY-v1");
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(policy, "configureRewards", "INITIATIVE=500000");
        var numbers = mock(DecisionNumberService.class);
        when(numbers.next(DecisionType.KHEN_THUONG)).thenReturn("KT-000001");
        var configured = new EmployeeDecisionService(repo, employees, actors, audit, history, policy, CLOCK, numbers);

        status(400, () -> configured.create(reward("INITIATIVE", money("0.0087"))));
        status(400, () -> configured.create(reward("UNKNOWN", null)));
        verify(repo, never()).saveAndFlush(any());
        assertEquals(money("500000"), configured.create(reward("INITIATIVE", null)).amount());
    }

    private DecisionRequest reward(String code, java.math.BigDecimal amount) {
        return new DecisionRequest(1L, null, DecisionType.KHEN_THUONG, "Test", TODAY,
                DecisionPayrollEffect.ADD, amount, TODAY, "Test", null, null, null, code);
    }
    EmployeeDecisionRepository repo;EmployeeRepository employees;DecisionActorProvider actors;DecisionAuditService audit;PayrollHistoryGuard history;EmployeeDecisionService service;EmployeeDecision row;
    @BeforeEach void setup(){repo=mock(EmployeeDecisionRepository.class);employees=mock(EmployeeRepository.class);actors=mock(DecisionActorProvider.class);audit=mock(DecisionAuditService.class);history=mock(PayrollHistoryGuard.class);row=decision(3,DecisionPayrollEffect.ADD);when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(row.getEmployee()));when(actors.requireManager()).thenReturn(new DecisionActor(20L,200L));when(repo.findById(3L)).thenReturn(Optional.of(row));when(repo.findEmployeeIdById(3L)).thenReturn(Optional.of(1L));when(repo.findByIdForUpdate(3L)).thenReturn(Optional.of(row));when(repo.saveAndFlush(any())).thenAnswer(x->{var d=(EmployeeDecision)x.getArgument(0);if(d.getDecisionId()==null)id(d,"decisionId",4);return d;});service=service("TEST-ONLY-v1");}
    EmployeeDecisionService service(String version){var numbers=mock(DecisionNumberService.class);when(numbers.next(any())).thenAnswer(call->switch((DecisionType)call.getArgument(0)){case KHEN_THUONG->"KT-000001";case KY_LUAT->"KL-000001";case BOI_THUONG->"BT-000001";});return new EmployeeDecisionService(repo,employees,actors,audit,history,new DecisionPayrollPolicy(version),CLOCK,numbers);}
    @Test void createIsAlwaysPendingWithServerActor(){var r=service.create(decisionRequest(DecisionPayrollEffect.NONE));assertEquals(DecisionApprovalStatus.CHO_DUYET,r.approvalStatus());assertEquals(20L,r.issuedBy());assertNull(r.approvedBy());assertNull(r.approvedAt());verify(audit).record(eq(20L),eq(4L),eq("DECISION_CREATE"),anyString(),isNull(),any());}
    @Test void generatesPrefixAndIgnoresClientNumber(){assertEquals("KT-000001",service.create(decisionRequest(DecisionPayrollEffect.NONE)).decisionNumber());assertEquals("KL-000001",service.create(discipline(DisciplineForm.KHIEN_TRACH,null,TODAY,DecisionPayrollEffect.NONE)).decisionNumber());}
    @Test void duplicateNumberIs409(){when(repo.existsByDecisionNumber("KT-000001")).thenReturn(true);status(409,()->service.create(decisionRequest(DecisionPayrollEffect.NONE)));verify(repo,never()).saveAndFlush(any());}
    @Test void noneMustNotHaveMoneyOrApplyDate(){var r=decisionRequest(DecisionPayrollEffect.ADD);status(400,()->service.create(new DecisionRequest(1L,"NONE",r.decisionType(),r.content(),TODAY,DecisionPayrollEffect.NONE,r.amount(),r.applyDate(),"Test",null,null,null)));}
    @Test void moneyRequiresPositiveAmountAndDate(){var r=decisionRequest(DecisionPayrollEffect.ADD);status(400,()->service.create(new DecisionRequest(1L,"BAD",r.decisionType(),r.content(),TODAY,r.payrollEffect(),money("0"),TODAY,"Test",null,null,null)));status(400,()->service.create(new DecisionRequest(1L,"BAD",r.decisionType(),r.content(),TODAY,r.payrollEffect(),r.amount(),null,"Test",null,null,null)));}
    @Test void applyDateCannotPrecedeIssue(){var r=decisionRequest(DecisionPayrollEffect.ADD);status(400,()->service.create(new DecisionRequest(1L,"BAD",r.decisionType(),r.content(),TODAY,r.payrollEffect(),r.amount(),TODAY.minusDays(1),"Test",null,null,null)));}
    @Test void issuerGetsSpecificReviewBlocker(){when(actors.requireManager()).thenReturn(new DecisionActor(10L,100L));assertTrue(service.get(3L).reviewBlocker().contains("người ban hành"));}
    @Test void independentReviewerHasNoReviewBlocker(){assertNull(service.get(3L).reviewBlocker());}
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
