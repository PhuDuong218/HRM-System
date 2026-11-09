package com.hrm.controller;

import java.security.Principal;
import java.time.*;
import java.util.*;
import com.hrm.service.*;
import com.hrm.util.PagingUtils;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Central audit feed. HR receives only entries tied to managed personnel or departments. */
@RestController @RequestMapping("/audit-logs")
public class AuditLogController {
    private final JdbcTemplate db;
    private final PersonnelAccessService access;
    private final AuditReadPolicy policy;
    public AuditLogController(JdbcTemplate db,PersonnelAccessService access,AuditReadPolicy policy){
        this.db=db;this.access=access;this.policy=policy;
    }
    @GetMapping public Page<Map<String,Object>> search(@RequestParam(required=false) String targetTable,
        @RequestParam(required=false) String recordKey,@RequestParam(required=false) String action,
        @RequestParam(required=false) Long actorAccountId,@RequestParam(required=false) LocalDateTime fromUtc,
        @RequestParam(required=false) LocalDateTime toUtc,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Principal p) {
        var actor=access.manager(p);
        boolean hr="HR".equals(actor.role());
        if(!hr&&!"ADMIN".equals(actor.role())) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN,"Chỉ ADMIN và HR được tra cứu nhật ký tổng hợp");
        var paging=PagingUtils.create(page,size,Sort.unsorted());
        if((targetTable!=null&&!targetTable.matches("[a-z][a-z0-9_]{0,127}"))||(action!=null&&!action.matches("[A-Z][A-Z0-9_]{0,99}"))
            ||(recordKey!=null&&recordKey.length()>100)||(fromUtc!=null&&toUtc!=null&&!fromUtc.isBefore(toUtc)))throw PersonnelWorkflowSupport.bad("Bộ lọc audit không hợp lệ");
        var clauses=new ArrayList<String>();var args=new ArrayList<Object>();
        if(hr) clauses.add(policy.hrFeedPredicate());
        if(targetTable!=null){clauses.add("a.target_table=?");args.add(targetTable);}if(recordKey!=null){clauses.add("a.target_record_key=?");args.add(recordKey);}
        if(action!=null){clauses.add("a.action=?");args.add(action);}if(actorAccountId!=null){clauses.add("a.actor_account_id=?");args.add(actorAccountId);}
        if(fromUtc!=null){clauses.add("a.occurred_at>=?");args.add(fromUtc);}if(toUtc!=null){clauses.add("a.occurred_at<?");args.add(toUtc);}
        String where=clauses.isEmpty()?"":" WHERE "+String.join(" AND ",clauses);
        long count=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs a"+where,Long.class,args.toArray());
        args.add(paging.getOffset());args.add(size);
        var rows=db.queryForList("""
            SELECT a.audit_id AS auditId,a.actor_account_id AS actorAccountId,
              COALESCE(NULLIF(TRIM(e.full_name), '') COLLATE DATABASE_DEFAULT,
                       NULLIF(TRIM(u.username), '') COLLATE DATABASE_DEFAULT) AS actorName,
              CONVERT(varchar(23),a.occurred_at,126)+'Z' AS occurredAt,a.action,
              a.target_table AS targetTable,a.target_record_key AS recordKey,a.reason,
              a.before_data_json AS beforeDataJson,a.after_data_json AS afterDataJson,
              CONVERT(varchar(36),a.correlation_id) AS correlationId
            FROM dbo.audit_logs a LEFT JOIN dbo.accounts u ON u.account_id=a.actor_account_id
            LEFT JOIN dbo.employees e ON e.employee_id=u.employee_id
            """+where+" ORDER BY a.audit_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray());
        return new PageImpl<>(rows,paging,count);
    }
}
