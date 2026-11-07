package com.hrm.dto;

/** Shared module contract. Null actor is reserved for an explicit background system operation. */
public record AuditEvent(Long actorAccountId,String action,String targetTable,String targetRecordKey,
                         String reason,Object before,Object after) {}
