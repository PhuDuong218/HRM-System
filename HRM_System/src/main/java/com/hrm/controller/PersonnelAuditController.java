package com.hrm.controller;

import java.security.Principal;
import java.time.*;
import java.util.Map;
import com.hrm.service.PersonnelAccessService;
import com.hrm.service.PersonnelWorkflowSupport;
import com.hrm.util.PagingUtils;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/personnel/audit","/g2/audit"})
public class PersonnelAuditController {
    private final JdbcTemplate jdbc;
    private final PersonnelAccessService access;
    public PersonnelAuditController(JdbcTemplate jdbc,PersonnelAccessService access){this.jdbc=jdbc;this.access=access;}
    @GetMapping
    public Page<Map<String,Object>> history(@RequestParam String targetTable,@RequestParam long recordId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal p) {
        access.manager(p);
        if(!java.util.Set.of("qualification_levels","employee_qualifications","leave_types","leave_entitlements","employee_requests").contains(targetTable)||recordId<=0)
            throw PersonnelWorkflowSupport.bad("Đối tượng audit G2 không hợp lệ");
        var paging=PagingUtils.create(page,size,Sort.unsorted());
        long count=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table=? AND target_record_key=?",Long.class,targetTable,Long.toString(recordId));
        var rows=jdbc.queryForList("""
            SELECT audit_id AS auditId,actor_account_id AS actorAccountId,CONVERT(varchar(23),occurred_at,126)+'Z' AS occurredAt,
              action,reason,before_data_json AS beforeDataJson,after_data_json AS afterDataJson
            FROM dbo.audit_logs WHERE target_table=? AND target_record_key=? ORDER BY audit_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """,targetTable,Long.toString(recordId),paging.getOffset(),size);
        return new PageImpl<>(rows,paging,count);
    }
}
