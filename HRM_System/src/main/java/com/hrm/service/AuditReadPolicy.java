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

    /** Predicate for audit_logs alias a. IDs come only from the server-side scope. */
    public String hrFeedPredicate() {
        String departments = ids(scope.departments());
        String key = "TRY_CONVERT(bigint,a.target_record_key)";
        String eventDate = "CONVERT(date, a.occurred_at AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time')";
        String assignment = " FROM dbo.employee_assignments h WHERE h.department_id IN ("+departments
            +") AND h.effective_from<="+eventDate+" AND (h.effective_to IS NULL OR h.effective_to>"+eventDate+")";
        var conditions = new ArrayList<String>();
        conditions.add("(a.target_table='employees' AND EXISTS (SELECT 1"+assignment+" AND h.employee_id="+key+"))");
        conditions.add("(a.target_table='employees' AND a.action='EMPLOYEE_TRANSFER' AND ("
            + auditDepartment("before", departments)+" OR "+auditDepartment("after", departments)+"))");
        conditions.add("(a.target_table='departments' AND "+key+" IN ("+departments+"))");
        Map<String,String> ownerTables = Map.of(
            "employee_qualifications","employee_qualification_id",
            "leave_entitlements","entitlement_id",
            "employee_requests","request_id",
            "contracts","contract_id",
            "employee_decisions","decision_id",
            "work_schedules","schedule_id",
            "overtime_requests","overtime_id");
        ownerTables.forEach((table,id) -> conditions.add("(a.target_table='"+table+"' AND EXISTS (SELECT 1 FROM dbo."
            +table+" t WHERE t."+id+"="+key+" AND EXISTS (SELECT 1"+assignment+" AND h.employee_id=t.employee_id)))"));
        return "("+String.join(" OR ",conditions)+")";
    }

    private static String auditDepartment(String snapshot, String departments) {
        String column = "a."+snapshot+"_data_json";
        return "TRY_CONVERT(bigint,JSON_VALUE(CASE WHEN ISJSON("+column+")=1 THEN "+column
            +" ELSE '{}' END,'$.departmentId')) IN ("+departments+")";
    }

    private static String ids(List<Long> values) {
        return values.isEmpty()?"-1":String.join(",",values.stream().map(String::valueOf).toList());
    }

    public void require(String table, long id) {
        if (PersonnelScopeService.isHr()) {
            if ("employees".equals(table)) scope.managedEmployee(id);
            else if ("departments".equals(table)) scope.department(id);
            else if (EMPLOYEE_TABLES.contains(table)) scope.managedEmployee(employeeId(table,id));
            else throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,"Nhật ký danh mục chung chỉ dành cho ADMIN");
            return;
        }
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
        boolean allowPrivate = own || (actor != null && "ADMIN".equals(actor.roleCode()))
            || ("employees".equals(table) && key != null && key.matches("[0-9]{1,18}")
                && scope.canReadPrivate(Long.parseLong(key)));
        try { return json.writeValueAsString(redact(json.readValue(value, Object.class), allowPrivate)); }
        catch (RuntimeException invalidSnapshot) {
            // A malformed historic snapshot must not disclose private text via fallback.
            return "{\"notice\":\"Không đọc được dữ liệu lịch sử\"}";
        }
    }

    private Object redact(Object value, boolean allowPrivate) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> clean = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                String normalized = name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                boolean secret = normalized.contains("password") || normalized.contains("secret")
                    || normalized.contains("token") || Set.of("authorization", "cookie").contains(normalized);
                boolean privateField = Set.of("identitynumber", "address", "homeaddress", "cccd").contains(normalized);
                clean.put(name, secret || (privateField && !allowPrivate) ? "[REDACTED]" : redact(item, allowPrivate));
            });
            return clean;
        }
        if (value instanceof List<?> list) return list.stream().map(item -> redact(item, allowPrivate)).toList();
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
