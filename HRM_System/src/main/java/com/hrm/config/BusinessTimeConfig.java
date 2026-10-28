package com.hrm.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class BusinessTimeConfig {
    @Bean
    @Primary
    public Clock businessClock() {
        return Clock.system(ZoneId.of("Asia/Bangkok"));
    }
}
