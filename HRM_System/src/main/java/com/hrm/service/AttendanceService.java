package com.hrm.service;

import com.hrm.dto.AttendanceDTOs.AttendanceCorrection;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.AttendanceDTOs.AttendanceInput;
import com.hrm.dto.AttendanceDTOs.AttendanceView;

@Service
public class AttendanceService {

        private final JdbcTemplate db;
        private final AttendancePermissionService permissionService;

        public AttendanceService(JdbcTemplate db, AttendancePermissionService permissionService) {
                this.db = db;
                this.permissionService = java.util.Objects.requireNonNull(permissionService, "permissionService");
        }

        // Chuyển dữ liệu SQL Server sang AttendanceView
        private static final RowMapper<AttendanceView> MAPPER = (rs, rowNum) -> new AttendanceView(
                        rs.getLong("attendance_id"),
                        rs.getLong("employee_id"),
                        rs.getObject("schedule_id", Long.class),
                        rs.getDate("work_date").toLocalDate(),
                        toDateTime(rs.getTimestamp("actual_start")),
                        toDateTime(rs.getTimestamp("actual_end")),
                        rs.getInt("actual_break_minutes"),
                        rs.getString("attendance_result"),
                        rs.getString("verification_status"),
                        rs.getObject("confirmed_by", Long.class),
                        toDateTime(rs.getTimestamp("confirmed_at")),
                        rs.getString("correction_reason"));

        private static LocalDateTime toDateTime(Timestamp value) {
                return value == null ? null : value.toLocalDateTime();
        }

        // Lấy một bản ghi chấm công theo ID
        public AttendanceView get(long attendanceId) {

                return db.query(
                                """
                                                SELECT *
                                                FROM dbo.attendance_records
                                                WHERE attendance_id = ?
                                                """,
                                MAPPER,
                                attendanceId).stream().findFirst().orElseThrow(
                                                () -> new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Không tìm thấy bản ghi chấm công"));
        }

        // Kiểm tra dữ liệu đầu vào
        private void validate(AttendanceInput input) {

                if (input == null
                                || input.employeeId() == null
                                || input.employeeId() <= 0
                                || input.scheduleId() == null
                                || input.scheduleId() <= 0
                                || input.workDate() == null) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Phải chọn nhân viên, lịch làm việc và ngày công");
                }

                if (input.actualBreakMinutes() < 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Thời gian nghỉ không được âm");
                }

                String result = input.attendanceResult();

                if (!"DI_LAM".equals(result)
                                && !"VANG".equals(result)) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Kết quả công chỉ được là DI_LAM hoặc VANG");
                }

                LocalDateTime start = input.actualStart();
                LocalDateTime end = input.actualEnd();

                // Nhân viên vắng mặt không có giờ vào/ra
                if ("VANG".equals(result)) {

                        if (start != null
                                        || end != null
                                        || input.actualBreakMinutes() != 0) {

                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Công vắng mặt không được có giờ vào, giờ ra hoặc phút nghỉ");
                        }

                        return;
                }

                // Nhân viên đi làm phải có giờ vào
                if (start == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Chấm công đi làm phải có giờ bắt đầu");
                }

                // Có thể chưa check-out, tức actualEnd = null
                if (end != null) {

                        if (!end.isAfter(start)) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Giờ ra phải sau giờ vào");
                        }

                        long duration = Duration.between(
                                        start, end).toMinutes();

                        if (input.actualBreakMinutes() > duration) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Phút nghỉ vượt quá thời gian làm việc");
                        }

                } else if (input.actualBreakMinutes() != 0) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Chưa có giờ ra thì phút nghỉ phải bằng 0");
                }
        }

        // Kiểm tra và khóa lịch làm việc trong transaction
        private void validateAndLockSchedule(
                        AttendanceInput input) {

                /*
                 * UPDLOCK + HOLDLOCK:
                 * Ngăn hai yêu cầu đồng thời cùng tạo công
                 * cho một lịch làm việc.
                 *
                 * Phải thực hiện trong JDBC transaction.
                 */
                List<LocalDate> scheduleDates = db.query(
                                """
                                                SELECT work_date
                                                FROM dbo.work_schedules WITH (UPDLOCK, HOLDLOCK)
                                                WHERE schedule_id = ?
                                                  AND employee_id = ?
                                                """,
                                (rs, rowNum) -> rs.getDate("work_date").toLocalDate(),
                                input.scheduleId(),
                                input.employeeId());

                if (scheduleDates.isEmpty()) {
                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Lịch làm việc không tồn tại hoặc không thuộc nhân viên");
                }

                LocalDate scheduleDate = scheduleDates.get(0);

                if (!scheduleDate.equals(input.workDate())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ngày chấm công không khớp ngày của lịch làm việc");
                }
        }

        // Ngăn một lịch có nhiều bản ghi chấm công
        private void assertNoDuplicate(long scheduleId) {

                Integer count = db.queryForObject(
                                """
                                                SELECT COUNT(1)
                                                FROM dbo.attendance_records
                                                WHERE schedule_id = ?
                                                """,
                                Integer.class,
                                scheduleId);

                if (count != null && count > 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Lịch làm việc này đã có bản ghi chấm công");
                }
        }

        // HR / Quản lý nhập công thủ công
        @Transactional("scheduleTransactionManager")
        public AttendanceView createManual(
                        AttendanceInput input, long actorAccountId) {

                // 1. Kiểm tra đầu vào
                validate(input);

                // Actor ID must be supplied by trusted internal/authenticated code.
                permissionService.requireAttendanceAccess(actorAccountId, input.employeeId(),
                                input.workDate(), AttendancePermissionService.MANAGE);

                // 2. Khóa và kiểm tra lịch
                validateAndLockSchedule(input);

                // 3. Kiểm tra đã có công chưa
                assertNoDuplicate(input.scheduleId());

                // 4. Tạo bản ghi chưa xác nhận
                Long attendanceId = db.queryForObject(
                                """
                                                INSERT INTO dbo.attendance_records
                                                (
                                                    employee_id,
                                                    schedule_id,
                                                    work_date,
                                                    actual_start,
                                                    actual_end,
                                                    actual_break_minutes,
                                                    attendance_result,
                                                    verification_status,
                                                    correction_reason,
                                                    created_by
                                                )
                                                OUTPUT INSERTED.attendance_id
                                                VALUES
                                                (
                                                    ?, ?, ?, ?, ?, ?, ?,
                                                    'CHUA_XAC_NHAN', ?, ?
                                                )
                                                """,
                                Long.class,
                                input.employeeId(),
                                input.scheduleId(),
                                input.workDate(),
                                input.actualStart() == null
                                                ? null
                                                : Timestamp.valueOf(input.actualStart()),
                                input.actualEnd() == null
                                                ? null
                                                : Timestamp.valueOf(input.actualEnd()),
                                input.actualBreakMinutes(),
                                input.attendanceResult(),
                                input.correctionReason(),
                                actorAccountId);

                // 5. Trả kết quả vừa tạo
                if (attendanceId == null) {
                        throw new IllegalStateException(
                                        "Không nhận được ID bản ghi chấm công");
                }

                writeAudit(actorAccountId, attendanceId, "ATTENDANCE_CREATE",
                                "Nhap cong thu cong", null, attendanceSnapshot(attendanceId));
                return get(attendanceId);
        }

        // Lấy bản ghi và khóa trong suốt transaction
        private AttendanceView getForUpdate(long attendanceId) {

                return db.query(
                                """
                                                SELECT *
                                                FROM dbo.attendance_records
                                                     WITH (UPDLOCK, HOLDLOCK)
                                                WHERE attendance_id = ?
                                                """,
                                MAPPER,
                                attendanceId).stream().findFirst().orElseThrow(
                                                () -> new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Không tìm thấy bản ghi chấm công"));
        }

        // Kiểm tra tài khoản thực hiện có tồn tại
        // CHƯA thay thế được kiểm tra quyền HR/Quản lý
        private void validateActor(long actorAccountId) {

                if (actorAccountId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Tài khoản thực hiện không hợp lệ");
                }

                Integer count = db.queryForObject(
                                """
                                                SELECT COUNT(*)
                                                FROM dbo.accounts
                                                WHERE account_id = ?
                                                """,
                                Integer.class,
                                actorAccountId);

                if (count == null || count == 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Không tìm thấy tài khoản thực hiện");
                }
        }

        // Lấy ảnh chụp bản ghi ở dạng JSON từ SQL Server
        private String attendanceSnapshot(long attendanceId) {

                return db.queryForObject(
                                """
                                                SELECT (
                                                    SELECT
                                                        attendance_id,
                                                        employee_id,
                                                        schedule_id,
                                                        work_date,
                                                        actual_start,
                                                        actual_end,
                                                        actual_break_minutes,
                                                        attendance_result,
                                                        verification_status,
                                                        confirmed_by,
                                                        confirmed_at,
                                                        correction_reason
                                                    FROM dbo.attendance_records
                                                    WHERE attendance_id = ?
                                                    FOR JSON PATH,
                                                        INCLUDE_NULL_VALUES,
                                                        WITHOUT_ARRAY_WRAPPER
                                                )
                                                """,
                                String.class,
                                attendanceId);
        }

        // Ghi lịch sử trước/sau trong cùng transaction
        private void writeAudit(
                        long actorAccountId,
                        long attendanceId,
                        String action,
                        String reason,
                        String beforeJson,
                        String afterJson) {

                db.update(
                                """
                                                INSERT INTO dbo.audit_logs
                                                (
                                                    actor_account_id,
                                                    occurred_at,
                                                    action,
                                                    target_table,
                                                    target_record_key,
                                                    reason,
                                                    before_data_json,
                                                    after_data_json
                                                )
                                                VALUES (?, SYSUTCDATETIME(), ?, 'attendance_records', ?, ?, ?, ?)
                                                """,
                                actorAccountId,
                                action,
                                String.valueOf(attendanceId),
                                reason,
                                beforeJson,
                                afterJson);
        }

        // HR/Quản lý điều chỉnh công CHƯA xác nhận
        @Transactional("scheduleTransactionManager")
        public AttendanceView correct(
                        long attendanceId,
                        AttendanceCorrection correction,
                        long actorAccountId) {

                if (correction == null
                                || correction.correctionReason() == null
                                || correction.correctionReason().isBlank()) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Phải nhập lý do điều chỉnh chấm công");
                }

                AttendanceView current = getForUpdate(attendanceId);
                // B1: always authorize the locked record before any update/audit.
                permissionService.requireAttendanceAccess(
                                actorAccountId, current.employeeId(), current.workDate(),
                                AttendancePermissionService.MANAGE);

                // Cấm điều chỉnh sau khi đã xác nhận
                if (!"CHUA_XAC_NHAN".equals(
                                current.verificationStatus())) {

                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Chấm công đã xác nhận, phải hủy xác nhận trước");
                }

                // Tận dụng validate() hiện có
                AttendanceInput proposed = new AttendanceInput(
                                current.employeeId(),
                                current.scheduleId(),
                                current.workDate(),
                                correction.actualStart(),
                                correction.actualEnd(),
                                correction.actualBreakMinutes(),
                                correction.attendanceResult(),
                                correction.correctionReason());

                validate(proposed);

                String beforeJson = attendanceSnapshot(attendanceId);

                db.update(
                                """
                                                UPDATE dbo.attendance_records
                                                SET actual_start = ?,
                                                    actual_end = ?,
                                                    actual_break_minutes = ?,
                                                    attendance_result = ?,
                                                    correction_reason = ?,
                                                    updated_at = SYSUTCDATETIME(),
                                                    updated_by = ?
                                                WHERE attendance_id = ?
                                                """,
                                correction.actualStart() == null
                                                ? null
                                                : Timestamp.valueOf(correction.actualStart()),
                                correction.actualEnd() == null
                                                ? null
                                                : Timestamp.valueOf(correction.actualEnd()),
                                correction.actualBreakMinutes(),
                                correction.attendanceResult(),
                                correction.correctionReason(),
                                actorAccountId,
                                attendanceId);

                String afterJson = attendanceSnapshot(attendanceId);

                writeAudit(
                                actorAccountId,
                                attendanceId,
                                "ATTENDANCE_CORRECT",
                                correction.correctionReason(),
                                beforeJson,
                                afterJson);

                return get(attendanceId);
        }

        // HR/Quản lý xác nhận công
        @Transactional("scheduleTransactionManager")
        public AttendanceView confirm(
                        long attendanceId,
                        long actorAccountId) {

                AttendanceView current = getForUpdate(attendanceId);
                var actor = permissionService.requireAttendanceAccess(
                                actorAccountId, current.employeeId(), current.workDate(),
                                AttendancePermissionService.CONFIRM);

                if ("HR".equals(actor.roleCode())) {
                        Long creator = db.query("""
                                        SELECT created_by FROM dbo.attendance_records WITH (HOLDLOCK)
                                        WHERE attendance_id=?
                                        """, rs -> rs.next() ? rs.getObject(1, Long.class) : null, attendanceId);
                        // Legacy records without a verifiable creator require a separate workflow.
                        if (creator == null || creator == actorAccountId) {
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                                "HR cannot approve own or unattributed attendance");
                        }
                        Long lastCorrector = db.query("""
                                        SELECT TOP (1) actor_account_id FROM dbo.audit_logs WITH (HOLDLOCK)
                                        WHERE target_table='attendance_records'
                                          AND target_record_key=? AND action='ATTENDANCE_CORRECT'
                                        ORDER BY occurred_at DESC, audit_id DESC
                                        """, rs -> rs.next() ? rs.getObject(1, Long.class) : null,
                                        String.valueOf(attendanceId));
                        if (lastCorrector != null && lastCorrector == actorAccountId) {
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                                "HR cannot approve attendance they last corrected");
                        }
                }

                if (!"CHUA_XAC_NHAN".equals(
                                current.verificationStatus())) {

                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Bản ghi chấm công đã được xác nhận");
                }

                // Công đi làm phải có giờ ra mới được xác nhận
                if ("DI_LAM".equals(current.attendanceResult())
                                && current.actualEnd() == null) {

                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Chưa check-out nên không thể xác nhận công");
                }

                String beforeJson = attendanceSnapshot(attendanceId);

                db.update(
                                """
                                                UPDATE dbo.attendance_records
                                                SET verification_status = 'DA_XAC_NHAN',
                                                    confirmed_by = ?,
                                                    confirmed_at = SYSUTCDATETIME(),
                                                    updated_at = SYSUTCDATETIME(),
                                                    updated_by = ?
                                                WHERE attendance_id = ?
                                                """,
                                actorAccountId,
                                actorAccountId,
                                attendanceId);

                String afterJson = attendanceSnapshot(attendanceId);

                writeAudit(
                                actorAccountId,
                                attendanceId,
                                "ATTENDANCE_CONFIRM",
                                "Xac nhan cham cong",
                                beforeJson,
                                afterJson);

                return get(attendanceId);
        }

        // Hủy xác nhận chấm công; giữ nguyên giờ công và lịch sử xác nhận trong audit.
        @Transactional("scheduleTransactionManager")
        public AttendanceView unconfirm(
                        long attendanceId,
                        String reason,
                        long actorAccountId) {

                if (reason == null || reason.isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Phải nhập lý do hủy xác nhận chấm công");
                }

                AttendanceView current = getForUpdate(attendanceId);
                var actor = permissionService.requireAttendanceAccess(
                                actorAccountId,
                                current.employeeId(),
                                current.workDate(),
                                AttendancePermissionService.UNCONFIRM);

                if (!"DA_XAC_NHAN".equals(current.verificationStatus())) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Bản ghi chấm công chưa được xác nhận");
                }

                if ("HR".equals(actor.roleCode())
                                && java.util.Objects.equals(current.confirmedBy(), actor.accountId())) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "HR không được tự hủy xác nhận do mình thực hiện");
                }

                String beforeJson = attendanceSnapshot(attendanceId);

                int updated = db.update(
                                """
                                                UPDATE dbo.attendance_records
                                                SET verification_status = 'CHUA_XAC_NHAN',
                                                    confirmed_by = NULL,
                                                    confirmed_at = NULL,
                                                    updated_at = SYSUTCDATETIME(),
                                                    updated_by = ?
                                                WHERE attendance_id = ?
                                                  AND verification_status = 'DA_XAC_NHAN'
                                                """,
                                actorAccountId,
                                attendanceId);

                if (updated != 1) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Trạng thái chấm công đã thay đổi");
                }

                String afterJson = attendanceSnapshot(attendanceId);
                writeAudit(
                                actorAccountId,
                                attendanceId,
                                "ATTENDANCE_UNCONFIRM",
                                reason.trim(),
                                beforeJson,
                                afterJson);

                return get(attendanceId);
        }
}
