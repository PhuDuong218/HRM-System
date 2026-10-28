package com.hrm.hrm.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.EmployeeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EmployeeAssignmentValidityIntegrationTest {

    @Autowired
    private com.hrm.service.EmployeeService employeeService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("hrmBusinessClock")
    private java.time.Clock businessClock;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmployeeAssignmentRepository assignmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private LocalDate today;
    private String code;
    private Long departmentId;
    private Long positionId;
    private Long employeeId;

    @BeforeEach
    void prepare() {
        String database = jdbc.queryForObject(
                "SELECT DB_NAME()", String.class);

        assertEquals(
                "hrm_test",
                database == null ? "" : database.toLowerCase(Locale.ROOT),
                "Chỉ chạy bộ test này trên database hrm_test");

        // Dùng ngày của database vì truy vấn hiện vẫn dùng CURRENT_DATE.
        // Ngày kiểm tra cố định, không phụ thuộc ngày chạy test.
today = LocalDate.of(2020, 6, 15);

        code = "TEST-VALID-" + UUID.randomUUID()
                .toString().substring(0, 12);

        jdbc.update("""
                INSERT dbo.departments
                    (department_code, department_name, is_active)
                VALUES (?, ?, 1)
                """, code, "Phòng kiểm thử hiệu lực");

        departmentId = jdbc.queryForObject("""
                SELECT department_id
                FROM dbo.departments
                WHERE department_code = ?
                """, Long.class, code);

        jdbc.update("""
                INSERT dbo.positions
                    (position_code, position_name, is_active)
                VALUES (?, ?, 1)
                """, code, "Chức vụ kiểm thử");

        positionId = jdbc.queryForObject("""
                SELECT position_id
                FROM dbo.positions
                WHERE position_code = ?
                """, Long.class, code);

        jdbc.update("""
                INSERT dbo.employees
                    (employee_code, full_name, hire_date, employment_status)
                VALUES (?, ?, ?, 'DANG_LAM')
                """, code, "Nhân viên kiểm thử hiệu lực",
                today.minusYears(1));

        employeeId = jdbc.queryForObject("""
                SELECT employee_id
                FROM dbo.employees
                WHERE employee_code = ?
                """, Long.class, code);
    }

    private void verifyValidity(
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean expectedActive) {

        jdbc.update("""
                INSERT dbo.employee_assignments
                    (employee_id, department_id, position_id,
                     effective_from, effective_to)
                VALUES (?, ?, ?, ?, ?)
                """,
                employeeId, departmentId, positionId,
                effectiveFrom, effectiveTo);

        int expectedCount = expectedActive ? 1 : 0;

        // Truy vấn phân công của một nhân viên.
        var current = assignmentRepository
        .findCurrentAssignments(employeeId, today);

        assertEquals(expectedCount, current.size(),
                "Sai khi lấy phân công hiện tại của một nhân viên");

        // Truy vấn dùng để hiển thị danh sách nhân viên.
        var currentForList = assignmentRepository
        .findCurrentByEmployeeIds(List.of(employeeId), today);

        assertEquals(expectedCount, currentForList.size(),
                "Sai khi lấy phân công cho danh sách nhân viên");

        // Truy vấn tìm kiếm theo phòng ban và chức vụ.
        var searchResult = employeeRepository.searchEmployees(
        "%" + code.toLowerCase(Locale.ROOT) + "%",
        null,
        departmentId,
        positionId,
        today,
        PageRequest.of(0, 1));

        assertEquals(expectedCount, searchResult.getNumberOfElements(),
                "Sai danh sách nhân viên tìm được");

        assertEquals((long) expectedCount, searchResult.getTotalElements(),
                "Sai tổng số nhân viên tìm được");

        if (expectedActive) {
            assertEquals(employeeId,
                    searchResult.getContent().get(0).getEmployeeId());
        }
    }

    @Test
    void includesAssignmentStartingToday() {
        verifyValidity(today, null, true);
    }

    @Test
    void includesAssignmentEndingTomorrow() {
        verifyValidity(
                today.minusDays(10),
                today.plusDays(1),
                true);
    }

    @Test
    void excludesAssignmentEndingToday() {
        verifyValidity(
                today.minusDays(10),
                today,
                false);
    }

    @Test
    void excludesAssignmentStartingTomorrow() {
        verifyValidity(
                today.plusDays(1),
                null,
                false);
    }

    @Test
    void terminationPreservesEmployeeAndClosesAssignmentInDatabase() {
        LocalDate terminationDay = LocalDate.now(businessClock);
        jdbc.update("""
                INSERT dbo.employee_assignments
                    (employee_id, department_id, position_id, effective_from)
                VALUES (?, ?, ?, ?)
                """, employeeId, departmentId, positionId, terminationDay.minusDays(10));

        employeeService.deleteEmployee(employeeId);
        entityManager.flush();
        entityManager.clear();

        assertEquals("DA_NGHI", jdbc.queryForObject("""
                SELECT employment_status FROM dbo.employees WHERE employee_id = ?
                """, String.class, employeeId));
        assertEquals(terminationDay, jdbc.queryForObject("""
                SELECT termination_date FROM dbo.employees WHERE employee_id = ?
                """, LocalDate.class, employeeId));
        assertEquals(terminationDay.plusDays(1), jdbc.queryForObject("""
                SELECT effective_to FROM dbo.employee_assignments WHERE employee_id = ?
                """, LocalDate.class, employeeId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.employees WHERE employee_id = ?
                """, Integer.class, employeeId));
        assertEquals(1, assignmentRepository.findCurrentAssignments(
                employeeId, terminationDay).size());
        assertTrue(assignmentRepository.findCurrentAssignments(
                employeeId, terminationDay.plusDays(1)).isEmpty());
    }
}
