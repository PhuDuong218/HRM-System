
package com.hrm.config;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration
public class JdbcTransactionConfig {

    @Bean("transactionManager")
    @Primary
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean("scheduleTransactionManager")
    public JdbcTransactionManager scheduleTransactionManager(
            DataSource dataSource) {

        return new JdbcTransactionManager(dataSource);
    }
}
