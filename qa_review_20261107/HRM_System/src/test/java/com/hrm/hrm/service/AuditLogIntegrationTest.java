package com.hrm.hrm.service;
import java.util.*;
import com.hrm.dto.AuditEvent;
import com.hrm.service.AuditLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties="spring.jpa.show-sql=false")
class AuditLogIntegrationTest {
    @Autowired AuditLogService service;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager transactions;
    @BeforeEach void testDatabase(){assertEquals("hrm_test",db.queryForObject("SELECT DB_NAME()",String.class));}
    @Test void refusesNonTransactionalAudit(){assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->service.record(new AuditEvent(null,"SYSTEM_TEST","employees","test",null,null,Map.of("a",1))));}
    @Test void storedSnapshotRedactsNestedSecretsAndRollbackKeepsNoRecord(){
        String key=UUID.randomUUID().toString();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{tx.setRollbackOnly();
            service.record(new AuditEvent(null,"SYSTEM_TEST","employees",key,null,Map.of(),Map.of("nested",Map.of("password","DO_NOT_STORE","jwtToken","DO_NOT_STORE_TOKEN"),"email","demo@test.invalid")));
            String stored=db.queryForObject("SELECT after_data_json FROM dbo.audit_logs WHERE target_record_key=?",String.class,key);
            assertFalse(stored.contains("DO_NOT_STORE"));assertTrue(stored.contains("[REDACTED]"));assertTrue(stored.contains("demo@test.invalid"));
        });
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.audit_logs WHERE target_record_key=?",Integer.class,key));
    }
}
