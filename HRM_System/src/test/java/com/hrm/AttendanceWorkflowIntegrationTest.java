
package com.hrm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.AttendanceDTOs.AttendanceInput;
import com.hrm.dto.AttendanceDTOs.AttendanceCorrection;
import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.service.AttendanceService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=hrm_test;encrypt=false;trustServerCertificate=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
class AttendanceWorkflowIntegrationTest {

    private static final long TEST_ADMIN_ID = 2L;

    @Autowired
    private AttendanceService service;

    @Autowired
    private JdbcTemplate db;

    @Test
    @Transactional("scheduleTransactionManager")
    @Rollback
    void kiemTraDieuChinhVaXacNhanCong() {

        // 1. Dam bao chi kiem thu tren database hrm_test
        String database = db.queryForObject(
                "SELECT DB_NAME()",
                String.class);

        assertEquals(
                "hrm_test",
                database,
                "DUNG TEST: Database khong phai hrm_test!");

        // 2. Kiem tra tai khoan ADMIN va cac quyen can thiet
        Integer adminCount = db.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM dbo.accounts a
                        JOIN dbo.roles r ON r.role_id = a.role_id
                        WHERE a.account_id = ?
                          AND a.employee_id IS NOT NULL
                          AND r.role_code = 'ADMIN'
                        """,
                Integer.class,
                TEST_ADMIN_ID);

        assertEquals(
                1,
                adminCount,
                "Tai khoan kiem thu khong phai ADMIN hop le!");

        Long permissionCount = db.queryForObject(
                """
                        SELECT COUNT(DISTINCT p.permission_code)
                        FROM dbo.accounts a
                        JOIN dbo.role_permissions rp
                            ON rp.role_id = a.role_id
                        JOIN dbo.permissions p
                            ON p.permission_id = rp.permission_id
                        WHERE a.account_id = ?
                          AND p.permission_code IN (
                              'ATTENDANCE_MANAGE',
                              'ATTENDANCE_CONFIRM'
                          )
                        """,
                Long.class,
                TEST_ADMIN_ID);

        assertNotNull(permissionCount);
        assertEquals(
                2L,
                permissionCount.longValue(),
                "ADMIN chua co du quyen cham cong!");

        // 3. Kich hoat tam ADMIN trong transaction kiem thu
        db.update(
                """
                        UPDATE dbo.accounts
                        SET is_active = 1
                        WHERE account_id = ?
                          AND is_active = 0
                        """,
                TEST_ADMIN_ID);

        Integer activeAdminCount = db.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM dbo.accounts
                        WHERE account_id = ?
                          AND is_active = 1
                        """,
                Integer.class,
                TEST_ADMIN_ID);

        assertEquals(
                1,
                activeAdminCount,
                "Khong the kich hoat ADMIN trong transaction test!");

        // 4. Kiem tra lich lam viec cua nhan vien
        Integer scheduleCount = db.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM dbo.work_schedules
                        WHERE schedule_id = 4
                          AND employee_id = 3
                          AND work_date = '2026-10-22'
                        """,
                Integer.class);

        assertEquals(
                1,
                scheduleCount,
                "Khong tim thay lich lam viec kiem thu!");

        // 5. Dam bao lich chua co ban ghi cham cong
        Integer existing = db.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM dbo.attendance_records
                        WHERE schedule_id = 4
                        """,
                Integer.class);

        assertEquals(
                0,
                existing,
                "Lich so 4 da co cham cong!");

        // 6. Tao cham cong chua xac nhan
        AttendanceInput input = new AttendanceInput(
                3L,
                4L,
                LocalDate.of(2026, 10, 22),
                LocalDateTime.of(2026, 10, 22, 14, 10),
                LocalDateTime.of(2026, 10, 22, 22, 0),
                45,
                "DI_LAM",
                "Kiem thu quy trinh cham cong");

        AttendanceView created = service.createManual(input);

        assertEquals(
                "CHUA_XAC_NHAN",
                created.verificationStatus());

        long attendanceId = created.attendanceId();

        System.out.println("PASS: Tao cong thanh cong");

        // 7. Dieu chinh gio vao tu 14:10 thanh 14:05
        AttendanceCorrection correction = new AttendanceCorrection(
                LocalDateTime.of(2026, 10, 22, 14, 5),
                LocalDateTime.of(2026, 10, 22, 22, 0),
                45,
                "DI_LAM",
                "Dieu chinh gio vao tu 14:10 thanh 14:05");

        AttendanceView corrected = service.correct(
                attendanceId,
                correction,
                TEST_ADMIN_ID);

        assertEquals(
                LocalDateTime.of(2026, 10, 22, 14, 5),
                corrected.actualStart());

        assertEquals(
                "CHUA_XAC_NHAN",
                corrected.verificationStatus());

        System.out.println("PASS: Dieu chinh cong thanh cong");

        // 8. Xac nhan cham cong
        AttendanceView confirmed = service.confirm(
                attendanceId,
                TEST_ADMIN_ID);

        assertEquals(
                "DA_XAC_NHAN",
                confirmed.verificationStatus());

        assertEquals(
                TEST_ADMIN_ID,
                confirmed.confirmedBy());

        assertNotNull(confirmed.confirmedAt());

        System.out.println("PASS: Xac nhan cong thanh cong");

        // 9. Kiem tra audit logs dieu chinh va xac nhan
        Integer auditCount = db.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM dbo.audit_logs
                        WHERE target_table = 'attendance_records'
                          AND target_record_key = ?
                          AND action IN (
                              'ATTENDANCE_CORRECT',
                              'ATTENDANCE_CONFIRM'
                          )
                          AND actor_account_id = ?
                          AND ISJSON(before_data_json) = 1
                          AND ISJSON(after_data_json) = 1
                        """,
                Integer.class,
                String.valueOf(attendanceId),
                TEST_ADMIN_ID);

        assertEquals(
                2,
                auditCount,
                "Khong tim thay du hai audit log hop le!");

        System.out.println("PASS: Ghi audit_logs thanh cong");

        // 10. Cam dieu chinh cong da xac nhan
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.correct(
                        attendanceId,
                        correction,
                        TEST_ADMIN_ID));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());

        System.out.println(
                "PASS: Chan dieu chinh cong da xac nhan");

        // @Rollback se rollback transaction khi test ket thuc.
    }
}
