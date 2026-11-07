package com.hrm.hrm.service;
import java.time.*;
import java.util.*;
import com.hrm.dto.AuditEvent;
import com.hrm.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class AuditLogServiceTest {
    @Test void recursivelyRedactsCredentialsAndPreservesOrdinarySnapshots(){
        var writer=new AuditLogService(mock(JdbcTemplate.class),Clock.systemUTC());
        String result=writer.safeJson(Map.of("email","e@test.invalid","nested",List.of(Map.of("passwordHash","PRIVATE_HASH","access_token","PRIVATE_TOKEN","name","Test"))));
        assertFalse(result.contains("PRIVATE_HASH"));assertFalse(result.contains("PRIVATE_TOKEN"));assertTrue(result.contains("[REDACTED]"));assertTrue(result.contains("e@test.invalid"));
    }
    @Test void recordsUtcActorTargetSnapshotsAndCorrelation(){
        var db=mock(JdbcTemplate.class);var clock=Clock.fixed(Instant.parse("2026-10-27T02:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
        var writer=new AuditLogService(db,clock);var before=ArgumentCaptor.forClass(String.class);var after=ArgumentCaptor.forClass(String.class);
        writer.record(new AuditEvent(7L,"TEST_UPDATE","employees","100",null,Map.of("name","Nhà \"A\"\n"),Map.of("name","New")));
        verify(db).update(contains("INSERT dbo.audit_logs"),eq(7L),eq(LocalDateTime.of(2026,10,27,2,0)),eq("TEST_UPDATE"),eq("employees"),eq("100"),isNull(),before.capture(),after.capture(),any(UUID.class));
        assertTrue(before.getValue().contains("\\\"A\\\""));assertTrue(after.getValue().contains("New"));
    }
    @Test void rejectsInvalidActionBeforeDatabaseWrite(){var db=mock(JdbcTemplate.class);var writer=new AuditLogService(db,Clock.systemUTC());assertThrows(IllegalArgumentException.class,()->writer.record(new AuditEvent(7L,"bad action","employees","100",null,null,null)));verifyNoInteractions(db);}
}
