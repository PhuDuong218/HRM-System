package com.hrm.hrm.service;

import java.time.LocalDate;
import java.util.Optional;

import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Employee;
import com.hrm.model.EmployeeAssignment;
import com.hrm.repository.EmployeeRepository;
import com.hrm.service.EmployeeAuditService;
import com.hrm.service.EmployeeCreationAuditService;
import com.hrm.service.EmployeeService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeCreationAuditServiceTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeAuditService auditService;

    @InjectMocks
    private EmployeeCreationAuditService creationAudit;

    private Employee prepare(EmployeeRequestDTO request) {
        Employee employee = new Employee();

        employee.setEmployeeId(100L);
        employee.setEmployeeCode("TEST-CREATE-001");
        employee.setFullName("Nhân viên kiểm thử");
        employee.setHireDate(LocalDate.of(2020, 1, 1));
        employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);

        EmployeeAssignment assignment = new EmployeeAssignment();
        assignment.setEmployee(employee);
        employee.getAssignments().add(assignment);

        EmployeeDTO result = new EmployeeDTO();
        result.setEmployeeId(100L);
        result.setCurrentDepartmentId(10L);
        result.setCurrentPositionId(20L);

        when(employeeService.addEmployee(request)).thenReturn(result);

        when(employeeRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(employee));

        return employee;
    }

    @Test
    void recordsCreatorAndCreationSnapshot() {
        EmployeeRequestDTO request = new EmployeeRequestDTO();
        Employee employee = prepare(request);

        EmployeeDTO result = creationAudit.create(request, 7L);

        assertEquals(100L, result.getEmployeeId());
        assertEquals(7L, employee.getCreatedBy());

        assertEquals(
                7L,
                employee.getAssignments().get(0).getCreatedBy());

        var order = inOrder(
                employeeService, employeeRepository, auditService);

        order.verify(employeeService).addEmployee(request);
        order.verify(employeeRepository).findByIdForUpdate(100L);
        order.verify(employeeRepository).flush();

        order.verify(auditService).recordCreation(
                eq(7L),
                eq(100L),
                argThat(data ->
                        "TEST-CREATE-001".equals(data.get("employeeCode"))
                        && "DANG_LAM".equals(data.get("employmentStatus"))
                        && "10".equals(data.get("departmentId"))
                        && "20".equals(data.get("positionId"))));
    }

    @Test
    void rejectsMissingActorBeforeCreatingEmployee() {
        EmployeeRequestDTO request = new EmployeeRequestDTO();

        assertThrows(
                IllegalArgumentException.class,
                () -> creationAudit.create(request, null));

        verifyNoInteractions(
                employeeService, employeeRepository, auditService);
    }

    @Test
    void propagatesAuditFailure() {
        EmployeeRequestDTO request = new EmployeeRequestDTO();
        prepare(request);

        doThrow(new DataAccessResourceFailureException(
                "Lỗi audit tạo nhân viên giả lập"))
                .when(auditService)
                .recordCreation(eq(7L), eq(100L), anyMap());

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> creationAudit.create(request, 7L));
    }
}
