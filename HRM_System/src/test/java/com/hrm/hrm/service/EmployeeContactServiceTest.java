package com.hrm.hrm.service;

import java.security.Principal;
import java.util.Optional;

import com.hrm.dto.EmployeeContactPatchDTO;
import com.hrm.dto.EmployeeDetailDTO;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;
import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.EmployeeAuditService;
import com.hrm.service.EmployeeContactService;
import com.hrm.service.EmployeeService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeContactServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private DepartmentHeadAccessService accessService;

    @Mock
    private EmployeeAuditService auditService;

    @InjectMocks
    private EmployeeContactService contactService;

    private final Principal manager = () -> "hr-test";

    private Employee prepareEmployee() {
        Employee employee = new Employee();

        employee.setEmployeeId(100L);
        employee.setAddress("Địa chỉ cũ");
        employee.setPhone("0901234567");

        when(accessService.requireManager(manager)).thenReturn(7L);

        when(employeeRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(employee));

        return employee;
    }

    @Test
    void updatesContactAndRecordsBeforeAfterWithAuthenticatedActor() {
        Employee employee = prepareEmployee();

        EmployeeContactPatchDTO request = new EmployeeContactPatchDTO();
        request.setAddress("  Địa chỉ mới  ");

        EmployeeDetailDTO expected = new EmployeeDetailDTO();

        when(employeeService.patchEmployeeContact(100L, request))
                .thenAnswer(invocation -> {
                    employee.setAddress("Địa chỉ mới");
                    return expected;
                });

        EmployeeDetailDTO result =
                contactService.patchContact(100L, request, manager);

        assertSame(expected, result);
        assertEquals(7L, employee.getUpdatedBy());

        verify(auditService).recordContactUpdate(
                eq(7L),
                eq(100L),
                argThat(data ->
                        "Địa chỉ cũ".equals(data.get("address"))
                        && "0901234567".equals(data.get("phone"))),
                argThat(data ->
                        "Địa chỉ mới".equals(data.get("address"))
                        && "0901234567".equals(data.get("phone"))));

        var order = inOrder(
                accessService, employeeRepository,
                employeeService, auditService);

        order.verify(accessService).requireManager(manager);
        order.verify(employeeRepository).findByIdForUpdate(100L);
        order.verify(employeeService)
                .patchEmployeeContact(100L, request);

        order.verify(auditService).recordContactUpdate(
                eq(7L), eq(100L), anyMap(), anyMap());
    }

    @Test
    void unchangedContactDoesNotWriteOrCreateAudit() {
        Employee employee = prepareEmployee();

        EmployeeContactPatchDTO request = new EmployeeContactPatchDTO();
        request.setAddress("  Địa chỉ cũ  ");

        EmployeeDetailDTO expected = new EmployeeDetailDTO();

        when(employeeService.getEmployeeDetailById(100L))
                .thenReturn(expected);

        EmployeeDetailDTO result =
                contactService.patchContact(100L, request, manager);

        assertSame(expected, result);
        assertNull(employee.getUpdatedBy());

        verify(employeeService, never())
                .patchEmployeeContact(anyLong(), any());

        verifyNoInteractions(auditService);
    }

    @Test
    void unauthorizedRequestCannotReadOrUpdateEmployee() {
        when(accessService.requireManager(null))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED));

        EmployeeContactPatchDTO request = new EmployeeContactPatchDTO();
        request.setAddress("Địa chỉ mới");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> contactService.patchContact(
                        100L, request, null));

        assertEquals(401, error.getStatusCode().value());

        verifyNoInteractions(
                employeeRepository, employeeService, auditService);
    }

    @Test
    void auditFailureIsPropagatedInsteadOfBeingIgnored() {
        Employee employee = prepareEmployee();

        EmployeeContactPatchDTO request = new EmployeeContactPatchDTO();
        request.setAddress("Địa chỉ mới");

        when(employeeService.patchEmployeeContact(100L, request))
                .thenAnswer(invocation -> {
                    employee.setAddress("Địa chỉ mới");
                    return new EmployeeDetailDTO();
                });

        doThrow(new DataAccessResourceFailureException(
                "Lỗi ghi nhật ký giả lập"))
                .when(auditService)
                .recordContactUpdate(
                        eq(7L), eq(100L), anyMap(), anyMap());

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> contactService.patchContact(
                        100L, request, manager));
    }
}