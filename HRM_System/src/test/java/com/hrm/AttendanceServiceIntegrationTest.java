
package com.hrm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.AttendanceDTOs.AttendanceInput;
import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.service.AttendanceService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=hrm_test;encrypt=false;trustServerCertificate=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
class AttendanceServiceIntegrationTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private JdbcTemplate db;

    @Test
    @Transactional("scheduleTransactionManager")
    @Rollback
    void kiemTraTaoCongVaChongTrung() {

        // 1. Bảo vệ database chính
        String database = db.queryForObject(
                "SELECT DB_NAME()",
                String.class);

        assertEquals(
                "hrm_test",
                database,
                "DUNG TEST: Khong duoc ghi vao database khac!");

        // 2. Kiểm tra lịch số 4 tồn tại
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
                "Khong tim thay lich kiem thu so 4");

        // 3. Đảm bảo chưa có chấm công cho lịch số 4
        Integer existingCount = db.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM dbo.attendance_records
                        WHERE schedule_id = 4
                        """,
                Integer.class);

        assertEquals(
                0,
                existingCount,
                "Lich so 4 da co cham cong, khong chay test");

        // 4. Chuẩn bị dữ liệu chấm công
        AttendanceInput input = new AttendanceInput(
                3L,
                4L,
                LocalDate.of(2026, 10, 22),
                LocalDateTime.of(2026, 10, 22, 14, 5),
                LocalDateTime.of(2026, 10, 22, 22, 0),
                45,
                "DI_LAM",
                "Integration test");

        // 5. Kiểm tra tạo công hợp lệ
        AttendanceView created = attendanceService.createManual(input, 2L);

        assertNotNull(created);
        assertEquals(3L, created.employeeId());
        assertEquals(4L, created.scheduleId());
        assertEquals("DI_LAM", created.attendanceResult());
        assertEquals(
                "CHUA_XAC_NHAN",
                created.verificationStatus());

        System.out.println(
                "PASS: Tao cham cong thanh cong");

        // 6. Xác nhận bản ghi đã được ghi trong transaction
        Integer countAfterCreate = db.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM dbo.attendance_records
                        WHERE schedule_id = 4
                        """,
                Integer.class);

        assertEquals(1, countAfterCreate);

        // 7. Thử tạo chấm công trùng lịch
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> attendanceService.createManual(input, 2L));

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode());

        System.out.println(
                "PASS: Ngan cham cong trung lich");

        // Spring Test sẽ rollback toàn bộ thay đổi
        // khi phương thức kiểm thử kết thúc.
    }
}
