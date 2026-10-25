package com.hrm.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disabled by default. Read-only recurring detection; cannot flag rows in DB or
 * notify HR without a durable store and approved RBAC routing design.
 * Logs only aggregate counts; never logs employee names or attendance IDs.
 */
@Component
@ConditionalOnProperty(name = "hrm.attendance.missing-checkout-scan-enabled", havingValue = "true")
public final class AttendanceMissingCheckoutScanJob {
    private static final Logger log = LoggerFactory.getLogger(AttendanceMissingCheckoutScanJob.class);
    private final AttendanceMissingCheckoutQueryService query;

    public AttendanceMissingCheckoutScanJob(AttendanceMissingCheckoutQueryService query) {
        this.query = query;
    }

    @Scheduled(fixedDelayString = "${hrm.attendance.missing-checkout-scan-delay-ms:300000}")
    public void scan() {
        int count = query.findExpiredCheckouts(1000).size();
        if (count > 0)
            log.info("Expired check-outs discovered in read-only scan (up to 1000): {}", count);
    }
}
