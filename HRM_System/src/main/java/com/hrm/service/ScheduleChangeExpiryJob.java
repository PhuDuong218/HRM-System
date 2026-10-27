package com.hrm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Expires outstanding change requests after the original shift start. */
@Service
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true")
public class ScheduleChangeExpiryJob {
    private final JdbcTemplate db;

    public ScheduleChangeExpiryJob(JdbcTemplate db) {
        this.db = db;
    }

    @Scheduled(fixedDelayString = "${hrm.schedule-change.expiry-scan-ms:60000}")
    @Transactional("scheduleTransactionManager")
    public void expirePending() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var expired = db.query("""
                SELECT TOP (100) r.request_id
                FROM dbo.employee_requests r WITH(UPDLOCK,HOLDLOCK)
                JOIN dbo.schedule_change_request_details d ON d.request_id=r.request_id
                WHERE r.request_type='DOI_LICH' AND r.status='CHO_DUYET' AND d.original_start<=?
                ORDER BY d.original_start,r.request_id
                """, (rs, i) -> rs.getLong(1), now);
        for (long id : expired) {
            int updated = db.update("""
                    UPDATE dbo.employee_requests SET status='HET_HIEU_LUC',processed_at=SYSUTCDATETIME(),
                    processed_by=NULL,rejection_reason=NULL
                    WHERE request_id=? AND status='CHO_DUYET'
                    """, id);
            if (updated == 1) {
                db.update("""
                        INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason)
                        VALUES(NULL,'SCHEDULE_CHANGE_EXPIRED','employee_requests',?,?)
                        """, Long.toString(id), "Expired at original shift start");
            }
        }
    }
}
