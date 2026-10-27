package com.hrm.hrm.service;

import java.time.*;
import java.util.*;

import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeTerminationServiceTest {
    @Mock EmployeeRepository employees;
    @Mock DepartmentRepository departments;
    @Mock PositionRepository positions;
    @Mock EmployeeAssignmentRepository assignments;
    @Mock AssignmentGuardService guard;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-10-23T17:30:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    @InjectMocks EmployeeService service;
    private final LocalDate day = LocalDate.of(2026, 10, 24);

    private Employee employee(EmploymentStatus status) {
        Employee employee = new Employee();
        employee.setEmployeeId(100L);
        employee.setHireDate(day.minusYears(1));
        employee.setEmploymentStatus(status);
        when(employees.findByIdForUpdate(100L)).thenReturn(Optional.of(employee));
        return employee;
    }

    private EmployeeAssignment assignment(String code) {
        Position position = new Position();
        position.setPositionCode(code);
        EmployeeAssignment assignment = new EmployeeAssignment();
        assignment.setPosition(position);
        assignment.setEffectiveFrom(day.minusDays(10));
        when(assignments.findCurrentAssignments(100L, day))
                .thenReturn(List.of(assignment));
        return assignment;
    }

    @Test void preservesEmployeeAndEndsAssignmentAfterLastWorkingDay() {
        Employee employee = employee(EmploymentStatus.DANG_LAM);
        EmployeeAssignment assignment = assignment("CHUYEN_VIEN");
        service.deleteEmployee(100L);
        assertEquals(EmploymentStatus.DA_NGHI, employee.getEmploymentStatus());
        assertEquals(day, employee.getTerminationDate());
        assertEquals(day.plusDays(1), assignment.getEffectiveTo());
        verify(guard).lockTimeline();
        verify(employees).save(employee);
        verify(assignments).save(assignment);
        verify(employees, never()).deleteById(anyLong());
        verify(employees, never()).delete(any(Employee.class));
    }

    @Test void rejectsMissingEmployee() {
        when(employees.findByIdForUpdate(100L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteEmployee(100L));
        verifyNoInteractions(assignments);
    }

    @Test void rejectsRepeatedTermination() {
        employee(EmploymentStatus.DA_NGHI);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.deleteEmployee(100L));
        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(assignments);
        verify(employees, never()).save(any(Employee.class));
    }

    @Test void rejectsDepartmentHeadUntilHeadWorkflowIsCompleted() {
        Employee employee = employee(EmploymentStatus.DANG_LAM);
        assignment(AssignmentGuardService.HEAD_CODE);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.deleteEmployee(100L));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(EmploymentStatus.DANG_LAM, employee.getEmploymentStatus());
        verify(employees, never()).save(any(Employee.class));
        verify(assignments, never()).save(any(EmployeeAssignment.class));
    }

    @Test void rejectsEmployeeWithoutCurrentAssignment() {
        employee(EmploymentStatus.DANG_LAM);
        when(assignments.findCurrentAssignments(100L, day)).thenReturn(List.of());
        var error = assertThrows(ResponseStatusException.class,
                () -> service.deleteEmployee(100L));
        assertEquals(409, error.getStatusCode().value());
        verify(employees, never()).save(any(Employee.class));
    }
}
