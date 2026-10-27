
package com.hrm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

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

                // 4. Dữ liệu ADMIN kiểm thử độc lập, tạo và rollback trong cùng transaction.
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                                "Test phải chạy trong transaction để rollback dữ liệu kiểm thử");

                String suffix = UUID.randomUUID().toString().replace("-", "");
                Long testAdminEmployeeId = db.queryForObject("""
                                INSERT INTO dbo.employees
                                    (employee_code, full_name, hire_date, employment_status, termination_date)
                                OUTPUT INSERTED.employee_id
                                VALUES (?, N'ADMIN integration test (rollback)', ?, 'DANG_LAM', NULL)
                                """, Long.class, "ITADM_" + suffix, LocalDate.of(2025, 1, 1));
                assertNotNull(testAdminEmployeeId);
                assertNotEquals(3L, testAdminEmployeeId.longValue());

                // Hash giả chỉ tồn tại trong giao dịch rollback; không sử dụng để đăng nhập.
                String unusableHash = "!".repeat(60);
                Long testAdminAccountId = db.queryForObject("""
                                INSERT INTO dbo.accounts
                                    (employee_id, role_id, username, password_hash, is_active, token_version)
                                OUTPUT INSERTED.account_id
                                SELECT ?, r.role_id, ?, ?, 1, 0
                                FROM dbo.roles r
                                WHERE r.role_code = 'ADMIN'
                                """, Long.class, testAdminEmployeeId, "it_admin_" + suffix, unusableHash);
                assertNotNull(testAdminAccountId, "Không thể tạo ADMIN test: thiếu vai trò ADMIN");

                // Fail-fast: cần quyền do cấu hình RBAC hiện có cấp, không tự sửa
                // role_permissions.
                Long hasPermission = db.queryForObject("""
                                SELECT COUNT_BIG(*)
                                FROM dbo.accounts a
                                JOIN dbo.role_permissions rp ON rp.role_id = a.role_id
                                JOIN dbo.permissions p ON p.permission_id = rp.permission_id
                                WHERE a.account_id = ? AND a.is_active = 1
                                  AND p.permission_code = 'ATTENDANCE_MANAGE'
                                """, Long.class, testAdminAccountId);
                assertEquals(1L, hasPermission, "Vai trò ADMIN chưa có quyền ATTENDANCE_MANAGE");

                // 5. Chuẩn bị dữ liệu chấm công
                AttendanceInput input = new AttendanceInput(
                                3L,
                                4L,
                                LocalDate.of(2026, 10, 22),
                                LocalDateTime.of(2026, 10, 22, 14, 5),
                                LocalDateTime.of(2026, 10, 22, 22, 0),
                                45,
                                "DI_LAM",
                                "Integration test");

                // 6. Kiểm tra tạo công hợp lệ
                AttendanceView created = attendanceService.createManual(input, testAdminAccountId);

                assertNotNull(created);
                assertEquals(3L, created.employeeId());
                assertEquals(4L, created.scheduleId());
                assertEquals("DI_LAM", created.attendanceResult());
                assertEquals(
                                "CHUA_XAC_NHAN",
                                created.verificationStatus());

                System.out.println(
                                "PASS: Tao cham cong thanh cong");

                // 7. Xác nhận bản ghi đã được ghi trong transaction
                Integer countAfterCreate = db.queryForObject(
                                """
                                                SELECT COUNT(*)
                                                FROM dbo.attendance_records
                                                WHERE schedule_id = 4
                                                """,
                                Integer.class);

                assertEquals(1, countAfterCreate);

                // 8. Thử tạo chấm công trùng lịch
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> attendanceService.createManual(input, testAdminAccountId));

                assertEquals(
                                HttpStatus.CONFLICT,
                                exception.getStatusCode());

                System.out.println(
                                "PASS: Ngan cham cong trung lich");

                // Spring Test sẽ rollback toàn bộ thay đổi
                // khi phương thức kiểm thử kết thúc.
        }
}
