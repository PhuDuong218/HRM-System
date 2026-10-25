package com.hrm.service;

import java.time.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;

import static com.hrm.dto.WorkTimeDTOs.*;

/**
 * Check overlap and INSERT/UPDATE share a SQL Server transaction-scoped
 * employee lock.
 */
@Service
public class WorkScheduleService {
    private final JdbcTemplate db;
    private final ScheduleWritePermissionService permissions;

    public WorkScheduleService(JdbcTemplate db, ScheduleWritePermissionService permissions) {
        this.db = db;
        this.permissions = permissions;
    }

    private static final RowMapper<ScheduleView> MAPPER = (r, n) -> new ScheduleView(
            r.getLong("schedule_id"),
            r.getLong("employee_id"),
            r.getObject("shift_id", Long.class),
            r.getDate("work_date").toLocalDate(),
            r.getTimestamp("planned_start").toLocalDateTime(),
            r.getTimestamp("planned_end").toLocalDateTime(),
            r.getInt("planned_break_minutes"),
            r.getString("note"));

    public List<ScheduleView> list(long employeeId, LocalDate from, LocalDate to) {
        return db.query(
                "SELECT * FROM dbo.work_schedules WHERE employee_id=? AND planned_start < ? AND planned_end > ? ORDER BY planned_start",
                MAPPER,
                employeeId,
                Timestamp.valueOf(to.plusDays(1).atStartOfDay()),
                Timestamp.valueOf(from.atStartOfDay()));
    }

    public ScheduleView get(long id) {
        return db.query("SELECT * FROM dbo.work_schedules WHERE schedule_id=?", MAPPER, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch"));
    }

    private void validate(ScheduleInput v) {
        if (v.employeeId() == null || v.plannedStart() == null || v.plannedEnd() == null
                || !v.plannedEnd().isAfter(v.plannedStart())) {
            throw new IllegalArgumentException("Thời gian lịch không hợp lệ");
        }
        long duration = Duration.between(v.plannedStart(), v.plannedEnd()).toMinutes();
        if (v.plannedBreakMinutes() < 0 || v.plannedBreakMinutes() > duration) {
            throw new IllegalArgumentException("Phút nghỉ không hợp lệ");
        }
    }

    private void lockEmployee(long employeeId) {

        // Thực hiện truy vấn bảng thực để khởi tạo
        // giao dịch SQL Server khi JDBC dùng implicit transaction.
        Long foundEmployee = db.queryForObject(
                """
                        SELECT employee_id
                        FROM dbo.employees
                        WHERE employee_id = ?
                        """,
                Long.class,
                employeeId);

        db.execute((ConnectionCallback<Void>) connection -> {

            try (Statement check = connection.createStatement();
                    ResultSet rs = check.executeQuery(
                            "SELECT @@TRANCOUNT")) {

                if (!rs.next() || rs.getInt(1) <= 0) {
                    throw new IllegalStateException(
                            "SQL transaction chưa hoạt động trên kết nối JDBC");
                }

                System.out.println(
                        "SQL TRANSACTION COUNT = " + rs.getInt(1));
            }

            try (CallableStatement st = connection.prepareCall(
                    "{? = call sys.sp_getapplock(?, ?, ?, ?, ?)}")) {

                st.registerOutParameter(1, Types.INTEGER);
                st.setString(2, "HRM_SCHEDULE_EMPLOYEE_" + employeeId);
                st.setString(3, "Exclusive");
                st.setString(4, "Transaction");
                st.setInt(5, 10000);
                st.setString(6, "public");

                st.execute();

                int result = st.getInt(1);

                System.out.println(
                        "SP_GETAPPLOCK RESULT = " + result);

                if (result < 0) {
                    throw new IllegalStateException(
                            "Không thể khóa lịch nhân viên. Mã: " + result);
                }
            }

            return null;
        });
    }

    /**
     * Shared row lock is held until transaction end; blocks concurrent
     * deactivate/delete.
     */
    private void requireActiveShift(long shiftId) {
        Boolean active = db.query("""
                SELECT is_active FROM dbo.work_shifts WITH (HOLDLOCK)
                WHERE shift_id=?
                """, rs -> rs.next() ? rs.getBoolean(1) : null, shiftId);
        if (active == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ca không tồn tại");
        }
        if (!active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ca đã ngừng hoạt động");
        }
    }

    private void assertNoOverlap(long emp, LocalDateTime start, LocalDateTime end, long excluding) {
        Integer n = db.queryForObject(
                "SELECT COUNT(1) FROM dbo.work_schedules WHERE employee_id=? AND schedule_id<>? AND planned_start < ? AND planned_end > ?",
                Integer.class,
                emp,
                excluding,
                Timestamp.valueOf(end),
                Timestamp.valueOf(start));
        if (n != null && n > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lịch làm việc bị chồng lấn");
        }
    }

    private void rejectStarted(LocalDateTime start) {
        if (!LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không được tạo hoặc chuyển lịch về thời điểm đã bắt đầu");
        }
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleView create(AuthenticatedAccount actor, ScheduleInput v) {
        validate(v);
        lockEmployee(v.employeeId());
        rejectStarted(v.plannedStart());
        permissions.requireCreate(actor, v.employeeId(), v.plannedStart());
        if (v.shiftId() != null) {
            requireActiveShift(v.shiftId());
        }
        assertNoOverlap(v.employeeId(), v.plannedStart(), v.plannedEnd(), -1);

        Long id = db.queryForObject(
                "INSERT INTO dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note) OUTPUT INSERTED.schedule_id VALUES (?,?,?,?,?,?,?)",
                Long.class,
                v.employeeId(),
                v.shiftId(),
                v.plannedStart().toLocalDate(),
                Timestamp.valueOf(v.plannedStart()),
                Timestamp.valueOf(v.plannedEnd()),
                v.plannedBreakMinutes(),
                v.note());
        return get(id);
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleView update(AuthenticatedAccount actor, long id, ScheduleInput v) {
        validate(v);
        ScheduleView previous = get(id);

        // Immutable ownership: changing the employee means creating a distinct
        // schedule, not silently transferring.
        if (previous.employeeId() != v.employeeId()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không được đổi chủ lịch");
        }

        lockEmployee(v.employeeId());
        previous = get(id);
        permissions.requireUpdate(actor, previous, v);

        if (!LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).isBefore(previous.plannedStart())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ca đã bắt đầu, không được sửa lịch");
        }

        rejectStarted(v.plannedStart());
        if (!Objects.equals(previous.shiftId(), v.shiftId()) && v.shiftId() != null) {
            requireActiveShift(v.shiftId());
        }
        assertNoOverlap(v.employeeId(), v.plannedStart(), v.plannedEnd(), id);

        db.update(
                "UPDATE dbo.work_schedules SET shift_id=?,work_date=?,planned_start=?,planned_end=?,planned_break_minutes=?,note=? WHERE schedule_id=?",
                v.shiftId(),
                v.plannedStart().toLocalDate(),
                Timestamp.valueOf(v.plannedStart()),
                Timestamp.valueOf(v.plannedEnd()),
                v.plannedBreakMinutes(),
                v.note(),
                id);
        return get(id);
    }
}