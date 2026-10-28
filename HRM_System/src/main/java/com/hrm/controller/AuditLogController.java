package com.hrm.controller;

import java.security.Principal;
import java.time.*;
import java.util.*;
import com.hrm.service.*;
import com.hrm.util.PagingUtils;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Central audit search is ADMIN-only: snapshots may contain salary and personal data. */
@RestController @RequestMapping("/audit-logs")
public class AuditLogController {
    private final JdbcTemplate db;private final PersonnelAccessService access;
    public AuditLogController(JdbcTemplate db,PersonnelAccessService access){this.db=db;this.access=access;}
    @GetMapping public Page<Map<String,Object>> search(@RequestParam(required=false) String targetTable,
        @RequestParam(required=false) String recordKey,@RequestParam(required=false) String action,
        @RequestParam(required=false) Long actorAccountId,@RequestParam(required=false) LocalDateTime fromUtc,
        @RequestParam(required=false) LocalDateTime toUtc,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Principal p) {
        access.admin(p);var paging=PagingUtils.create(page,size,Sort.unsorted());
        if((targetTable!=null&&!targetTable.matches("[a-z][a-z0-9_]{0,127}"))||(action!=null&&!action.matches("[A-Z][A-Z0-9_]{0,99}"))
            ||(recordKey!=null&&recordKey.length()>100)||(fromUtc!=null&&toUtc!=null&&!fromUtc.isBefore(toUtc)))throw PersonnelWorkflowSupport.bad("Bộ lọc audit không hợp lệ");
        var clauses=new ArrayList<String>();var args=new ArrayList<Object>();
        if(targetTable!=null){clauses.add("target_table=?");args.add(targetTable);}if(recordKey!=null){clauses.add("target_record_key=?");args.add(recordKey);}
        if(action!=null){clauses.add("action=?");args.add(action);}if(actorAccountId!=null){clauses.add("actor_account_id=?");args.add(actorAccountId);}
        if(fromUtc!=null){clauses.add("occurred_at>=?");args.add(fromUtc);}if(toUtc!=null){clauses.add("occurred_at<?");args.add(toUtc);}
        String where=clauses.isEmpty()?"":" WHERE "+String.join(" AND ",clauses);
        long count=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs"+where,Long.class,args.toArray());
        args.add(paging.getOffset());args.add(size);
        var rows=db.queryForList("SELECT audit_id AS auditId,actor_account_id AS actorAccountId,CONVERT(varchar(23),occurred_at,126)+'Z' AS occurredAt,action,target_table AS targetTable,target_record_key AS recordKey,reason,before_data_json AS beforeDataJson,after_data_json AS afterDataJson,CONVERT(varchar(36),correlation_id) AS correlationId FROM dbo.audit_logs"+where+" ORDER BY audit_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray());
        return new PageImpl<>(rows,paging,count);
    }
}
