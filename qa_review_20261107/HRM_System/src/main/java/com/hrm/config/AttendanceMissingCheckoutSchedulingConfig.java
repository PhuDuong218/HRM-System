package com.hrm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Explicit opt-in for the read-only missing checkout scanner.
 * No database writes or notifications are performed by the scanner.
 * Absent property means DISABLED.
 */
@Configuration
@ConditionalOnProperty(name = "hrm.attendance.missing-checkout-scan-enabled", havingValue = "true")
@EnableScheduling
public class AttendanceMissingCheckoutSchedulingConfig {
}
