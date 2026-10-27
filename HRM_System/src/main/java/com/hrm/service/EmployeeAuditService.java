package com.hrm.service;

import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service
public class EmployeeAuditService {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public EmployeeAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordProfileUpdate(Long actor, Long employeeId,
            Map<String, String> before, Map<String, String> after) {
        recordChange("EMPLOYEE_PROFILE_UPDATE", actor, employeeId, before, after);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransfer(Long actor, Long employeeId,
            Map<String, String> before, Map<String, String> after) {
        recordChange("EMPLOYEE_TRANSFER", actor, employeeId, before, after);
    }

    private void recordChange(String action, Long actor, Long employeeId,
            Map<String, String> before, Map<String, String> after) {
        recordForTarget(action, "employees", actor, employeeId, before, after);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCatalog(String table, String action, Long actor, Long id,
            Map<String, String> before, Map<String, String> after) {
        if (!("departments".equals(table) && action.matches("DEPARTMENT_(CREATE|UPDATE|DEACTIVATE)"))
                && !("positions".equals(table) && action.matches("POSITION_(CREATE|UPDATE|DEACTIVATE)"))) {
            throw new IllegalArgumentException("Đối tượng audit danh mục không hợp lệ");
        }
        recordForTarget(action, table, actor, id, before, after);
    }

    private void recordForTarget(String action, String table, Long actor, Long employeeId,
            Map<String, String> before, Map<String, String> after) {
        if (actor == null || actor <= 0 || employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException("Thiếu tài khoản thực hiện hoặc nhân viên hợp lệ");
        }
        Objects.requireNonNull(before, "Thiếu dữ liệu trước cập nhật");
        Objects.requireNonNull(after, "Thiếu dữ liệu sau cập nhật");
        if (before.equals(after)) return;
        jdbc.update("""
                INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,
                    before_data_json,after_data_json,correlation_id)
                VALUES (?,?,?,?,?,?,NEWID())
                """, actor, action, table, employeeId.toString(),
                jsonMapper.writeValueAsString(before), jsonMapper.writeValueAsString(after));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordContactUpdate(
            Long actorAccountId,
            Long employeeId,
            Map<String, String> before,
            Map<String, String> after) {

        if (actorAccountId == null || actorAccountId <= 0) {
            throw new IllegalArgumentException(
                    "Phải xác định tài khoản thực hiện");
        }

        if (employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException(
                    "Phải xác định nhân viên được cập nhật");
        }

        Objects.requireNonNull(before, "Thiếu dữ liệu trước cập nhật");
        Objects.requireNonNull(after, "Thiếu dữ liệu sau cập nhật");

        // Không ghi nhật ký thay đổi nếu dữ liệu không thay đổi.
        if (before.equals(after)) {
            return;
        }

        String beforeJson = jsonMapper.writeValueAsString(before);
        String afterJson = jsonMapper.writeValueAsString(after);

        jdbc.update("""
                INSERT dbo.audit_logs (
                    actor_account_id,
                    action,
                    target_table,
                    target_record_key,
                    before_data_json,
                    after_data_json,
                    correlation_id
                )
                VALUES (
                    ?,
                    'EMPLOYEE_CONTACT_UPDATE',
                    'employees',
                    ?,
                    ?,
                    ?,
                    NEWID()
                )
                """,
                actorAccountId,
                employeeId.toString(),
                beforeJson,
                afterJson);
    }

    @Transactional(propagation = Propagation.MANDATORY)
public void recordTermination(
        Long actorAccountId,
        Long employeeId,
        Map<String, String> before,
        Map<String, String> after) {

    if (actorAccountId == null || actorAccountId <= 0) {
        throw new IllegalArgumentException(
                "Phải xác định tài khoản thực hiện");
    }

    if (employeeId == null || employeeId <= 0) {
        throw new IllegalArgumentException(
                "Phải xác định nhân viên nghỉ việc");
    }

    Objects.requireNonNull(before, "Thiếu dữ liệu trước nghỉ việc");
    Objects.requireNonNull(after, "Thiếu dữ liệu sau nghỉ việc");

    jdbc.update("""
            INSERT dbo.audit_logs (
                actor_account_id,
                action,
                target_table,
                target_record_key,
                before_data_json,
                after_data_json,
                correlation_id
            )
            VALUES (
                ?,
                'EMPLOYEE_TERMINATE',
                'employees',
                ?,
                ?,
                ?,
                NEWID()
            )
            """,
            actorAccountId,
            employeeId.toString(),
            jsonMapper.writeValueAsString(before),
            jsonMapper.writeValueAsString(after));
}

@Transactional(propagation = Propagation.MANDATORY)
public void recordCreation(
        Long actorAccountId,
        Long employeeId,
        Map<String, String> after) {

    if (actorAccountId == null || actorAccountId <= 0) {
        throw new IllegalArgumentException(
                "Phải xác định tài khoản thực hiện");
    }

    if (employeeId == null || employeeId <= 0) {
        throw new IllegalArgumentException(
                "Phải xác định nhân viên được tạo");
    }

    Objects.requireNonNull(after, "Thiếu dữ liệu nhân viên mới");

    jdbc.update("""
            INSERT dbo.audit_logs (
                actor_account_id,
                action,
                target_table,
                target_record_key,
                before_data_json,
                after_data_json,
                correlation_id
            )
            VALUES (
                ?,
                'EMPLOYEE_CREATE',
                'employees',
                ?,
                N'{}',
                ?,
                NEWID()
            )
            """,
            actorAccountId,
            employeeId.toString(),
            jsonMapper.writeValueAsString(after));
}
}
