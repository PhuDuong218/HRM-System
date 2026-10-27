package com.hrm.hrm.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import com.hrm.dto.EmployeeDetailDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Department;
import com.hrm.model.Employee;
import com.hrm.repository.DepartmentRepository;
import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PositionRepository;
import com.hrm.service.AssignmentGuardService;
import com.hrm.service.EmployeeService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.model.Department;
import com.hrm.model.Position;
import com.hrm.dto.EmployeeContactPatchDTO;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.mockito.Spy;
import com.hrm.model.EmployeeAssignment;
@ExtendWith(MockitoExtension.class)
class EmployeeDetailServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private EmployeeAssignmentRepository assignmentRepository;

    @Mock
    private AssignmentGuardService guard;

    @InjectMocks
    private EmployeeService employeeService;

    @Spy
private Clock businessClock = Clock.fixed(
        Instant.parse("2026-10-23T17:30:00Z"),
        ZoneId.of("Asia/Ho_Chi_Minh"));

    @Test
    void returnsBasicAndDetailedInformation() {

        Employee employee = new Employee();

        employee.setEmployeeId(100L);
        employee.setEmployeeCode("TEST-DETAIL-001");
        employee.setFullName("Nhân viên kiểm thử");
        employee.setHireDate(LocalDate.of(2025, 1, 1));
        employee.setEmploymentStatus(EmploymentStatus.DA_NGHI);

        employee.setAddress("Địa chỉ kiểm thử");
        employee.setIdentityNumber("TEST-IDENTITY-001");
        employee.setTerminationDate(LocalDate.of(2026, 10, 1));

        when(employeeRepository.findById(100L))
                .thenReturn(Optional.of(employee));

        EmployeeDetailDTO result = employeeService.getEmployeeDetailById(100L);

        assertEquals(100L, result.getEmployeeId());
        assertEquals("TEST-DETAIL-001", result.getEmployeeCode());
        assertEquals("Nhân viên kiểm thử", result.getFullName());
        assertEquals("DA_NGHI", result.getEmploymentStatus());

        assertEquals("Địa chỉ kiểm thử", result.getAddress());
        assertEquals("TEST-IDENTITY-001", result.getIdentityNumber());
        assertEquals(
                LocalDate.of(2026, 10, 1),
                result.getTerminationDate());
    }

    @Test
    void rejectsEmployeeThatDoesNotExist() {

        when(employeeRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> employeeService.getEmployeeDetailById(999L));
    }

    @Test
    void createsEmployeeWithAddressAndReturnsItInDetail() {

        Department department = new Department();
        department.setDepartmentId(10L);
        department.setDepartmentName("Phòng kiểm thử");
        department.setIsActive(true);

        Position position = new Position();
        position.setPositionId(20L);
        position.setPositionCode("CHUYEN_VIEN");
        position.setPositionName("Chuyên viên");
        position.setIsActive(true);

        EmployeeRequestDTO request = new EmployeeRequestDTO();
        request.setEmployeeCode("TEST-ADDRESS-001");
        request.setFullName("Nhân viên kiểm thử địa chỉ");
        request.setBirthDate(LocalDate.of(2000, 1, 1));
        request.setHireDate(LocalDate.of(2025, 1, 1));
        request.setDepartmentId(10L);
        request.setPositionId(20L);
        request.setAddress("  12 Đường Kiểm Thử  ");

        when(departmentRepository.findById(10L))
                .thenReturn(Optional.of(department));

        when(positionRepository.findById(20L))
                .thenReturn(Optional.of(position));

        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> {
                    Employee saved = invocation.getArgument(0);
                    saved.setEmployeeId(100L);
                    return saved;
                });

        employeeService.addEmployee(request);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);

        verify(employeeRepository).save(captor.capture());

        Employee savedEmployee = captor.getValue();

        assertEquals(
                "12 Đường Kiểm Thử",
                savedEmployee.getAddress());

        when(employeeRepository.findById(100L))
                .thenReturn(Optional.of(savedEmployee));

        EmployeeDetailDTO detail = employeeService.getEmployeeDetailById(100L);

        assertEquals(
                "12 Đường Kiểm Thử",
                detail.getAddress());
    }

    private Employee createContactTestEmployee() {
    Employee employee = new Employee();

    employee.setEmployeeId(100L);
    employee.setEmployeeCode("TEST-CONTACT-001");
    employee.setFullName("Nhân viên kiểm thử");
    employee.setHireDate(LocalDate.of(2025, 1, 1));
    employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);

    employee.setEmail("old@example.com");
    employee.setPhone("0901234567");
    employee.setAddress("Địa chỉ cũ");
    employee.setIdentityNumber("TEST-CCCD-001");

    return employee;
}

@Test
void updatesAddressWithoutErasingOtherContactFields() {
    Employee employee = createContactTestEmployee();

    when(employeeRepository.findByIdForUpdate(100L))
            .thenReturn(Optional.of(employee));

    when(employeeRepository.findById(100L))
            .thenReturn(Optional.of(employee));

    EmployeeContactPatchDTO request = new EmployeeContactPatchDTO();
    request.setAddress("  Địa chỉ mới  ");

    EmployeeDetailDTO result =
            employeeService.patchEmployeeContact(100L, request);

    assertEquals("Địa chỉ mới", result.getAddress());
    assertEquals("old@example.com", employee.getEmail());
    assertEquals("0901234567", employee.getPhone());
    assertEquals("TEST-CCCD-001", result.getIdentityNumber());

    verify(employeeRepository).saveAndFlush(employee);
}

@Test
void clearsAddressWhenExplicitlyGivenEmptyString() {
    Employee employee = createContactTestEmployee();

    when(employeeRepository.findByIdForUpdate(100L))
            .thenReturn(Optional.of(employee));

    when(employeeRepository.findById(100L))
            .thenReturn(Optional.of(employee));

    EmployeeContactPatchDTO request = new EmployeeContactPatchDTO();
    request.setAddress("");

    EmployeeDetailDTO result =
            employeeService.patchEmployeeContact(100L, request);

    assertNull(result.getAddress());
    assertEquals("old@example.com", employee.getEmail());
    assertEquals("0901234567", employee.getPhone());
    assertEquals("TEST-CCCD-001", result.getIdentityNumber());

    verify(employeeRepository).saveAndFlush(employee);
}
@Test
void selectsAssignmentUsingVietnamDateAtUtcDayBoundary() {
    Employee employee = createContactTestEmployee();

    Department department = new Department();
    department.setDepartmentId(10L);
    department.setDepartmentName("Phòng kiểm thử");

    Position position = new Position();
    position.setPositionId(20L);
    position.setPositionName("Chuyên viên");

    EmployeeAssignment oldAssignment = new EmployeeAssignment();
    oldAssignment.setEmployee(employee);
    oldAssignment.setDepartment(department);
    oldAssignment.setPosition(position);
    oldAssignment.setEffectiveFrom(LocalDate.of(2026, 10, 1));
    oldAssignment.setEffectiveTo(LocalDate.of(2026, 10, 24));

    Department newDepartment = new Department();
    newDepartment.setDepartmentId(11L);
    newDepartment.setDepartmentName("Phòng mới");

    EmployeeAssignment newAssignment = new EmployeeAssignment();
    newAssignment.setEmployee(employee);
    newAssignment.setDepartment(newDepartment);
    newAssignment.setPosition(position);
    newAssignment.setEffectiveFrom(LocalDate.of(2026, 10, 24));

    employee.getAssignments().add(oldAssignment);
    employee.getAssignments().add(newAssignment);

    when(employeeRepository.findById(100L))
            .thenReturn(Optional.of(employee));

    EmployeeDetailDTO result =
            employeeService.getEmployeeDetailById(100L);

    // Tại Việt Nam đã sang 24/10:
    // phân công cũ hết hiệu lực, phân công mới bắt đầu.
    assertEquals(11L, result.getCurrentDepartmentId());
    assertEquals("Phòng mới", result.getCurrentDepartmentName());
}

@Test
void searchUsesSameVietnamDateForFilteringAndAssignments() {
    Employee employee = createContactTestEmployee();

    LocalDate expectedDate = LocalDate.of(2026, 10, 24);

    when(employeeRepository.searchEmployees(
            eq("%test-contact%"),
            isNull(),
            eq(10L),
            eq(20L),
            eq(expectedDate),
            any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(employee)));

    when(assignmentRepository.findCurrentByEmployeeIds(
            List.of(100L), expectedDate))
            .thenReturn(List.of());

    var result = employeeService.searchEmployees(
            "TEST-CONTACT",
            null,
            10L,
            20L,
            0,
            10);

    assertEquals(1, result.getNumberOfElements());

    verify(employeeRepository).searchEmployees(
            eq("%test-contact%"),
            isNull(),
            eq(10L),
            eq(20L),
            eq(expectedDate),
            any(Pageable.class));

    verify(assignmentRepository).findCurrentByEmployeeIds(
            List.of(100L), expectedDate);
}
}