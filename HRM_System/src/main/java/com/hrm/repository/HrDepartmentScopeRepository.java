package com.hrm.repository;

import com.hrm.dto.HrScopeDTOs.ScopeView;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class HrDepartmentScopeRepository {
    private final JdbcTemplate jdbc;

    public HrDepartmentScopeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ScopeView> MAPPER = (r, i) -> new ScopeView(
            r.getLong("scope_id"), r.getLong("hr_account_id"), r.getLong("department_id"),
            r.getTimestamp("effective_from").toLocalDateTime(),
            nullableTime(r.getTimestamp("effective_to")), nullableTime(r.getTimestamp("revoked_at")),
            r.getObject("revoked_by", Long.class), r.getTimestamp("created_at").toLocalDateTime(),
            r.getLong("created_by"), nullableTime(r.getTimestamp("updated_at")),
            r.getObject("updated_by", Long.class));

    private static LocalDateTime nullableTime(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    public List<ScopeView> all() {
        return jdbc.query("SELECT * FROM dbo.hr_department_scopes ORDER BY scope_id", MAPPER);
    }

    public ScopeView byId(long id) {
        return jdbc.query("SELECT * FROM dbo.hr_department_scopes WHERE scope_id=?", MAPPER, id)
                .stream().findFirst().orElse(null);
    }

    public ScopeView byIdForUpdate(long id) {
        return jdbc
                .query("SELECT * FROM dbo.hr_department_scopes WITH (UPDLOCK, HOLDLOCK) WHERE scope_id=?", MAPPER, id)
                .stream().findFirst().orElse(null);
    }

    public LocalDateTime utcNow() {
        return jdbc.queryForObject("SELECT CAST(SYSUTCDATETIME() AS datetime2(3))", LocalDateTime.class);
    }

    public boolean activeScope(long hrAccountId, long departmentId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.hr_department_scopes
                WHERE hr_account_id=? AND department_id=? AND revoked_at IS NULL
                  AND effective_from <= SYSUTCDATETIME()
                  AND (effective_to IS NULL OR effective_to > SYSUTCDATETIME())
                """, Integer.class, hrAccountId, departmentId);
        return n != null && n > 0;
    }

    public boolean isActiveAdmin(long accountId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.account_id=? AND a.is_active=1 AND r.role_code='ADMIN'
                """, Integer.class, accountId);
        return n != null && n == 1;
    }

    public boolean isHrAccount(long accountId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.account_id=? AND r.role_code='HR'
                """, Integer.class, accountId);
        return n != null && n == 1;
    }

    public boolean departmentExists(long departmentId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.departments WHERE department_id=?",
                Integer.class, departmentId);
        return n != null && n == 1;
    }

    /**
     * Transaction-owned application lock. The owning service must
     * be @Transactional.
     */
    public void lockPair(long hrAccountId, long departmentId) {
        Integer result = jdbc.queryForObject("""
                SET NOCOUNT ON;
                DECLARE @result int;
                EXEC @result = sys.sp_getapplock
                  @Resource=?, @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000;
                SELECT @result;
                """, Integer.class, "hrscope:" + hrAccountId + ":" + departmentId);
        if (result == null || result < 0)
            throw new IllegalStateException("Không thể khóa phân công HR");
    }

    public boolean overlaps(long hrAccountId, long departmentId, LocalDateTime from,
            LocalDateTime to, long exceptId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.hr_department_scopes
                WHERE hr_account_id=? AND department_id=? AND scope_id<>? AND revoked_at IS NULL
                  AND (effective_to IS NULL OR effective_to > ?)
                  AND (? IS NULL OR effective_from < ?)
                """, Long.class, hrAccountId, departmentId, exceptId, from, to, to);
        return n != null && n > 0;
    }

    public long grant(long hr, long dept, LocalDateTime from, LocalDateTime to, long admin) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.hr_department_scopes
                    (hr_account_id,department_id,effective_from,effective_to,created_by)
                OUTPUT INSERTED.scope_id VALUES (?,?,?,?,?)
                """, Long.class, hr, dept, from, to, admin);
    }

    public int change(long id, LocalDateTime from, LocalDateTime to, long admin) {
        return jdbc.update("""
                UPDATE dbo.hr_department_scopes SET effective_from=?, effective_to=?,
                    updated_at=SYSUTCDATETIME(), updated_by=?
                WHERE scope_id=? AND revoked_at IS NULL
                """, from, to, admin, id);
    }

    public int revoke(long id, long admin) {
        return jdbc.update("""
                UPDATE dbo.hr_department_scopes SET revoked_at=SYSUTCDATETIME(), revoked_by=?,
                    updated_at=SYSUTCDATETIME(), updated_by=?
                WHERE scope_id=? AND revoked_at IS NULL
                """, admin, admin, id);
    }

    /**
     * JSON is generated by SQL Server; the existing audit CHECK requires valid
     * JSON.
     */
    public String asJson(long id) {
        return jdbc.query("""
                SELECT (SELECT scope_id,hr_account_id,department_id,effective_from,effective_to,
                        revoked_at,revoked_by,created_at,created_by,updated_at,updated_by
                        FROM dbo.hr_department_scopes WHERE scope_id=?
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER) AS json_value
                """, r -> r.next() ? r.getString(1) : null, id);
    }

    public void audit(long admin, String action, long scopeId, String reason, String before, String after) {
        jdbc.update("""
                INSERT INTO dbo.audit_logs
                    (actor_account_id,action,target_table,target_record_key,reason,
                     before_data_json,after_data_json)
                VALUES (?,?,'hr_department_scopes',?,?,?,?)
                """, admin, action, Long.toString(scopeId), reason, before, after);
    }
}
