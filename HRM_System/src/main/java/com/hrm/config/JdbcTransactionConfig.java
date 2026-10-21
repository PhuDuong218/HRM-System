
package com.hrm.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;

@Configuration
public class JdbcTransactionConfig {

    @Bean("scheduleTransactionManager")
    public JdbcTransactionManager scheduleTransactionManager(
            DataSource dataSource) {

        return new JdbcTransactionManager(dataSource);
    }
}
