package com.hrm.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Fail before repositories/jobs are created if the explicit test profile targets real data. */
@Configuration
@Profile("hrm-test")
public class TestDatabaseGuard {
    @Bean
    static BeanFactoryPostProcessor requireTestDatabase(Environment environment) {
        return factory -> {
            String url=environment.getProperty("spring.datasource.url","");
            var target=java.util.regex.Pattern.compile("(?i);(?:databaseName|database)=([^;]*)").matcher(url);
            int targets=0;boolean correct=false;
            while(target.find()){targets++;correct="hrm_test".equalsIgnoreCase(target.group(1));}
            if(!url.startsWith("jdbc:sqlserver:")||targets!=1||!correct)
                throw new IllegalStateException("Profile hrm-test chỉ được kết nối databaseName=hrm_test");
        };
    }
}
