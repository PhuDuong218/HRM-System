package com.hrm.service;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LeaveQuotaWorkspaceService {
 private final JdbcTemplate db;private final PersonnelAccessService access;private final PersonnelScopeService scope;private final PersonnelWorkflowSupport support;
 public LeaveQuotaWorkspaceService(JdbcTemplate db,PersonnelAccessService access,PersonnelScopeService scope,PersonnelWorkflowSupport support){this.db=db;this.access=access;this.scope=scope;this.support=support;}
 private String employeeScope(String column){return PersonnelScopeService.isHr()?" AND "+scope.sqlEmployees(column):"";}
 private static final String USED="""
 OUTER APPLY(SELECT SUM(a.charged_hours) usedHours FROM dbo.g2_leave_allocations a
 JOIN dbo.employee_requests r ON r.request_id=a.request_id
 WHERE a.entitlement_id=q.entitlement_id AND r.status='DA_DUYET'
 AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)) used
 """;
 private static final String FIELDS="""
 SELECT q.entitlement_id entitlementId,q.employee_id employeeId,e.employee_code employeeCode,e.full_name employeeName,
 q.leave_type_id leaveTypeId,t.type_name leaveTypeName,q.period_start periodStart,q.period_end periodEnd,
 q.entitled_hours entitledHours,COALESCE(used.usedHours,0) usedHours,q.entitled_hours-COALESCE(used.usedHours,0) remainingHours,
 CONVERT(varchar(16),q.row_version,2) version,dep.department_name departmentName,q.created_at createdAt,
 COALESCE(creator.username,N'Không có thông tin') grantedBy
 """;
 private String base(){return """
 FROM dbo.leave_entitlements q JOIN dbo.employees e ON e.employee_id=q.employee_id
 JOIN dbo.leave_types t ON t.leave_type_id=q.leave_type_id
 LEFT JOIN dbo.accounts creator ON creator.account_id=q.created_by
 OUTER APPLY(SELECT TOP(1) d.department_id,d.department_name FROM dbo.employee_assignments a
 JOIN dbo.departments d ON d.department_id=a.department_id WHERE a.employee_id=e.employee_id
 AND a.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')
 AND (a.effective_to IS NULL OR a.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time'))
 ORDER BY a.effective_from DESC,a.assignment_id DESC) dep
 """+USED;}
 @Transactional(readOnly=true)
 public Map<String,Object> search(Principal p,int year,Long department,Long employee,Long type,String keyword,int page){
  access.manager(p);if(year<2000||year>2100||page<0||page>100000)throw PersonnelWorkflowSupport.bad("Năm hoặc trang không hợp lệ");
  if(department!=null)scope.department(department);if(employee!=null)scope.employee(employee,true);
  var args=new ArrayList<Object>();args.add(LocalDate.of(year+1,1,1));args.add(LocalDate.of(year,1,1));
  String where=" WHERE q.period_start<? AND q.period_end>?"+employeeScope("q.employee_id");
  if(department!=null){where+=" AND dep.department_id=?";args.add(department);}
  if(employee!=null){where+=" AND q.employee_id=?";args.add(employee);}
  if(type!=null){where+=" AND q.leave_type_id=?";args.add(type);}
  if(keyword!=null&&!keyword.isBlank()){if(keyword.length()>200)throw PersonnelWorkflowSupport.bad("Từ khóa quá dài");where+=" AND (e.full_name COLLATE Latin1_General_100_CI_AI LIKE ? OR e.employee_code LIKE ?)";String like="%"+keyword.trim().replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%";args.add(like);args.add(like);}
  var stats=db.queryForMap("SELECT COUNT_BIG(*) totalRows,COUNT(DISTINCT q.employee_id) employees,COALESCE(SUM(q.entitled_hours),0) granted,COALESCE(SUM(used.usedHours),0) used,COALESCE(SUM(q.entitled_hours-COALESCE(used.usedHours,0)),0) remaining "+base()+where,args.toArray());
  long total=((Number)stats.get("totalRows")).longValue();args.add(page*20);args.add(20);
  return Map.of("items",db.queryForList(FIELDS+base()+where+" ORDER BY q.period_start DESC,q.entitlement_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray()),"stats",stats,"page",page,"totalPages",(total+19)/20,"totalElements",total);
 }
 public Map<String,Object> options(Principal p){access.manager(p);String filter=employeeScope("e.employee_id");return Map.of("employees",db.queryForList("SELECT e.employee_id id,CONCAT(e.employee_code,N' — ',e.full_name) label FROM dbo.employees e WHERE employment_status='DANG_LAM'"+filter+" ORDER BY e.employee_code"),"departments",db.queryForList("SELECT department_id id,department_name label FROM dbo.departments WHERE is_active=1"+(PersonnelScopeService.isHr()?" AND department_id IN ("+(scope.departments().isEmpty()?"-1":String.join(",",scope.departments().stream().map(String::valueOf).toList()))+")":"")),"types",db.queryForList("SELECT t.leave_type_id id,t.type_name label,COALESCE(p.consumes_entitlement,0) consumes FROM dbo.leave_types t LEFT JOIN dbo.g2_leave_policies p ON p.leave_type_id=t.leave_type_id WHERE t.is_active=1"));}
 private Map<String,Object> required(long id){var rows=db.queryForList(FIELDS+base()+" WHERE q.entitlement_id=?",id);if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy quỹ phép");return rows.get(0);}
 public Map<String,Object> detail(Principal p,long id){access.manager(p);var row=required(id);scope.employee(((Number)row.get("employeeId")).longValue(),true);
  return Map.of("row",row,"history",db.queryForList("SELECT a.action,a.occurred_at occurredAt,ac.username actor,a.reason,a.before_data_json beforeJson,a.after_data_json afterJson FROM dbo.audit_logs a LEFT JOIN dbo.accounts ac ON ac.account_id=a.actor_account_id WHERE a.target_table='leave_entitlements' AND a.target_record_key=? ORDER BY a.occurred_at DESC,a.audit_id DESC",Long.toString(id)),"requests",db.queryForList("SELECT r.request_id requestId,r.request_code requestCode,l.leave_start leaveStart,l.leave_end leaveEnd,r.status,a.charged_hours hours,r.processed_at processedAt,ac.username approver,CASE WHEN w.request_id IS NULL THEN 0 ELSE 1 END cancelled FROM dbo.g2_leave_allocations a JOIN dbo.employee_requests r ON r.request_id=a.request_id JOIN dbo.leave_request_details l ON l.request_id=r.request_id LEFT JOIN dbo.employee_request_withdrawals w ON w.request_id=r.request_id LEFT JOIN dbo.accounts ac ON ac.account_id=r.processed_by WHERE a.entitlement_id=? ORDER BY l.leave_start DESC",id));
 }
 public record Adjust(BigDecimal delta,String expectedVersion,String reason){}
 public static BigDecimal adjusted(BigDecimal granted,BigDecimal used,BigDecimal delta){if(delta==null||delta.signum()==0||delta.scale()>4)throw PersonnelWorkflowSupport.bad("Số giờ điều chỉnh khác 0, tối đa 4 chữ số thập phân");BigDecimal next=granted.add(delta);if(next.signum()<=0||next.compareTo(used)<0||next.compareTo(new BigDecimal("99999.9999"))>0)throw PersonnelWorkflowSupport.conflict("Quỹ sau điều chỉnh phải dương và không thấp hơn số giờ đã sử dụng");return next;}
 @Transactional public Map<String,Object> adjust(Principal p,long id,Adjust input){var actor=access.manager(p);if(input.reason()==null||input.reason().isBlank()||input.reason().length()>1000)throw PersonnelWorkflowSupport.bad("Bắt buộc lý do tối đa 1000 ký tự");support.lock();
  Long employee=((Number)required(id).get("employeeId")).longValue();scope.managedEmployee(employee);
  if(actor.role().equals("HR")&&actor.employeeId()==employee)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"HR không tự điều chỉnh quỹ phép của mình");
  db.queryForList("SELECT employee_id FROM dbo.employees WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?",employee);
  db.queryForList("SELECT entitlement_id FROM dbo.leave_entitlements WITH(UPDLOCK,HOLDLOCK) WHERE entitlement_id=?",id);
  var before=required(id);if(input.expectedVersion()==null||!input.expectedVersion().equalsIgnoreCase((String)before.get("version")))throw PersonnelWorkflowSupport.conflict("Quỹ phép đã thay đổi. Hãy tải lại chi tiết");
  if(!support.today().isBefore(((java.sql.Date)before.get("periodEnd")).toLocalDate()))throw PersonnelWorkflowSupport.conflict("Kỳ phép đã kết thúc");
  var next=adjusted((BigDecimal)before.get("entitledHours"),(BigDecimal)before.get("usedHours"),input.delta());
  db.update("UPDATE dbo.leave_entitlements SET entitled_hours=?,updated_by=?,updated_at=SYSUTCDATETIME() WHERE entitlement_id=?",next,actor.accountId(),id);
  var after=required(id);support.audit(actor.accountId(),"LEAVE_QUOTA_ADJUST","leave_entitlements",id,input.reason().trim(),before,after);return after;
 }
}
