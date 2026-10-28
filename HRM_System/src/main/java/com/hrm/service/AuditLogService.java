package com.hrm.service;

import java.time.*;
import java.util.*;
import com.hrm.dto.AuditEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** XA-14: one transactional, UTC, append-only writer for every module. */
@Service
public class AuditLogService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JsonMapper json=JsonMapper.builder().build();
    public AuditLogService(JdbcTemplate jdbc,@Qualifier("hrmBusinessClock") Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public String safeJson(Object value) {
        if(value==null)return null;
        Object tree=json.convertValue(value,Object.class);
        if(value instanceof String text) {
            try { tree=json.readValue(text,Object.class); }
            catch(RuntimeException ex) { throw new IllegalArgumentException("Audit snapshots must be structured JSON",ex); }
        }
        return json.writeValueAsString(redact(tree));
    }
    private Object redact(Object value) {
        if(value instanceof Map<?,?> map){
            var clean=new LinkedHashMap<String,Object>();
            map.forEach((key,item)->{
                String name=key.toString();String normalized=name.replaceAll("[^A-Za-z0-9]","").toLowerCase(Locale.ROOT);
                boolean secret=normalized.contains("password")||normalized.contains("secret")||normalized.contains("token")||normalized.equals("authorization")||normalized.equals("cookie");
                clean.put(name,secret?"[REDACTED]":redact(item));
            });return clean;
        }
        if(value instanceof Collection<?> values)return values.stream().map(this::redact).toList();
        return value;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(AuditEvent event) {
        if(event.actorAccountId()!=null&&event.actorAccountId()<=0)throw new IllegalArgumentException("Invalid audit actor");
        if(event.action()==null||!event.action().matches("[A-Z][A-Z0-9_]{0,99}")||event.targetTable()==null
            ||!event.targetTable().matches("[a-z][a-z0-9_]{0,127}")||event.targetRecordKey()==null||event.targetRecordKey().isBlank()||event.targetRecordKey().length()>100)
            throw new IllegalArgumentException("Invalid audit target/action");
        if(event.reason()!=null&&event.reason().length()>1000)throw new IllegalArgumentException("Audit reason exceeds 1000 characters");
        jdbc.update("""
            INSERT dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json,correlation_id)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,event.actorAccountId(),LocalDateTime.now(clock.withZone(ZoneOffset.UTC)),event.action(),event.targetTable(),event.targetRecordKey(),
            event.reason(),safeJson(event.before()),safeJson(event.after()),com.hrm.util.AuditCorrelation.current());
    }
}
