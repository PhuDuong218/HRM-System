package com.hrm.hrm.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hrm.service.EmployeeAuditService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeAuditServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private EmployeeAuditService auditService;

    @Test
    void recordsActorEmployeeAndValidJson() {
        Map<String, String> before = new LinkedHashMap<>();
        before.put("email", "old@example.com");
        before.put("address", null);

        Map<String, String> after = new LinkedHashMap<>();
        after.put("email", "new@example.com");
        after.put("address", "Nhà \"A\"\nĐường Nguyễn Trãi");

        auditService.recordContactUpdate(
                7L, 100L, before, after);

        ArgumentCaptor<String> beforeJson =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> afterJson =
                ArgumentCaptor.forClass(String.class);

        verify(jdbc).update(
                contains("INSERT dbo.audit_logs"),
                eq(7L),
                eq("100"),
                beforeJson.capture(),
                afterJson.capture());

        JsonMapper mapper = JsonMapper.builder().build();

        var beforeNode = mapper.readTree(beforeJson.getValue());
        var afterNode = mapper.readTree(afterJson.getValue());

        assertEquals(
                "old@example.com",
                beforeNode.get("email").asText());

        assertTrue(beforeNode.get("address").isNull());

        assertEquals(
                "new@example.com",
                afterNode.get("email").asText());

        assertEquals(
                "Nhà \"A\"\nĐường Nguyễn Trãi",
                afterNode.get("address").asText());
    }

    @Test
    void doesNotRecordWhenContactDataIsUnchanged() {
        Map<String, String> before = new LinkedHashMap<>();
        before.put("email", "same@example.com");
        before.put("address", null);

        Map<String, String> after = new LinkedHashMap<>(before);

        auditService.recordContactUpdate(
                7L, 100L, before, after);

        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingActorBeforeWritingToDatabase() {
        Map<String, String> before =
                Map.of("email", "old@example.com");

        Map<String, String> after =
                Map.of("email", "new@example.com");

        assertThrows(
                IllegalArgumentException.class,
                () -> auditService.recordContactUpdate(
                        null, 100L, before, after));

        verifyNoInteractions(jdbc);
    }
}