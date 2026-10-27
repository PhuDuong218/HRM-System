package com.hrm.hrm.service;

import java.time.LocalDate;
import java.util.Optional;

import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;
import com.hrm.service.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeTerminationAuditServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeeService employeeService;
    @Mock EmployeeAuditService auditService;
    @Mock AssignmentGuardService guard;

    @InjectMocks
    EmployeeTerminationAuditService terminationAudit;

    private Employee prepareEmployee() {
        Employee employee = new Employee();

        employee.setEmployeeId(100L);
        employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);

        when(employeeRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(employee));

        return employee;
    }

    @Test
    void recordsActorAndBeforeAfterState() {
        Employee employee = prepareEmployee();
        LocalDate day = LocalDate.of(2026, 10, 24);

        doAnswer(invocation -> {
            employee.setEmploymentStatus(EmploymentStatus.DA_NGHI);
            employee.setTerminationDate(day);
            return null;
        }).when(employeeService).deleteEmployee(100L);

        terminationAudit.terminate(100L, 7L);

        assertEquals(7L, employee.getUpdatedBy());

        var order = inOrder(
                guard, employeeRepository, employeeService, auditService);

        order.verify(guard).lockTimeline();
        order.verify(employeeRepository).findByIdForUpdate(100L);
        order.verify(employeeService).deleteEmployee(100L);

        order.verify(auditService).recordTermination(
                eq(7L),
                eq(100L),
                argThat(data ->
                        "DANG_LAM".equals(data.get("employmentStatus"))
                        && data.get("terminationDate") == null),
                argThat(data ->
                        "DA_NGHI".equals(data.get("employmentStatus"))
                        && day.toString().equals(
                                data.get("terminationDate"))));
    }

    @Test
    void rejectsMissingActorBeforeAccessingEmployee() {
        assertThrows(
                IllegalArgumentException.class,
                () -> terminationAudit.terminate(100L, null));

        verifyNoInteractions(
                guard, employeeRepository, employeeService, auditService);
    }

    @Test
    void propagatesAuditFailure() {
        Employee employee = prepareEmployee();

        doAnswer(invocation -> {
            employee.setEmploymentStatus(EmploymentStatus.DA_NGHI);
            employee.setTerminationDate(LocalDate.of(2026, 10, 24));
            return null;
        }).when(employeeService).deleteEmployee(100L);

        doThrow(new DataAccessResourceFailureException(
                "Lỗi audit giả lập"))
                .when(auditService)
                .recordTermination(
                        eq(7L), eq(100L), anyMap(), anyMap());

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> terminationAudit.terminate(100L, 7L));
    }
}