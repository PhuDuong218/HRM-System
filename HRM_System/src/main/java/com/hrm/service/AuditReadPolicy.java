package com.hrm.service;

import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Apply the same object scope as personnel APIs; audit is not a bypass. */
@Service
public class AuditReadPolicy {
    private final PersonnelScopeService scope;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final Set<String> EMPLOYEE_TABLES = Set.of(
            "employee_qualifications", "leave_entitlements", "employee_requests");

    public AuditReadPolicy(PersonnelScopeService scope) { this.scope = scope; }

    public void require(String table, long id) {
        if ("employees".equals(table)) scope.employee(id, true);
        else if ("departments".equals(table)) scope.department(id);
        else if (EMPLOYEE_TABLES.contains(table)) scope.employee(employeeId(table, id), true);
    }

    private long employeeId(String table, long id) {
        String column = switch (table) {
            case "employee_qualifications" -> "employee_qualification_id";
            case "leave_entitlements" -> "entitlement_id";
            case "employee_requests" -> "request_id";
            default -> throw new IllegalArgumentException("Unsupported audit target");
        };
        return scope.employeeOf(table, column, id);
    }

    public String snapshot(String table, String key, String value) {
        if (value == null || value.isBlank()) return value;
        boolean own = false;
        var actor = PersonnelScopeService.current();
        if (actor != null && "employees".equals(table)) {
            own = Objects.equals(String.valueOf(actor.employeeId()), key);
        }
        if (own) return value;
        try { return json.writeValueAsString(redact(json.readValue(value, Object.class))); }
        catch (RuntimeException invalidSnapshot) {
            // A malformed historic snapshot must not disclose private text via fallback.
            return "{\"notice\":\"Không đọc được dữ liệu lịch sử\"}";
        }
    }

    private Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> clean = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                String normalized = name.replace("_", "").toLowerCase(Locale.ROOT);
                clean.put(name, Set.of("identitynumber", "address", "homeaddress", "cccd").contains(normalized) ? "[REDACTED]" : redact(item));
            });
            return clean;
        }
        if (value instanceof List<?> list) return list.stream().map(this::redact).toList();
        return value;
    }

    public Map<String, Object> row(String table, String key, Map<String, Object> source) {
        var result = new LinkedHashMap<>(source);
        for (String field : List.of("beforeDataJson", "afterDataJson")) {
            Object value = result.get(field);
            if (value != null) result.put(field, snapshot(table, key, value.toString()));
        }
        return result;
    }
}
