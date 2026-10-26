package com.hrm.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** @Past/@Future use the same business clock as date-sensitive services. */
@Configuration
public class BusinessValidationConfiguration {
    @Bean
    public LocalValidatorFactoryBean validator(@Qualifier("hrmBusinessClock") Clock clock) {
        var validator=new LocalValidatorFactoryBean();
        validator.setConfigurationInitializer(configuration -> configuration.clockProvider(() -> clock));
        return validator;
    }
}
