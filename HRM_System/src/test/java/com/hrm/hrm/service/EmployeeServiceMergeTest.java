package com.hrm.hrm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.dto.EmployeeUpdateRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Department;
import com.hrm.model.Employee;
import com.hrm.model.EmployeeAssignment;
import com.hrm.model.Position;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.DepartmentRepository;
import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PositionRepository;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AssignmentGuardService;
import com.hrm.service.EmployeeService;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceMergeTest {

    @Mock EmployeeRepository employees;
    @Mock DepartmentRepository departments;
    @Mock PositionRepository positions;
    @Mock AccountRepository accounts;
    @Mock EmployeeAssignmentRepository assignments;
    @Mock AssignmentGuardService guard;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-10-23T17:30:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    @InjectMocks EmployeeService service;

    private final LocalDate businessDate = LocalDate.of(2026, 10, 24);

    @Test
    void employeeCannotReadDirectoryOrAnotherEmployeesProfile() {
        AuthenticatedAccount actor = new AuthenticatedAccount(1L, 100L, "employee", "EMPLOYEE");
        assertThrows(AccessDeniedException.class, () -> service.getAllEmployees(actor));
        assertThrows(AccessDeniedException.class, () -> service.getEmployeeById(101L, actor));
        AuthenticatedAccount unlinked = new AuthenticatedAccount(2L, null, "unlinked", "EMPLOYEE");
        assertThrows(AccessDeniedException.class, () -> service.getEmployeeById(100L, unlinked));
        verifyNoInteractions(employees);
    }

    @Test
    void ownProfileUsesBusinessDateAndDeterministicCurrentAssignment() {
        Employee employee = employee();
        EmployeeAssignment expired = assignment(employee, 99L, 99L, businessDate.minusMonths(1));
        expired.setEffectiveTo(businessDate);
        EmployeeAssignment latest = assignment(employee, 6L, 60L, businessDate);
        EmployeeAssignment sameDateOlderId = assignment(employee, 5L, 50L, businessDate);
        employee.getAssignments().addAll(List.of(expired, latest, sameDateOlderId));
        when(employees.findByIdWithAssignments(100L)).thenReturn(Optional.of(employee));

        var result = service.getEmployeeById(100L,
                new AuthenticatedAccount(1L, 100L, "employee", "EMPLOYEE"));

        assertEquals(60L, result.getCurrentDepartmentId());
    }

    @Test
    void searchChoosesSameLatestAssignmentAsProfileWhenLegacyAssignmentsOverlap() {
        Employee employee = employee();
        EmployeeAssignment latest = assignment(employee, 6L, 60L, businessDate);
        EmployeeAssignment sameDateOlderId = assignment(employee, 5L, 50L, businessDate);
        when(employees.searchEmployees(eq("%test%"), isNull(), isNull(), isNull(),
                eq(businessDate), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));
        when(assignments.findCurrentByEmployeeIds(List.of(100L), businessDate))
                .thenReturn(List.of(sameDateOlderId, latest));

        var result = service.searchEmployees(" TEST ", null, null, null, 0, 10);

        assertEquals(60L, result.getContent().get(0).getCurrentDepartmentId());
    }

    @Test
    void legacyProfileUpdateRetainsIdentityAndAssignments() {
        Employee employee = employee();
        employee.setIdentityNumber("original-identity");
        employee.setAddress("original-address");
        EmployeeAssignment assignment = assignment(employee, 1L, 10L, employee.getHireDate());
        employee.getAssignments().add(assignment);
        stubUpdate(employee);
        EmployeeRequestDTO request = new EmployeeRequestDTO();
        request.setFullName("  Updated Name  ");
        request.setBirthDate(LocalDate.of(2000, 1, 1));
        request.setEmail(" updated@example.com ");
        request.setIdentityNumber("ignored-identity");
        request.setDepartmentId(999L);

        var result = service.updateEmployee(100L, request);

        assertEquals("Updated Name", result.getFullName());
        assertEquals("updated@example.com", result.getEmail());
        assertEquals("original-identity", employee.getIdentityNumber());
        assertEquals("original-address", employee.getAddress());
        assertEquals(List.of(assignment), employee.getAssignments());
        assertEquals(LocalDateTime.of(2026, 10, 23, 17, 30), employee.getUpdatedAt());
    }

    @Test
    void newProfileUpdateCanReplaceIdentityWithoutErasingContactAddress() {
        Employee employee = employee();
        employee.setAddress("original-address");
        stubUpdate(employee);
        EmployeeUpdateRequestDTO request = new EmployeeUpdateRequestDTO();
        request.setFullName("  Updated Name  ");
        request.setBirthDate(LocalDate.of(2000, 1, 1));
        request.setPhone("   ");
        request.setIdentityNumber("  new-identity  ");

        service.updateEmployee(100L, request);

        assertEquals("new-identity", employee.getIdentityNumber());
        assertNull(employee.getPhone());
        assertEquals("original-address", employee.getAddress());
    }

    private void stubUpdate(Employee employee) {
        when(employees.findById(100L)).thenReturn(Optional.of(employee));
        when(employees.save(employee)).thenReturn(employee);
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setEmployeeId(100L);
        employee.setEmployeeCode("TEST-001");
        employee.setFullName("Test Employee");
        employee.setHireDate(LocalDate.of(2025, 1, 1));
        employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);
        return employee;
    }

    private EmployeeAssignment assignment(Employee employee, Long id, Long departmentId, LocalDate from) {
        Department department = new Department();
        department.setDepartmentId(departmentId);
        department.setDepartmentName("Department " + departmentId);
        Position position = new Position();
        position.setPositionId(20L);
        position.setPositionName("Specialist");
        EmployeeAssignment assignment = new EmployeeAssignment();
        assignment.setAssignmentId(id);
        assignment.setEmployee(employee);
        assignment.setDepartment(department);
        assignment.setPosition(position);
        assignment.setEffectiveFrom(from);
        return assignment;
    }
}
