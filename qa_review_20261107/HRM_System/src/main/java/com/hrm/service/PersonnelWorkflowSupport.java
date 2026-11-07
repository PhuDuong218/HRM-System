package com.hrm.service;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PersonnelWorkflowSupport {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditLogService audit;
    private final JsonMapper json=JsonMapper.builder().build();
    public PersonnelWorkflowSupport(JdbcTemplate jdbc,@Qualifier("hrmBusinessClock") Clock clock,AuditLogService audit) { this.jdbc=jdbc;this.clock=clock;this.audit=audit; }
    public LocalDate today(){return LocalDate.now(clock);}
    public LocalDateTime utc(){return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));}
    public LocalDateTime localNow(){return LocalDateTime.now(clock);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void lock() {
        jdbc.queryForList("SELECT TOP(1) department_id FROM dbo.departments");
        Integer result=jdbc.queryForObject("""
            DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'HRM_G2_WORKFLOW',
              @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000; SELECT @r;
            """,Integer.class);
        if(result==null||result<0) throw conflict("Nghiệp vụ nhân sự đang được xử lý, vui lòng thử lại");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void audit(Long actor,String action,String table,long id,String reason,Object before,Object after) {
        audit.record(new com.hrm.dto.AuditEvent(actor,action,table,Long.toString(id),reason,before,after));
    }
    public static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
    public static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
