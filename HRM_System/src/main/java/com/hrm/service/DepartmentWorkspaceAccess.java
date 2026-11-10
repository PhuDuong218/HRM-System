package com.hrm.service;
import java.time.*;import java.util.*;import com.hrm.security.AuthenticatedAccount;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.web.server.ResponseStatusException;import org.springframework.http.HttpStatus;
@Service public class DepartmentWorkspaceAccess {
 private final JdbcTemplate db;public DepartmentWorkspaceAccess(JdbcTemplate db){this.db=db;}
 public LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));}
 public List<Long> departments(AuthenticatedAccount p){
  if(p==null||p.accountId()==null||p.employeeId()==null)throw denied();
  var roles=db.queryForList("SELECT r.role_code FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id JOIN dbo.employees e ON e.employee_id=a.employee_id WHERE a.account_id=? AND a.employee_id=? AND a.is_active=1 AND e.employment_status='DANG_LAM'",String.class,p.accountId(),p.employeeId());
  if(roles.size()!=1||!roles.get(0).equals(p.roleCode()))throw denied();
  var ids=db.queryForList("SELECT DISTINCT h.department_id FROM dbo.employee_assignments h WITH(HOLDLOCK) JOIN dbo.positions p ON p.position_id=h.position_id WHERE h.employee_id=? AND p.position_code='TRUONG_PHONG' AND h.effective_from<=? AND (h.effective_to IS NULL OR h.effective_to>?)",Long.class,p.employeeId(),today(),today());
  if(ids.isEmpty())throw denied();return ids;
 }
 public String predicate(AuthenticatedAccount p,String employee,String date){return "EXISTS(SELECT 1 FROM dbo.employee_assignments member WITH(HOLDLOCK) WHERE member.employee_id="+employee+" AND member.department_id IN ("+departments(p).stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+") AND member.effective_from<="+date+" AND (member.effective_to IS NULL OR member.effective_to>"+date+"))";}
 public void employee(AuthenticatedAccount p,long id,LocalDate date,boolean write){
  String scope=predicate(p,"e.employee_id","CAST(? AS date)");
  Long n=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employees e WHERE e.employee_id=? AND "+scope,Long.class,id,date,date);
  if(n==null||n!=1)throw denied();
  if(write){if(Objects.equals(id,p.employeeId()))throw denied();Long eligible=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employees e WHERE e.employee_id=? AND e.employment_status='DANG_LAM' AND e.termination_date IS NULL AND e.hire_date<=? AND EXISTS(SELECT 1 FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id WHERE a.employee_id=e.employee_id AND a.is_active=1 AND r.role_code='EMPLOYEE')",Long.class,id,date);if(eligible==null||eligible!=1)throw denied();}
 }
 public static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Ngoài phạm vi phòng ban phụ trách hoặc nhiệm kỳ trưởng phòng đã hết hiệu lực");}
}
