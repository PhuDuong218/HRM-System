package com.hrm.config;

import com.hrm.exception.BusinessException;
import com.hrm.service.AllowanceWorkSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class AllowanceIntegrationConfig {
    @Bean
    @ConditionalOnMissingBean(AllowanceWorkSource.class)
    public AllowanceWorkSource unconfiguredAllowanceWorkSource() {
        return (employeeId,from,to) -> {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa nối nguồn công đủ điều kiện cho phụ cấp");
        };
    }
}