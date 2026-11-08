package com.hrm.service;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Leaves three explicitly labelled QA examples in hrm_test; never changes existing definitions. */
@SpringBootTest(properties={"spring.jpa.show-sql=false","hrm.contracts.expiry-job-enabled=false","personnel.resignations.scheduler-enabled=false","hrm.attendance.missing-checkout-scan-enabled=false"})
class LeaveExampleSeedSqlServerTest {
 @Autowired JdbcTemplate db;
 @Test void demoTypesAreTestOnlyAndSeedCanBeRepeated()throws Exception{
  assertEquals("hrm_test",db.queryForObject("SELECT DB_NAME()",String.class));
  for(String path:new String[]{"database/migrations/006_leave_request_rules.sql","database/migrations/009_leave_eligibility_and_accrual.sql","database/seed_personnel_leave_examples_test_only.sql"})db.execute(Files.readString(Path.of(path)));
  var before=db.queryForList("SELECT leave_type_id,type_code,type_name,is_paid,is_active FROM dbo.leave_types WHERE type_code IN ('QA_ANNUAL','QA_UNPAID','QA_EVIDENCE') ORDER BY type_code");assertEquals(3,before.size());
  db.execute(Files.readString(Path.of("database/seed_personnel_leave_examples_test_only.sql")));
  assertEquals(before,db.queryForList("SELECT leave_type_id,type_code,type_name,is_paid,is_active FROM dbo.leave_types WHERE type_code IN ('QA_ANNUAL','QA_UNPAID','QA_EVIDENCE') ORDER BY type_code"));
 }
}
