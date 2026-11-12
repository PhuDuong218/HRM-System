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

    public record HistoryEntry(long auditId, String occurredAt, String action, String actor, String reason) {}

    public java.util.List<HistoryEntry> history(Long id) {
        return jdbc.query("""
                SELECT l.audit_id, CONVERT(varchar(23), l.occurred_at, 126) AS occurred_at,
                       l.action, a.username AS actor, l.reason
                FROM dbo.audit_logs l
                LEFT JOIN dbo.accounts a ON a.account_id = l.actor_account_id
                WHERE l.target_table = 'contracts' AND l.target_record_key = ?
                ORDER BY l.occurred_at DESC, l.audit_id DESC
                """, (rs, row) -> new HistoryEntry(rs.getLong("audit_id"), rs.getString("occurred_at"),
                        rs.getString("action"), rs.getString("actor"), rs.getString("reason")), id.toString());
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