package com.hrm.duong;

import java.time.LocalDate;
import java.util.*;
import com.hrm.dto.*;
import com.hrm.model.*;
import com.hrm.enums.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import com.hrm.exception.BusinessException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class EmployeeAllowanceServiceTest {
    @Test void resignationClosesOpenAllowanceAndDeletesLaterAssignmentsOnlyOnce() {
        LocalDate cutoff = TODAY.minusDays(5);
        var ended = allowance(4, cutoff.minusDays(8), cutoff.minusDays(1), AllowanceCalculationBasis.THEO_KY, "10");
        var onCutoff = allowance(5, cutoff, cutoff.plusDays(1), AllowanceCalculationBasis.THEO_KY, "20");
        var future = allowance(6, TODAY.plusDays(2), null, AllowanceCalculationBasis.THEO_KY, "30");
        var rows = new ArrayList<>(List.of(old, ended, onCutoff, future));
        when(allowances.findByEmployee_EmployeeIdOrderByEffectiveFromAsc(1L)).thenReturn(rows);
        doAnswer(call -> { rows.remove(call.getArgument(0)); return null; }).when(allowances).delete(any());

        service.endAllFrom(1L, cutoff, "  Nghỉ việc  ");
        service.endAllFrom(1L, cutoff, "  Nghỉ việc  ");

        assertEquals(cutoff.minusDays(1), old.getEffectiveTo());
        assertEquals(List.of(old, ended), rows);
        var order = inOrder(employees, allowances, guard);
        order.verify(employees).findByIdForUpdate(1L);
        order.verify(allowances).findByEmployee_EmployeeIdOrderByEffectiveFromAsc(1L);
        order.verify(guard).assertMutable(1L, cutoff, null);
        order.verify(allowances).saveAndFlush(old);
        verify(guard).assertMutable(1L, cutoff, cutoff.plusDays(1));
        verify(guard).assertMutable(1L, future.getEffectiveFrom(), null);
        verify(audit).record(eq("employee_allowances"), eq(3L), eq("ALLOWANCE_END"), eq("Nghỉ việc"),
                any(EmployeeAllowanceResponse.class), any(EmployeeAllowanceResponse.class));
        for (long id : List.of(5L, 6L)) {
            verify(audit).record(eq("employee_allowances"), eq(id), eq("ALLOWANCE_DELETE"), eq("Nghỉ việc"),
                    any(EmployeeAllowanceResponse.class), isNull());
        }
        verifyNoMoreInteractions(audit);
    }

    @Test void resignationKeepsRemainingSingleDay() {
        when(allowances.findByEmployee_EmployeeIdOrderByEffectiveFromAsc(1L)).thenReturn(List.of(old));

        var stop = old.getEffectiveFrom().plusDays(1);
        service.endAllFrom(1L, stop, "Nghỉ việc");
        service.endAllFrom(1L, stop, "Nghỉ việc");

        assertEquals(old.getEffectiveFrom(), old.getEffectiveTo());
        verify(guard).assertMutable(1L, stop, null);
        verify(allowances).saveAndFlush(old);
        verify(allowances, never()).delete(any());
    }

    @Test void resignationCannotRemoveAllowanceFromClosedPayroll() {
        var cutoff = TODAY.minusDays(5);
        when(allowances.findByEmployee_EmployeeIdOrderByEffectiveFromAsc(1L)).thenReturn(List.of(old));
        doThrow(new BusinessException(HttpStatus.CONFLICT, "Kỳ lương đã chốt"))
                .when(guard).assertMutable(1L, cutoff, null);

        status(409, () -> service.endAllFrom(1L, cutoff, "Nghỉ việc"));

        assertNull(old.getEffectiveTo());
        verify(allowances, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    private EmployeeAllowanceRequest percentage(String amount) {
        return new EmployeeAllowanceRequest(2L,TODAY,null,AllowanceCalculationBasis.THEO_KY,
                money(amount),null,"Synthetic test",AllowanceAmountType.PERCENT_BASE_SALARY);
    }

    @Test void acceptsZeroAndOneHundredPercentAndRejectsOutsideBounds() {
        status(400,()->service.create(1L,percentage("-0.0001")));
        status(400,()->service.create(1L,percentage("100.0001")));
        verify(allowances,never()).saveAndFlush(any());
        assertEquals(AllowanceAmountType.PERCENT_BASE_SALARY,service.create(1L,percentage("0")).amountType());
        assertEquals(AllowanceAmountType.PERCENT_BASE_SALARY,service.create(1L,percentage("100")).amountType());
    }

    @Test void legacyEditCannotSilentlyReinterpretPercentAmount() {
        old.setAmountType(AllowanceAmountType.PERCENT_BASE_SALARY);
        old.setEffectiveFrom(TODAY.plusDays(5));
        status(400,()->service.updateFuture(1L,3L,allowanceRequest(TODAY.plusDays(6),null,"2")));
        verify(allowances,never()).saveAndFlush(any());
    }
    EmployeeAllowanceRepository allowances;AllowanceTypeRepository types;EmployeeRepository employees;BusinessAuditService audit;PayrollHistoryGuard guard;EmployeeAllowanceService service;EmployeeAllowance old;
    @BeforeEach void setup(){allowances=mock(EmployeeAllowanceRepository.class);types=mock(AllowanceTypeRepository.class);employees=mock(EmployeeRepository.class);audit=mock(BusinessAuditService.class);guard=mock(PayrollHistoryGuard.class);service=new EmployeeAllowanceService(allowances,types,employees,audit,guard,CLOCK);ReflectionTestUtils.setField(service,"em",mock(EntityManager.class));old=allowance(3,TODAY.minusDays(30),null,AllowanceCalculationBasis.THEO_NGAY_CONG,"30000");when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(old.getEmployee()));when(types.findByIdForUpdate(2L)).thenReturn(Optional.of(old.getAllowanceType()));when(allowances.findByEmployeeAllowanceIdAndEmployee_EmployeeId(3L,1L)).thenReturn(Optional.of(old));when(allowances.saveAndFlush(any())).thenAnswer(x->{var a=(EmployeeAllowance)x.getArgument(0);if(a.getEmployeeAllowanceId()==null)id(a,"employeeAllowanceId",4);return a;});}
    @Test void duplicateEffectiveIntervalIs409AndDoesNotSave(){when(allowances.countOverlapping(1L,2L,TODAY,null,null)).thenReturn(1L);status(409,()->service.create(1L,allowanceRequest(TODAY,null,"1")));verify(allowances,never()).saveAndFlush(any());}
    @Test void inactiveTypeCannotBeAssigned(){old.getAllowanceType().setIsActive(false);status(400,()->service.create(1L,allowanceRequest(TODAY,null,"1")));}
    @Test void formerEmployeeCannotGetNewAllowance(){old.getEmployee().setEmploymentStatus(EmploymentStatus.DA_NGHI);status(400,()->service.create(1L,allowanceRequest(TODAY,null,"1")));}
    @Test void assignmentCanCoverOneDayButRejectsReversedDates() {
        status(400, () -> service.create(1L, allowanceRequest(TODAY, TODAY.minusDays(1), "1")));
        var result = service.create(1L, allowanceRequest(TODAY, TODAY, "1"));
        assertEquals(TODAY, result.effectiveFrom());
        assertEquals(TODAY, result.effectiveTo());
        verify(guard).assertMutable(1L, TODAY, TODAY);
    }

    @Test void endingOnFirstEffectiveDayUsesNextDayAsStopFrom() {
        old.setEffectiveFrom(TODAY);
        status(400, () -> service.endFrom(1L, 3L, new AllowanceEndRequest(TODAY, "No eligible day")));
        var result = service.endFrom(1L, 3L, new AllowanceEndRequest(TODAY.plusDays(1), "Keep first day"));
        assertEquals(TODAY, result.effectiveTo());
        verify(guard).assertMutable(1L, TODAY.plusDays(1), null);
    }

    @Test void changeFromCanLeaveOneDayInOldAllowance() {
        old.setEffectiveFrom(TODAY);
        var result = service.changeFrom(1L, 3L,
                new AllowanceChangeRequest(allowanceRequest(TODAY.plusDays(1), null, "1")));
        assertEquals(TODAY, old.getEffectiveTo());
        assertEquals(TODAY.plusDays(1), result.effectiveFrom());
    }
    @Test void effectiveRowCannotBeRewrittenOrDeleted(){status(400,()->service.updateFuture(1L,3L,allowanceRequest(TODAY.plusDays(1),null,"2")));status(400,()->service.deleteFuture(1L,3L,"Test"));}
    @Test void employeeScopePreventsReadingOtherEmployeesRecord(){status(404,()->service.get(2L,3L));}
    @Test void changePreservesOldAmountAndClosesAtPreviousDay(){var r=service.changeFrom(1L,3L,new AllowanceChangeRequest(allowanceRequest(TODAY.plusDays(10),null,"35000")));assertEquals(4L,r.employeeAllowanceId());assertEquals(money("30000"),old.getAmount());assertEquals(TODAY.plusDays(9),old.getEffectiveTo());assertEquals(money("35000"),r.amount());}
    @Test void stopFromDayIsNotPaid(){service.endFrom(1L,3L,new AllowanceEndRequest(TODAY.plusDays(10),"Test"));assertEquals(TODAY.plusDays(9),old.getEffectiveTo());verify(guard).assertMutable(1L,TODAY.plusDays(10),null);}
    @Test void cannotEndRetroactively(){status(400,()->service.endFrom(1L,3L,new AllowanceEndRequest(TODAY.minusDays(1),"Test")));}
    @Test void futureAssignmentCanBeUpdatedAndDeleted(){old.setEffectiveFrom(TODAY.plusDays(5));service.updateFuture(1L,3L,allowanceRequest(TODAY.plusDays(6),null,"2"));assertEquals(money("2"),old.getAmount());service.deleteFuture(1L,3L,"Test");verify(allowances).delete(old);}
}
