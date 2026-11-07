package com.hrm.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.hrm.security.AuditActor;

@Service
public class ContractAuditService {
    private final JdbcTemplate jdbc;

    public ContractAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasRenewal(Long id) {
        Long count = jdbc.queryForObject("""
                select count_big(*) from dbo.audit_logs
                where target_table = 'contracts'
                  and target_record_key = ? and action = 'CONTRACT_RENEW'
                """, Long.class, id.toString());
        return count != null && count > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            String action,
            Long id,
            String reason,
            String beforeJson,
            String afterJson) {

        Long actorAccountId = AuditActor.currentAccountIdOrNull();

        jdbc.update("""
                insert into dbo.audit_logs
                    (actor_account_id, occurred_at, action, target_table,
                     target_record_key, reason, before_data_json, after_data_json)
                values (?, ?, ?, 'contracts', ?, ?, ?, ?)
                """,
                actorAccountId,
                Timestamp.valueOf(LocalDateTime.now()),
                action,
                id.toString(),
                reason,
                beforeJson,
                afterJson);
    }
}