package com.hrm.service;

import java.security.Principal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Identity comes exclusively from the authenticated Principal installed by the login module. */
@Service
public class PersonnelAccessService {
    public record Actor(long accountId,long employeeId,String role) {}
    private final JdbcTemplate jdbc;
    private final DepartmentHeadAccessService managers;
    public PersonnelAccessService(JdbcTemplate jdbc,DepartmentHeadAccessService managers) { this.jdbc=jdbc;this.managers=managers; }
    public Actor actor(Principal principal) {
        if(principal==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Cần đăng nhập");
        var actors=jdbc.query("""
            SELECT a.account_id,a.employee_id,r.role_code FROM dbo.accounts a
            JOIN dbo.roles r ON r.role_id=a.role_id JOIN dbo.employees e ON e.employee_id=a.employee_id
            WHERE a.username=? AND a.is_active=1 AND e.employment_status='DANG_LAM'
            """,(r,n)->new Actor(r.getLong(1),r.getLong(2),r.getString(3)),principal.getName());
        if(actors.size()!=1) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tài khoản không còn quyền truy cập");
        return actors.get(0);
    }
    public Actor manager(Principal principal) { managers.requireManager(principal);return actor(principal); }
    public Actor approver(Principal principal) {
        Actor a=manager(principal);
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM dbo.accounts a JOIN dbo.role_permissions rp ON rp.role_id=a.role_id
            JOIN dbo.permissions p ON p.permission_id=rp.permission_id
            WHERE a.account_id=? AND p.permission_code='REQUEST_APPROVE'
            """,Integer.class,a.accountId());
        if(count==null||count==0) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Thiếu quyền REQUEST_APPROVE");
        return a;
    }
    public Actor admin(Principal principal) {
        Actor a=manager(principal);
        if(!"ADMIN".equals(a.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ ADMIN được cấu hình chính sách");
        return a;
    }
    public Actor ownerOrManager(Principal principal,long employeeId) {
        Actor a=actor(principal); if(a.employeeId()!=employeeId) manager(principal);return a;
    }
}
