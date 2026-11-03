package com.hrm.service;

import com.hrm.config.TestDatabaseGuard;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import static org.junit.jupiter.api.Assertions.*;

class TestDatabaseGuardTest {
    private AnnotationConfigApplicationContext context(String database) {
        var context=new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("hrm-test");
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName="+database+";encrypt=true");
        context.register(TestDatabaseGuard.class);return context;
    }
    @Test void refusesRealDatabaseBeforeAnyDataAccess() {
        try(var context=context("hrm_db")){assertThrows(Exception.class,context::refresh);}
    }
    @Test void explicitTestDatabaseIsAccepted() {
        try(var context=context("hrm_test")){assertDoesNotThrow(context::refresh);}
    }
    @Test void duplicateDatabasePropertyCannotOverrideTheTestTarget() {
        try(var context=context("hrm_test;databaseName=hrm_db")){assertThrows(Exception.class,context::refresh);}
    }
}
