package com.hrm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "target_table", nullable = false, length = 128)
    private String targetTable;

    @Column(name = "target_record_key", nullable = false, length = 100)
    private String targetRecordKey;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "correlation_id")
    private UUID correlationId;

    protected AuditLog() {
    }

    public AuditLog(Long actorAccountId, String action, String targetTable, Long targetRecordId, String reason) {
        this.actorAccountId = actorAccountId;
        this.action = action;
        this.targetTable = targetTable;
        this.targetRecordKey = String.valueOf(targetRecordId);
        this.reason = reason;
        this.correlationId = UUID.randomUUID();
    }

    public static AuditLog forReportExport(
            Long actorAccountId, String reportCode, String targetTable, String effectiveFilters) {
        AuditLog log = new AuditLog();
        log.actorAccountId = actorAccountId;
        // Records successful generation; HTTP delivery to the client cannot be guaranteed.
        log.action = "REPORT_EXPORT_GENERATED";
        log.targetTable = targetTable;
        log.targetRecordKey = reportCode;
        log.reason = "format=XLSX; " + effectiveFilters;
        log.correlationId = UUID.randomUUID();
        return log;
    }
}
