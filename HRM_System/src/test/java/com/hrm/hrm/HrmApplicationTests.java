package com.hrm.hrm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
// Existing full-context smoke test loads application-local.properties and can reach SQL Server.
// Keep it opt-in; the D15 suite never sets this property.
@EnabledIfSystemProperty(named = "hrm.tests.allow-external-db", matches = "true")
class HrmApplicationTests {

	@Test
	void contextLoads() {
	}

}
