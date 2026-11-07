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
 /** Read access is separate from mutation rights and is re-evaluated against effective assignments. */
 public boolean canReadPrivate(long employeeId){var a=current();return a!=null&&("ADMIN".equals(a.roleCode())||Objects.equals(a.employeeId(),employeeId)||("HR".equals(a.roleCode())&&employees().contains(employeeId))||headEmployees().contains(employeeId));}
 public List<Long> headEmployees(){var actor=current();if(actor==null)return List.of();
  return jdbc.queryForList("""
   SELECT DISTINCT member.employee_id FROM dbo.employee_assignments head
   JOIN dbo.positions p ON p.position_id=head.position_id AND p.position_code='TRUONG_PHONG'
   JOIN dbo.employees leader ON leader.employee_id=head.employee_id AND leader.employment_status='DANG_LAM'
   JOIN dbo.employee_assignments member ON member.department_id=head.department_id
   JOIN dbo.employees e ON e.employee_id=member.employee_id AND e.employment_status='DANG_LAM'
   WHERE head.employee_id=?
    AND head.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')
    AND (head.effective_to IS NULL OR head.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time'))
    AND member.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')
    AND (member.effective_to IS NULL OR member.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time'))
   """,Long.class,actor.employeeId());
 }
 public boolean isCurrentHead(){return !headEmployees().isEmpty();}
 public List<Long> readableEmployees(){var actor=current();if(actor==null||"ADMIN".equals(actor.roleCode()))return null;
  var ids=new java.util.LinkedHashSet<Long>();if("HR".equals(actor.roleCode()))ids.addAll(employees());ids.addAll(headEmployees());return ids.isEmpty()?List.of(-1L):List.copyOf(ids);}
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
 public void employee(long id,boolean read){var a=current();if(a==null)throw denied();if("ADMIN".equals(a.roleCode()))return;if(read&&(Objects.equals(a.employeeId(),id)||headEmployees().contains(id)))return;if("HR".equals(a.roleCode())&&employees().contains(id))return;throw denied();}
 public void managedEmployee(long id){var a=current();if(a==null)throw denied();if("ADMIN".equals(a.roleCode()))return;if("HR".equals(a.roleCode())&&employees().contains(id))return;throw denied();}
 public void department(long id){if(isHr()&&!departments().contains(id))throw denied();}
 public long employeeOf(String table,String idColumn,long id){
  if(!Map.of("employee_qualifications","employee_qualification_id","leave_entitlements","entitlement_id",
      "employee_requests","request_id","contracts","contract_id","employee_decisions","decision_id").getOrDefault(table, "").equals(idColumn))
   throw new IllegalArgumentException("Bảng hoặc khóa không hợp lệ");
  var rows=jdbc.queryForList("SELECT employee_id FROM dbo."+table+" WHERE "+idColumn+"=?",Long.class,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bản ghi");return rows.get(0);}
 public String sqlEmployees(String column){return column+" IN ("+employees().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+")";}
 private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Đối tượng nằm ngoài phạm vi phòng ban được giao");}
}
