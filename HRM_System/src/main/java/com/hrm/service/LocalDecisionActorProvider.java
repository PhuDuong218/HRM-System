package com.hrm.service;

import com.hrm.dto.DecisionActor;
import com.hrm.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LocalDecisionActorProvider implements DecisionActorProvider {
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final boolean enabled;
    private final Long accountId;
    public LocalDecisionActorProvider(JdbcTemplate jdbc,Environment environment,
            @Value("${hrm.decisions.dev-actor-enabled:false}") boolean enabled,
            @Value("${hrm.decisions.dev-actor-id:0}") Long accountId) {
        this.jdbc=jdbc;this.environment=environment;this.enabled=enabled;this.accountId=accountId;
    }
    public DecisionActor requireManager() {
        if (!enabled || accountId==null || accountId<=0 ||
            !environment.acceptsProfiles(Profiles.of("local & !prod & !production"))) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa nối danh tính và quyền cho quyết định; mô phỏng local đang tắt");
        }
        // Không lấy danh tính từ body/header. Không đọc password_hash.
        var rows=jdbc.query("""
            select a.account_id,a.employee_id from dbo.accounts a
            join dbo.roles r on r.role_id=a.role_id
            join dbo.employees e on e.employee_id=a.employee_id
            where a.account_id=? and a.is_active=1 and e.employment_status='DANG_LAM'
              and r.role_code in ('ADMIN','HR')
            """,(rs,n)->new DecisionActor(rs.getLong(1),rs.getLong(2)),accountId);
        if(rows.isEmpty()) throw new BusinessException(HttpStatus.FORBIDDEN,
            "Tài khoản mô phỏng phải tồn tại, hoạt động và có vai trò ADMIN hoặc HR");
        return rows.getFirst();
    }
}