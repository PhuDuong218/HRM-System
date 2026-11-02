package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.WorkShiftPermissionService;
import com.hrm.service.WorkShiftService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/shifts")
public class WorkShiftHistoryController {
    private final JdbcTemplate db;
    private final WorkShiftPermissionService permissions;
    private final WorkShiftService shifts;
    public WorkShiftHistoryController(JdbcTemplate db,WorkShiftPermissionService permissions,WorkShiftService shifts){this.db=db;this.permissions=permissions;this.shifts=shifts;}
    public record History(long id,String action,String actor,OffsetDateTime occurredAt,String reason,String beforeJson,String afterJson) {}
    @GetMapping("/{id}/history")
    public List<History> history(@AuthenticationPrincipal AuthenticatedAccount actor,@PathVariable long id){
        permissions.requireManage(actor);
        shifts.get(id);
        return db.query("""
            SELECT h.audit_id,h.action,COALESCE(a.username,N'Hệ thống'),h.occurred_at,h.reason,h.before_data_json,h.after_data_json
            FROM dbo.audit_logs h LEFT JOIN dbo.accounts a ON a.account_id=h.actor_account_id
            WHERE h.target_table='work_shifts' AND h.target_record_key=?
              AND h.action IN ('WORK_SHIFT_CREATE','WORK_SHIFT_UPDATE','WORK_SHIFT_BASIC_INFO_UPDATE','WORK_SHIFT_ACTIVATE','WORK_SHIFT_DEACTIVATE')
            ORDER BY h.occurred_at DESC,h.audit_id DESC
            """,(rs,n)->new History(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getObject(4,LocalDateTime.class).atOffset(ZoneOffset.UTC),rs.getString(5),rs.getString(6),rs.getString(7)),Long.toString(id));
    }
}
