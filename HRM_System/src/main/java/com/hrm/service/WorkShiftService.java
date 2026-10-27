package com.hrm.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.hrm.security.AuthenticatedAccount;
import static com.hrm.dto.WorkTimeDTOs.*;

@Service
public class WorkShiftService {
  private final JdbcTemplate db;
  private final WorkShiftPermissionService permissions;

  public WorkShiftService(JdbcTemplate db, WorkShiftPermissionService permissions) {
    this.db = db;
    this.permissions = permissions;
  }

  private static final RowMapper<ShiftView> MAPPER = (r, n) -> new ShiftView(
      r.getLong("shift_id"), r.getString("shift_code"), r.getString("shift_name"),
      r.getTime("start_time").toLocalTime(), r.getTime("end_time").toLocalTime(),
      r.getInt("end_day_offset"), r.getInt("break_minutes"), r.getBoolean("is_active"));

  public List<ShiftView> list() {
    return db.query("SELECT * FROM dbo.work_shifts ORDER BY shift_id", MAPPER);
  }

  public ShiftView get(long id) {
    return db.query("SELECT * FROM dbo.work_shifts WHERE shift_id=?", MAPPER, id).stream()
        .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ca"));
  }

  private void validate(ShiftInput v) {
    if (v == null || v.startTime() == null || v.endTime() == null || v.shiftCode() == null || v.shiftCode().isBlank()
        || v.shiftName() == null || v.shiftName().isBlank())
      throw new IllegalArgumentException("Thiếu mã, tên hoặc thời gian ca");
    long mins = Duration.between(v.startTime(), v.endTime()).toMinutes() + 1440L * v.endDayOffset();
    if (v.endDayOffset() < 0 || v.endDayOffset() > 1 || mins <= 0 || mins > 1440 || v.breakMinutes() < 0
        || v.breakMinutes() > mins)
      throw new IllegalArgumentException("Thời lượng ca hoặc phút nghỉ không hợp lệ");
  }

  /**
   * Lock the parent shift row before checking references or updating its state.
   */
  private ShiftView lockShift(long id) {
    return db.query("SELECT * FROM dbo.work_shifts WITH (UPDLOCK, HOLDLOCK) WHERE shift_id=?", MAPPER, id)
        .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ca"));
  }

  private boolean hasScheduleReference(long id) {
    Long count = db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.work_schedules WHERE shift_id=?", Long.class, id);
    return count != null && count > 0;
  }

  @Transactional("scheduleTransactionManager")
  public ShiftView create(AuthenticatedAccount actor, ShiftInput v) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Use detailed break-aware work time API");
  }

  @Transactional("scheduleTransactionManager")
  ShiftView createWithVerifiedBreaks(AuthenticatedAccount actor, ShiftInput v) {
    validate(v);
    permissions.requireManage(actor);
    Long id = db.queryForObject(
        "INSERT INTO dbo.work_shifts(shift_code,shift_name,start_time,end_time,end_day_offset,break_minutes,is_active) OUTPUT INSERTED.shift_id VALUES (?,?,?,?,?,?,?)",
        Long.class, v.shiftCode().trim(), v.shiftName().trim(), v.startTime(), v.endTime(), v.endDayOffset(),
        v.breakMinutes(), v.active());
    return get(id);
  }

  @Transactional("scheduleTransactionManager")
  public ShiftView update(AuthenticatedAccount actor, long id, ShiftInput v) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Use detailed break-aware work time API");
  }

  @Transactional("scheduleTransactionManager")
  ShiftView updateWithVerifiedBreaks(AuthenticatedAccount actor, long id, ShiftInput v) {
    validate(v);
    permissions.requireManage(actor);
    ShiftView old = lockShift(id);
    boolean timeChanged = !old.startTime().equals(v.startTime()) || !old.endTime().equals(v.endTime())
        || old.endDayOffset() != v.endDayOffset() || old.breakMinutes() != v.breakMinutes();
    if (timeChanged && hasScheduleReference(id))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ca đã được sử dụng, không thể sửa thời gian");
    int count = db.update(
        "UPDATE dbo.work_shifts SET shift_code=?,shift_name=?,start_time=?,end_time=?,end_day_offset=?,break_minutes=?,is_active=? WHERE shift_id=?",
        v.shiftCode().trim(), v.shiftName().trim(), v.startTime(), v.endTime(), v.endDayOffset(), v.breakMinutes(),
        v.active(), id);
    if (count == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ca");
    return get(id);
  }

  @Transactional("scheduleTransactionManager")
  public void deleteOrDeactivate(AuthenticatedAccount actor, long id) {
    permissions.requireManage(actor);
    lockShift(id);
    db.update("UPDATE dbo.work_shifts SET is_active=0 WHERE shift_id=?", id);
  }
}
