package com.hrm.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.hrm.security.AuditActor;

@Service
public class BusinessAuditService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public BusinessAuditService(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public boolean hasAction(String table, Long id, String action) {
        Long count = jdbc.queryForObject("""
                select count_big(*) from dbo.audit_logs
                where target_table=? and target_record_key=? and action=?
                """, Long.class, table, id.toString(), action);
        return count != null && count > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            String table,
            Long id,
            String action,
            String reason,
            Object before,
            Object after) {

        Long actorAccountId = AuditActor.currentAccountIdOrNull();

        jdbc.update("""
                insert into dbo.audit_logs
                  (actor_account_id, occurred_at, action, target_table,
                   target_record_key, reason, before_data_json, after_data_json)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                actorAccountId,
                Timestamp.valueOf(LocalDateTime.now(clock)),
                action,
                table,
                id.toString(),
                reason,
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after));
    }
}