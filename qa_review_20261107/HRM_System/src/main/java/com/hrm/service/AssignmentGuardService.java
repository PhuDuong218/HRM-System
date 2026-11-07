package com.hrm.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared transaction lock for writes to the assignment timeline. */
@Service
public class AssignmentGuardService {
    public static final String HEAD_CODE = "TRUONG_PHONG";
    private final JdbcTemplate jdbc;

    public AssignmentGuardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTimeline() {
        // SQL Server JDBC starts its physical transaction lazily. A table read starts it
        // before sp_getapplock requests a Transaction-owned lock (also for an empty table).
        jdbc.queryForList("SELECT TOP (1) department_id FROM dbo.departments", Long.class);
        Integer result = jdbc.queryForObject("""
            SET NOCOUNT ON;
            DECLARE @result int;
            EXEC @result = sys.sp_getapplock @Resource=N'HRM_ASSIGNMENT_TIMELINE',
                @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000;
            SELECT @result;
            """, Integer.class);
        if (result == null || result < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phân công đang được xử lý; vui lòng thử lại");
        }
    }

    public void requireNoOpenHead(Long departmentId) {
        if (jdbc.queryForObject("""
            SELECT COUNT_BIG(*) FROM dbo.employee_assignments a
            JOIN dbo.positions p ON p.position_id=a.position_id
            WHERE a.department_id=? AND p.position_code=? AND a.effective_to IS NULL
            """, Long.class, departmentId, HEAD_CODE) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hãy kết thúc hoặc thay trưởng phòng trước khi ngừng sử dụng phòng ban");
        }
    }

    public void requireNoDepartmentAssignments(Long id, java.time.LocalDate asOf) {
        requireNoAssignments("department_id", id, asOf);
    }

    public void requireNoPositionAssignments(Long id, java.time.LocalDate asOf) {
        requireNoAssignments("position_id", id, asOf);
    }

    private void requireNoAssignments(String column, Long id, java.time.LocalDate asOf) {
        // Column names come only from the two internal methods above, never from a request.
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_assignments WHERE "
                + column + "=? AND (effective_to IS NULL OR effective_to>?)", Integer.class, id, asOf);
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Còn phân công hiện tại hoặc tương lai; hãy điều chuyển/kết thúc trước khi ngừng sử dụng");
        }
    }
}
