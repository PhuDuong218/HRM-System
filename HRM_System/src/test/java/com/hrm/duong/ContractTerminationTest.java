package com.hrm.duong;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.model.Contract;
import com.hrm.repository.*;
import com.hrm.service.*;
import com.hrm.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class ContractTerminationTest {
    ContractRepository repo;
    EmployeeRepository employees;
    ContractService contracts;
    BusinessAuditService audit;
    PayrollHistoryGuard guard;
    ContractLifecycleService service;
    Contract old;

    @BeforeEach void setup() {
        repo=mock(ContractRepository.class);
        employees=mock(EmployeeRepository.class);
        contracts=mock(ContractService.class);
        audit=mock(BusinessAuditService.class);
        guard=mock(PayrollHistoryGuard.class);
        service=new ContractLifecycleService(repo,employees,contracts,audit,guard,CLOCK);
        ReflectionTestUtils.setField(service,"em",mock(EntityManager.class));
        old=contract(3,TODAY.minusDays(30),TODAY.plusDays(30));
        when(repo.findById(3L)).thenReturn(Optional.of(old));
        when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(old.getEmployee()));
        when(contracts.getOne(3L)).thenAnswer(ignored -> response(old));
    }

    private ContractTerminateRequest terminateAt(LocalDate date) {
        return new ContractTerminateRequest(date,"Test reason",TerminationReasonType.THOA_THUAN);
    }

    private ContractResponse response(Contract c) {
        return new ContractResponse(c.getContractId(),1L,"NV1","Synthetic",2L,"CT",c.getContractNumber(),
                c.getSignedDate(),c.getStartDate(),c.getEndDate(),c.getStatus(),ContractTermKind.XAC_DINH,
                null,c.getTerminationReasonType());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void resignationClosesOpenOrFixedContractOnce(boolean indefinite) {
        if (indefinite) old.setEndDate(null);
        var before = response(old);
        var lastDay = TODAY.minusDays(5);
        when(repo.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L)).thenReturn(List.of(old));

        service.terminateForResignation(1L, lastDay, TerminationReasonType.THOA_THUAN, "  Nghỉ việc  ");
        service.terminateForResignation(1L, lastDay, TerminationReasonType.THOA_THUAN, "  Nghỉ việc  ");

        assertEquals(lastDay, old.getEndDate());
        assertEquals(ContractStatus.DA_KET_THUC, old.getStatus());
        assertEquals(TerminationReasonType.THOA_THUAN, old.getTerminationReasonType());
        var order = inOrder(employees, repo, guard);
        order.verify(employees).findByIdForUpdate(1L);
        order.verify(repo).findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L);
        order.verify(guard).assertMutable(1L, lastDay.plusDays(1), before.endDate());
        order.verify(repo).saveAndFlush(old);
        verify(repo, times(1)).saveAndFlush(old);
        verify(audit).record("contracts", 3L, "CONTRACT_TERMINATE", "Nghỉ việc", before, response(old));
    }

    @Test void resignationIsBlockedByClosedPayrollInRemovedDays() {
        var lastDay = TODAY.minusDays(5);
        var before = response(old);
        when(repo.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L)).thenReturn(List.of(old));
        doThrow(new BusinessException(HttpStatus.CONFLICT, "Kỳ lương đã chốt"))
                .when(guard).assertMutable(1L, lastDay.plusDays(1), old.getEndDate());

        status(409, () -> service.terminateForResignation(1L, lastDay, TerminationReasonType.THOA_THUAN, "Nghỉ việc"));

        assertEquals(before, response(old));
        verify(repo, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void resignationCancelsFutureContractWithoutRewritingItsDates() {
        var future = contract(4, TODAY.plusDays(31), TODAY.plusDays(60));
        var before = response(future);
        when(contracts.getOne(4L)).thenAnswer(ignored -> response(future));
        when(repo.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L)).thenReturn(List.of(future));

        for (int call = 0; call < 2; call++) {
            service.terminateForResignation(1L, TODAY.minusDays(5), TerminationReasonType.THOA_THUAN, "Nghỉ việc");
        }

        assertEquals(ContractStatus.DA_HUY, future.getStatus());
        assertEquals(TerminationReasonType.THOA_THUAN, future.getTerminationReasonType());
        assertEquals(before.startDate(), future.getStartDate());
        assertEquals(before.endDate(), future.getEndDate());
        verify(guard).assertMutable(1L, before.startDate(), before.endDate());
        verify(repo).saveAndFlush(future);
        verify(audit).record("contracts", 4L, "CONTRACT_CANCEL", "Nghỉ việc", before, response(future));
    }

    @Test void retroactiveTerminationClosesImmediatelyAndAuditsBeforeAfter() {
        var before=response(old);
        var result=service.terminate(3L,new ContractTerminateRequest(TODAY.minusDays(5),"  agreed reason  ",TerminationReasonType.NLD_DON_PHUONG));
        assertEquals(ContractStatus.DA_KET_THUC,result.status());
        assertEquals(TerminationReasonType.NLD_DON_PHUONG,result.reasonType());
        assertEquals(TODAY.minusDays(5),result.endDate());
        verify(guard).assertMutable(1L,TODAY.minusDays(4),TODAY.plusDays(30));
        verify(repo).saveAndFlush(old);
        verify(audit).record("contracts",3L,"CONTRACT_TERMINATE","agreed reason",before,result);
    }

    @Test void closedPayrollBlocksBeforeChangingContract() {
        var before=response(old);
        doThrow(new BusinessException(HttpStatus.CONFLICT,"closed payroll"))
            .when(guard).assertMutable(1L,TODAY.minusDays(4),TODAY.plusDays(30));
        status(409,()->service.terminate(3L,terminateAt(TODAY.minusDays(5))));
        assertEquals(before,response(old));
        verify(repo,never()).saveAndFlush(any());
        verify(audit,never()).record(anyString(),anyLong(),anyString(),anyString(),any(),any());
    }

    @Test void indefiniteContractGuardsRemovedDaysToInfinity() {
        old.setEndDate(null);
        service.terminate(3L,terminateAt(TODAY.minusDays(1)));
        verify(guard).assertMutable(1L,TODAY,null);
        assertEquals(ContractStatus.DA_KET_THUC,old.getStatus());
    }

    @Test void lastDayTodayStaysSignedUntilTomorrow() {
        service.terminate(3L,terminateAt(TODAY));
        assertEquals(ContractStatus.DA_KY,old.getStatus());
        verify(guard).assertMutable(1L,TODAY.plusDays(1),TODAY.plusDays(30));
    }

    @Test void dateBeforeContractStartIsRejected() {
        status(409,()->service.terminate(3L,terminateAt(old.getStartDate().minusDays(1))));
        verifyNoInteractions(guard);
        verify(repo,never()).saveAndFlush(any());
    }

    @Test void dateAfterContractEndIsRejected() {
        status(409,()->service.terminate(3L,terminateAt(old.getEndDate().plusDays(1))));
        verifyNoInteractions(guard);
        verify(repo,never()).saveAndFlush(any());
    }

    @Test void terminationExactlyAtStartIsAllowedWithoutChangingChildIntervals() {
        service.terminate(3L,terminateAt(old.getStartDate()));
        assertEquals(old.getStartDate(),old.getEndDate());
        assertEquals(ContractStatus.DA_KET_THUC,old.getStatus());
    }

    @Test void terminationAtExistingEndHasNoRemovedDaysToGuard() {
        service.terminate(3L,terminateAt(old.getEndDate()));
        verifyNoInteractions(guard);
    }

    @Test void futureContractCanScheduleTerminationInsideItsDates() {
        old.setStartDate(TODAY.plusDays(2));
        service.terminate(3L,terminateAt(TODAY.plusDays(3)));
        assertEquals(ContractStatus.DA_KY,old.getStatus());
    }

    @ParameterizedTest @EnumSource(TerminationReasonType.class)
    void acceptsEachReasonType(TerminationReasonType type) {
        assertEquals(type,service.terminate(3L,new ContractTerminateRequest(TODAY,"reason",type)).reasonType());
    }

    @Test void missingReasonTypeIs400EvenForInternalCall() {
        status(400,()->service.terminate(3L,new ContractTerminateRequest(TODAY,"reason",null)));
        verifyNoInteractions(repo,employees);
    }

    @Test void missingDateOrBlankOrOversizedReasonIs400() {
        status(400,()->service.terminate(3L,new ContractTerminateRequest(null,"reason",TerminationReasonType.THOA_THUAN)));
        status(400,()->service.terminate(3L,new ContractTerminateRequest(TODAY,"  ",TerminationReasonType.THOA_THUAN)));
        status(400,()->service.terminate(3L,new ContractTerminateRequest(TODAY,"x".repeat(501),TerminationReasonType.THOA_THUAN)));
        verifyNoInteractions(repo,employees);
    }

    @Test void repeatedScheduledTerminationRejectedEvenWithoutAuditStub() {
        service.terminate(3L,terminateAt(TODAY.plusDays(3)));
        status(409,()->service.terminate(3L,terminateAt(TODAY.plusDays(2))));
        assertEquals(TODAY.plusDays(3),old.getEndDate());
        verify(repo,times(1)).saveAndFlush(old);
    }

    @Test void repeatedRetroactiveTerminationRejected() {
        service.terminate(3L,terminateAt(TODAY.minusDays(1)));
        status(409,()->service.terminate(3L,terminateAt(TODAY.minusDays(2))));
        verify(repo,times(1)).saveAndFlush(old);
    }

    @Test void expiryWritesNaturalReasonAndIsIdempotent() {
        old.setEndDate(TODAY.minusDays(1));
        service.closeExpired(3L);
        assertEquals(ContractStatus.DA_KET_THUC,old.getStatus());
        assertEquals(TerminationReasonType.HET_HAN,old.getTerminationReasonType());
        service.closeExpired(3L);
        verify(repo,times(1)).saveAndFlush(old);
    }

    @Test void expiryPreservesScheduledTerminationReason() {
        old.setEndDate(TODAY.minusDays(1));
        old.setTerminationReasonType(TerminationReasonType.SA_THAI);
        service.closeExpired(3L);
        assertEquals(ContractStatus.DA_KET_THUC,old.getStatus());
        assertEquals(TerminationReasonType.SA_THAI,old.getTerminationReasonType());
    }

    @Test void legacyApiTerminationIsNotRelabeledAsNaturalExpiry() {
        old.setEndDate(TODAY.minusDays(1));
        when(audit.hasAction("contracts",3L,"CONTRACT_TERMINATE")).thenReturn(true);
        service.closeExpired(3L);
        assertEquals(ContractStatus.DA_KET_THUC,old.getStatus());
        assertNull(old.getTerminationReasonType());
    }
}
