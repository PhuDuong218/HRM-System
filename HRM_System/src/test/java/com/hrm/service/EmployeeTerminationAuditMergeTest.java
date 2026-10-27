package com.hrm.service;

import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeTerminationAuditMergeTest {

    private static final LocalDate TERMINATION_DATE = LocalDate.of(2026, 10, 24);

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private EmployeeAuditService auditService;
    @Mock
    private AssignmentGuardService guard;
    @InjectMocks
    private EmployeeTerminationAuditService service;

    @Test
    void strictTerminationRejectsRepeatWithoutMutationOrDelegation() {
        Employee employee = prepareEmployee(EmploymentStatus.DA_NGHI);
        employee.setUpdatedBy(44L);
        employee.setTerminationDate(TERMINATION_DATE);

        var failure = assertThrows(ResponseStatusException.class, () -> service.terminate(100L, 7L));

        assertEquals(409, failure.getStatusCode().value());
        assertEquals(44L, employee.getUpdatedBy());
        assertEquals(TERMINATION_DATE, employee.getTerminationDate());
        verifyNoInteractions(employeeService, auditService);
        var order = inOrder(guard, employeeRepository);
        order.verify(guard).lockTimeline();
        order.verify(employeeRepository).findByIdForUpdate(100L);
    }

    @Test
    void idempotentTerminationStillRevokesAlreadyRetiredEmployeesWithoutChangingAuditActor() {
        Employee employee = prepareEmployee(EmploymentStatus.DA_NGHI);
        employee.setUpdatedBy(44L);
        employee.setTerminationDate(TERMINATION_DATE);

        service.terminateIfActive(100L, 7L);

        assertEquals(44L, employee.getUpdatedBy());
        assertEquals(TERMINATION_DATE, employee.getTerminationDate());
        assertEquals(EmploymentStatus.DA_NGHI, employee.getEmploymentStatus());
        verify(employeeService).deleteEmployee(100L);
        verifyNoInteractions(auditService);
    }

    @Test
    void idempotentTerminationAuditsFirstChangeAndOnlyRevokesOnRepeat() {
        Employee employee = prepareEmployee(EmploymentStatus.DANG_LAM);
        doAnswer(invocation -> {
            employee.setEmploymentStatus(EmploymentStatus.DA_NGHI);
            employee.setTerminationDate(TERMINATION_DATE);
            return null;
        }).when(employeeService).deleteEmployee(100L);

        service.terminateIfActive(100L, 7L);
        service.terminateIfActive(100L, 8L);

        assertEquals(7L, employee.getUpdatedBy());
        verify(employeeService, times(2)).deleteEmployee(100L);
        verify(auditService).recordTermination(eq(7L), eq(100L),
                argThat(before -> "DANG_LAM".equals(before.get("employmentStatus"))
                        && before.get("terminationDate") == null),
                argThat(after -> "DA_NGHI".equals(after.get("employmentStatus"))
                        && TERMINATION_DATE.toString().equals(after.get("terminationDate"))));
        verifyNoMoreInteractions(auditService);
    }

    @Test
    void idempotentTerminationRequiresActorBeforeLocksOrChanges() {
        assertThrows(IllegalArgumentException.class, () -> service.terminateIfActive(100L, null));

        verifyNoInteractions(guard, employeeRepository, employeeService, auditService);
    }

    private Employee prepareEmployee(EmploymentStatus status) {
        Employee employee = new Employee();
        employee.setEmployeeId(100L);
        employee.setEmploymentStatus(status);
        when(employeeRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(employee));
        return employee;
    }
}
