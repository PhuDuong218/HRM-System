
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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=hrm_test;encrypt=false;trustServerCertificate=true",
    "spring.jpa.hibernate.ddl-auto=none"
})
class AttendanceWorkflowIntegrationTest {

    @Autowired
    private AttendanceService service;

    @Autowired
    private JdbcTemplate db;

    @Test
    @Transactional("scheduleTransactionManager")
    @Rollback
    void kiemTraDieuChinhVaXacNhanCong() {

        // 1. Đảm bảo đang sử dụng hrm_test
        String database = db.queryForObject(
            "SELECT DB_NAME()",
            String.class
        );

        assertEquals("hrm_test", database,
            "DUNG TEST: Database khong phai hrm_test!");

        // 2. Kiểm tra tài khoản và lịch kiểm thử
        Integer accountCount = db.queryForObject(
            "SELECT COUNT(*) FROM dbo.accounts WHERE account_id = 1",
            Integer.class
        );

        assertEquals(1, accountCount);

        Integer scheduleCount = db.queryForObject(
            """
            SELECT COUNT(*)
            FROM dbo.work_schedules
            WHERE schedule_id = 4
              AND employee_id = 3
              AND work_date = '2026-10-22'
            """,
            Integer.class
        );

        assertEquals(1, scheduleCount);

        // 3. Đảm bảo lịch chưa có chấm công
        Integer existing = db.queryForObject(
            """
            SELECT COUNT(*)
            FROM dbo.attendance_records
            WHERE schedule_id = 4
            """,
            Integer.class
        );

        assertEquals(0, existing,
            "Lich so 4 da co cham cong!");

        // 4. Tạo công chưa xác nhận
        AttendanceInput input = new AttendanceInput(
            3L,
            4L,
            LocalDate.of(2026, 10, 22),
            LocalDateTime.of(2026, 10, 22, 14, 10),
            LocalDateTime.of(2026, 10, 22, 22, 0),
            45,
            "DI_LAM",
            "Kiem thu quy trinh cham cong"
        );

        AttendanceView created =
            service.createManual(input);

        assertEquals(
            "CHUA_XAC_NHAN",
            created.verificationStatus()
        );

        long attendanceId = created.attendanceId();

        System.out.println("PASS: Tao cong thanh cong");

        // 5. Điều chỉnh thời gian vào
        AttendanceCorrection correction =
            new AttendanceCorrection(
                LocalDateTime.of(2026, 10, 22, 14, 5),
                LocalDateTime.of(2026, 10, 22, 22, 0),
                45,
                "DI_LAM",
                "Dieu chinh gio vao tu 14:10 thanh 14:05"
            );

        AttendanceView corrected = service.correct(
            attendanceId,
            correction,
            1L
        );

        assertEquals(
            LocalDateTime.of(2026, 10, 22, 14, 5),
            corrected.actualStart()
        );

        assertEquals(
            "CHUA_XAC_NHAN",
            corrected.verificationStatus()
        );

        System.out.println("PASS: Dieu chinh cong thanh cong");

        // 6. Xác nhận công
        AttendanceView confirmed = service.confirm(
            attendanceId,
            1L
        );

        assertEquals(
            "DA_XAC_NHAN",
            confirmed.verificationStatus()
        );

        assertEquals(1L, confirmed.confirmedBy());

        assertNotNull(confirmed.confirmedAt());

        System.out.println("PASS: Xac nhan cong thanh cong");

        // 7. Kiểm tra lịch sử điều chỉnh + xác nhận
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
              AND actor_account_id = 1
              AND ISJSON(before_data_json) = 1
              AND ISJSON(after_data_json) = 1
            """,
            Integer.class,
            String.valueOf(attendanceId)
        );

        assertEquals(2, auditCount);

        System.out.println("PASS: Ghi audit_logs thanh cong");

        // 8. Không cho điều chỉnh công đã xác nhận
        ResponseStatusException exception =
            assertThrows(
                ResponseStatusException.class,
                () -> service.correct(
                    attendanceId,
                    correction,
                    1L
                )
            );

        assertEquals(
            HttpStatus.CONFLICT,
            exception.getStatusCode()
        );

        System.out.println(
            "PASS: Chan dieu chinh cong da xac nhan"
        );
    }
}
