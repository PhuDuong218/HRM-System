package com.hrm.controller;

import java.security.Principal;
import java.time.*;
import java.util.Set;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.util.PagingUtils;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/g1/audit")
public class G1AuditController {
    private final JdbcTemplate jdbc;
    private final DepartmentHeadAccessService access;
    public G1AuditController(JdbcTemplate jdbc, DepartmentHeadAccessService access) {
        this.jdbc=jdbc; this.access=access;
    }

    public record Entry(Long auditId, Long actorAccountId, String occurredAt, String action,
            String targetTable, String targetRecordKey, String reason, String beforeDataJson, String afterDataJson) {}

    @GetMapping
    public Page<Entry> history(@RequestParam String targetTable, @RequestParam Long recordId,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size, Principal principal) {
        access.requireManager(principal);
        if (!Set.of("employees","departments","positions").contains(targetTable) || recordId<=0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Đối tượng audit G1 không hợp lệ");
        Pageable paging=PagingUtils.create(page,size,Sort.by(Sort.Direction.DESC,"auditId"));
        String idColumn=switch(targetTable) { case "employees" -> "employee_id"; case "departments" -> "department_id"; default -> "position_id"; };
        // Both table and column are selected from a closed server-side whitelist.
        Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM dbo."+targetTable+" WHERE "+idColumn+"=?",Integer.class,recordId);
        if(exists==null || exists==0) throw new ResourceNotFoundException("Không tìm thấy đối tượng audit: "+recordId);
        Long count=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table=? AND target_record_key=?",Long.class,targetTable,recordId.toString());
        var rows=jdbc.query("""
                SELECT audit_id,actor_account_id,occurred_at,action,target_table,target_record_key,
                    reason,before_data_json,after_data_json FROM dbo.audit_logs
                WHERE target_table=? AND target_record_key=?
                ORDER BY audit_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """,(rs,n)->new Entry(rs.getLong("audit_id"),(Long)rs.getObject("actor_account_id"),
                        rs.getObject("occurred_at",LocalDateTime.class).atOffset(ZoneOffset.UTC).toString(),
                        rs.getString("action"),rs.getString("target_table"),rs.getString("target_record_key"),
                        rs.getString("reason"),rs.getString("before_data_json"),rs.getString("after_data_json")),
                targetTable,recordId.toString(),paging.getOffset(),paging.getPageSize());
        return new PageImpl<>(rows,paging,count==null?0:count);
    }
}
