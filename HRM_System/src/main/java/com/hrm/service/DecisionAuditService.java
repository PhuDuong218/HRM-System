package com.hrm.service;

import java.sql.Timestamp;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DecisionAuditService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;
    public DecisionAuditService(JdbcTemplate jdbc,JsonMapper json,Clock clock) {
        this.jdbc=jdbc;this.json=json;this.clock=clock;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Long actorId,Long decisionId,String action,String reason,Object before,Object after) {
        jdbc.update("""
            insert into dbo.audit_logs
            (actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json)
            values (?,?,?,'employee_decisions',?,?,?,?)
            """,actorId,Timestamp.valueOf(LocalDateTime.now(clock)),action,decisionId.toString(),reason,
            before==null?null:json.writeValueAsString(before),after==null?null:json.writeValueAsString(after));
    }
}