package com.hrm.hrm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/** Full-context check using the configured datasource, enabled explicitly by main's flag. */
@SpringBootTest
@EnabledIfSystemProperty(named = "hrm.tests.allow-external-db", matches = "true")
class ExternalDatabaseSmokeTest {

    @Test
    void contextLoads() {
    }
}
