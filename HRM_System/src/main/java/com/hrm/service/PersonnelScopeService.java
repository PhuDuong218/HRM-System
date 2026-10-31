package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Current, server-side HR scope for personnel management. Never accepts actor IDs from the client. */
@Service
public class PersonnelScopeService {
 private final JdbcTemplate jdbc;
 public PersonnelScopeService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public static AuthenticatedAccount current(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.isAuthenticated()&&a.getPrincipal() instanceof AuthenticatedAccount p?p:null;}
 public static boolean isHr(){var a=current();return a!=null&&"HR".equals(a.roleCode());}
 public List<Long> departments(){var a=current();if(a==null||!"HR".equals(a.roleCode()))return List.of(-1L);
 var ids=jdbc.queryForList("SELECT DISTINCT department_id FROM dbo.hr_department_scopes WHERE hr_account_id=? AND revoked_at IS NULL AND effective_from<=SYSUTCDATETIME() AND (effective_to IS NULL OR effective_to>SYSUTCDATETIME())",Long.class,a.accountId());return ids.isEmpty()?List.of(-1L):ids;}
 public List<Long> employees(){var a=current();if(a==null||!"HR".equals(a.roleCode()))return List.of(-1L);
 var ids=jdbc.queryForList("""
 SELECT DISTINCT e.employee_id FROM dbo.employees e JOIN dbo.employee_assignments a ON a.employee_id=e.employee_id
 JOIN dbo.hr_department_scopes s ON s.department_id=a.department_id
 WHERE s.hr_account_id=? AND s.revoked_at IS NULL AND s.effective_from<=SYSUTCDATETIME()
 AND (s.effective_to IS NULL OR s.effective_to>SYSUTCDATETIME())
 AND a.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')
 AND ((e.employment_status='DANG_LAM' AND (a.effective_to IS NULL OR a.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')))
 OR (e.employment_status='DA_NGHI' AND NOT EXISTS(SELECT 1 FROM dbo.employee_assignments newer WHERE newer.employee_id=e.employee_id AND newer.effective_from>a.effective_from AND newer.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time'))))
 """,Long.class,a.accountId());return ids.isEmpty()?List.of(-1L):ids;}
 public void employee(long id,boolean read){var a=current();if(a==null)throw denied();if("ADMIN".equals(a.roleCode()))return;if(read&&Objects.equals(a.employeeId(),id))return;if("HR".equals(a.roleCode())&&employees().contains(id))return;throw denied();}
 public void department(long id){if(isHr()&&!departments().contains(id))throw denied();}
 public long employeeOf(String table,String idColumn,long id){if(!Set.of("employee_qualifications","leave_entitlements","employee_requests").contains(table))throw new IllegalArgumentException();var rows=jdbc.queryForList("SELECT employee_id FROM dbo."+table+" WHERE "+idColumn+"=?",Long.class,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bản ghi");return rows.get(0);}
 public String sqlEmployees(String column){return column+" IN ("+employees().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+")";}
 private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Đối tượng nằm ngoài phạm vi phòng ban được giao");}
}
