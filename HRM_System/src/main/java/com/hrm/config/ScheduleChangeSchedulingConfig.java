package com.hrm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true")
public class ScheduleChangeSchedulingConfig {
}
