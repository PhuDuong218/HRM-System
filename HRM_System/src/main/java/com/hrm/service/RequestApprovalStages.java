package com.hrm.service;

import java.security.Principal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Derived head authority is restricted to requests, never granted as global HR authority. */
@Service
public class RequestApprovalStages {
 private final JdbcTemplate db;
 private final PersonnelAccessService access;
 private final PersonnelWorkflowSupport support;
 public RequestApprovalStages(JdbcTemplate db,PersonnelAccessService access,PersonnelWorkflowSupport support){this.db=db;this.access=access;this.support=support;}
 public boolean enabled(){return db.queryForObject("SELECT CASE WHEN OBJECT_ID('dbo.personnel_request_approval_stages') IS NULL THEN 0 ELSE 1 END",Integer.class)==1;}
 public List<Long> departments(long employee){return db.queryForList("""
  SELECT a.department_id FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id
  JOIN dbo.employees e ON e.employee_id=a.employee_id
  WHERE a.employee_id=? AND e.employment_status='DANG_LAM' AND p.position_code='TRUONG_PHONG'
    AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?)
  """,Long.class,employee,support.today(),support.today());}
 public Map<String,Object> capabilities(Principal p){var actor=access.actor(p);return Map.of("twoStageEnabled",enabled(),"departmentHead",enabled()&&!departments(actor.employeeId()).isEmpty());}
 private long department(long employee){var rows=db.queryForList("SELECT department_id FROM dbo.employee_assignments WHERE employee_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>?)",Long.class,employee,support.today(),support.today());if(rows.size()!=1)throw conflict("Nhân viên phải có đúng một phân công hiện tại");return rows.get(0);}
 public void register(long request,long employee){
  if(!enabled())return;
  var current=db.queryForList("SELECT department_id FROM dbo.employee_assignments WHERE employee_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>?)",Long.class,employee,support.today(),support.today());
  if(current.isEmpty()){db.update("INSERT dbo.personnel_request_approval_stages(request_id,department_id,stage_status,note) VALUES(?,NULL,'SKIPPED',?)",request,"Chuyển HR/ADMIN đối soát nhân viên chưa có phân công hiện tại");return;}
  long department=department(employee);
  boolean skip=departments(employee).contains(department);
  Long heads=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id WHERE a.department_id=? AND p.position_code='TRUONG_PHONG' AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?)",Long.class,department,support.today(),support.today());
  skip=skip||heads==0;
  db.update("INSERT dbo.personnel_request_approval_stages(request_id,department_id,stage_status,note) VALUES(?,?,?,?)",request,department,skip?"SKIPPED":"PENDING",skip?"Chuyển thẳng HR/ADMIN: người nộp là trưởng phòng hoặc phòng chưa có trưởng phòng":null);
 }
 public Map<String,Object> stage(long request){if(!enabled())return Map.of("stageStatus","LEGACY");var rows=db.queryForList("SELECT department_id AS departmentId,stage_status AS stageStatus,decided_by AS decidedBy,decided_at AS decidedAt,note FROM dbo.personnel_request_approval_stages WHERE request_id=?",request);return rows.isEmpty()?Map.of("stageStatus","LEGACY"):rows.get(0);}
 public void requireFinalApproval(long request,long employee){
  requireFinalApproval(request,employee,null);
 }
 /** Read-only counterpart: a preview must never mark a stage as skipped. */
 public String finalApprovalBlocker(long request,long employee){
  var stage=stage(request);String status=String.valueOf(stage.get("stageStatus"));
  if("LEGACY".equals(status)||"SKIPPED".equals(status))return null;
  if("PENDING".equals(status)){
   long current=department(employee);
   if(((Number)stage.get("departmentId")).longValue()!=current)return "Nhân viên đã đổi phòng; cần rút và nộp lại đơn để trưởng phòng mới xử lý";
   long heads=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id JOIN dbo.employees e ON e.employee_id=a.employee_id WHERE a.department_id=? AND p.position_code='TRUONG_PHONG' AND e.employment_status='DANG_LAM' AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?)",Long.class,current,support.today(),support.today());
   if(heads==0)return null;
  }
  if(!"APPROVED".equals(status))return "Đơn cần trưởng phòng duyệt cấp 1 trước khi HR/ADMIN duyệt cuối";
  if(((Number)stage.get("departmentId")).longValue()!=department(employee))return "Nhân viên đã đổi phòng; cần rút và nộp lại đơn để trưởng phòng mới xử lý";
  return null;
 }
 public void requireFinalApproval(long request,long employee,Long actor){
  var stage=stage(request);String status=String.valueOf(stage.get("stageStatus"));
  if("LEGACY".equals(status)||"SKIPPED".equals(status))return;
  if("PENDING".equals(status)&&actor!=null){
   long current=department(employee);
   if(((Number)stage.get("departmentId")).longValue()!=current)throw conflict("Nhân viên đã đổi phòng; người nộp cần rút và nộp lại đơn để trưởng phòng mới xử lý");
   long heads=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id JOIN dbo.employees e ON e.employee_id=a.employee_id WHERE a.department_id=? AND p.position_code='TRUONG_PHONG' AND e.employment_status='DANG_LAM' AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?)",Long.class,current,support.today(),support.today());
   if(heads==0){db.update("UPDATE dbo.personnel_request_approval_stages SET stage_status='SKIPPED',decided_by=?,decided_at=SYSUTCDATETIME(),note=? WHERE request_id=?",actor,"Không còn trưởng phòng hiện tại; chuyển HR/ADMIN duyệt cuối",request);support.audit(actor,"REQUEST_HEAD_SKIP","employee_requests",request,"Nhiệm kỳ trưởng phòng không còn hiệu lực",stage,stage(request));return;}
  }
  if(!"APPROVED".equals(status))throw conflict("Đơn cần trưởng phòng duyệt cấp 1 trước khi HR/ADMIN duyệt cuối");
  if(((Number)stage.get("departmentId")).longValue()!=department(employee))throw conflict("Nhân viên đã đổi phòng; người nộp cần rút và nộp lại đơn để trưởng phòng mới xử lý");
 }
 public boolean canRead(long employee,Principal p){var actor=access.actor(p);if(!enabled()||actor.employeeId()==employee)return false;var heads=departments(actor.employeeId());if(heads.isEmpty())return false;var assignments=db.queryForList("SELECT department_id FROM dbo.employee_assignments WHERE employee_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>?)",Long.class,employee,support.today(),support.today());return assignments.size()==1&&heads.contains(assignments.get(0));}
 @Transactional
 public Map<String,Object> decide(long request,boolean approve,String note,Principal p){
  if(!enabled())throw conflict("Chưa áp dụng migration duyệt hai cấp");
  var actor=access.actor(p);support.lock();
  var requests=db.queryForList("SELECT employee_id,status FROM dbo.employee_requests WITH(UPDLOCK,HOLDLOCK) WHERE request_id=?",request);
  if(requests.isEmpty())throw new com.hrm.exception.ResourceNotFoundException("Không tìm thấy đơn");
  var row=requests.get(0);long employee=((Number)row.get("employee_id")).longValue();
  if(employee==actor.employeeId()||!canRead(employee,p))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ trưởng phòng hiện tại được xử lý cấp 1; không được tự duyệt");
  if(!"CHO_DUYET".equals(row.get("status")))throw conflict("Đơn đã được xử lý");
  long withdrawn=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_request_withdrawals WHERE request_id=?",Long.class,request);
  if(withdrawn>0)throw conflict("Đơn đã được rút");
  if(!approve&&(note==null||note.isBlank()))throw PersonnelWorkflowSupport.bad("Phải nhập lý do từ chối");
  var beforeStage=stage(request);
  if(note!=null&&note.length()>1000)throw PersonnelWorkflowSupport.bad("Ghi chú tối đa 1000 ký tự");
  if(!"PENDING".equals(beforeStage.get("stageStatus")))throw conflict("Cấp 1 đã được xử lý hoặc đơn chuyển thẳng HR/ADMIN");
  db.update("UPDATE dbo.personnel_request_approval_stages SET stage_status=?,department_id=?,decided_by=?,decided_at=SYSUTCDATETIME(),note=? WHERE request_id=? AND stage_status='PENDING'",approve?"APPROVED":"REJECTED",department(employee),actor.accountId(),note,request);
  if(!approve)db.update("UPDATE dbo.employee_requests SET status='TU_CHOI',processed_by=?,processed_at=SYSUTCDATETIME(),rejection_reason=?,updated_by=?,updated_at=SYSUTCDATETIME() WHERE request_id=?",actor.accountId(),note,actor.accountId(),request);
  support.audit(actor.accountId(),approve?"REQUEST_HEAD_APPROVE":"REQUEST_HEAD_REJECT","employee_requests",request,note,beforeStage,stage(request));return stage(request);
 }
 public org.springframework.data.domain.Page<Map<String,Object>> queue(Principal p,int page,int size){
  var actor=access.actor(p);var departments=departments(actor.employeeId());
  if(!enabled()||departments.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có nhiệm kỳ trưởng phòng đang hiệu lực");
  var paging=com.hrm.util.PagingUtils.create(page,size,org.springframework.data.domain.Sort.unsorted());
  String ids=departments.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
  String from=" FROM dbo.employee_requests r JOIN dbo.personnel_request_approval_stages s ON s.request_id=r.request_id JOIN dbo.employees e ON e.employee_id=r.employee_id WHERE r.employee_id<>? AND r.status='CHO_DUYET' AND s.stage_status='PENDING' AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id) AND EXISTS(SELECT 1 FROM dbo.employee_assignments a WHERE a.employee_id=r.employee_id AND a.department_id IN ("+ids+") AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?))";
  Long total=db.queryForObject("SELECT COUNT_BIG(*)"+from,Long.class,actor.employeeId(),support.today(),support.today());
  var rows=db.queryForList("SELECT r.request_id AS requestId,r.request_code AS requestCode,r.employee_id AS employeeId,e.employee_code AS employeeCode,e.full_name AS employeeName,r.request_type AS requestType,r.status,r.submitted_at AS submittedAt,r.reason,s.stage_status AS stageStatus"+from+" ORDER BY r.submitted_at DESC,r.request_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",actor.employeeId(),support.today(),support.today(),paging.getOffset(),paging.getPageSize());
  return new org.springframework.data.domain.PageImpl<>(rows,paging,total);
 }

 @Transactional(readOnly=true)
 public org.springframework.data.domain.Page<Map<String,Object>> search(Principal p,String stageStatus,String status,String type,String keyword,java.time.LocalDate fromDate,java.time.LocalDate toDate,int page,int size){
  var actor=access.actor(p);var heads=departments(actor.employeeId());
  if(!enabled()||heads.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có nhiệm kỳ trưởng phòng đang hiệu lực");
  if(stageStatus!=null&&!Set.of("PENDING","APPROVED","REJECTED").contains(stageStatus))throw PersonnelWorkflowSupport.bad("Trạng thái cấp 1 không hợp lệ");
  if(status!=null&&!Set.of("CHO_DUYET","DA_DUYET","TU_CHOI","DA_HUY").contains(status))throw PersonnelWorkflowSupport.bad("Trạng thái đơn không hợp lệ");
  if(type!=null&&!Set.of("NGHI_PHEP","NGHI_VIEC").contains(type))throw PersonnelWorkflowSupport.bad("Loại đơn không hợp lệ");
  if(fromDate!=null&&toDate!=null&&fromDate.isAfter(toDate))throw PersonnelWorkflowSupport.bad("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
  if(keyword!=null&&keyword.length()>200)throw PersonnelWorkflowSupport.bad("Từ khóa tối đa 200 ký tự");
  var paging=com.hrm.util.PagingUtils.create(page,size,org.springframework.data.domain.Sort.unsorted());
  String ids=heads.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
  String finalStatus="CASE WHEN w.request_id IS NOT NULL THEN 'DA_HUY' ELSE r.status END";
  String from=" FROM dbo.employee_requests r JOIN dbo.personnel_request_approval_stages s ON s.request_id=r.request_id JOIN dbo.employees e ON e.employee_id=r.employee_id LEFT JOIN dbo.accounts decider ON decider.account_id=s.decided_by LEFT JOIN dbo.employee_request_withdrawals w ON w.request_id=r.request_id WHERE r.employee_id<>? AND s.stage_status IN ('PENDING','APPROVED','REJECTED') AND EXISTS(SELECT 1 FROM dbo.employee_assignments a WHERE a.employee_id=r.employee_id AND a.department_id IN ("+ids+") AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?))";
  var args=new ArrayList<Object>();args.add(actor.employeeId());args.add(support.today());args.add(support.today());
  if(stageStatus!=null){from+=" AND s.stage_status=?";args.add(stageStatus);if("PENDING".equals(stageStatus))from+=" AND r.status='CHO_DUYET' AND w.request_id IS NULL";}
  if(status!=null){from+=" AND ("+finalStatus+")=?";args.add(status);}
  if(type!=null){from+=" AND r.request_type=?";args.add(type);}
  if(fromDate!=null){from+=" AND r.submitted_at>=?";args.add(fromDate.atStartOfDay().minusHours(7));}
  if(toDate!=null){from+=" AND r.submitted_at<?";args.add(toDate.plusDays(1).atStartOfDay().minusHours(7));}
  for(String token:com.hrm.util.PersonnelSearch.normalize(keyword).replaceAll("[—–]"," ").split("\\s+")){
   if(token.isBlank()||token.equals("-"))continue;
   String like="%"+token.replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%";
   from+=" AND CONCAT(r.request_code,N' ',e.employee_code,N' ',e.full_name,N' ',r.reason) COLLATE Latin1_General_100_CI_AI LIKE ?";args.add(like);
  }
  Long total=db.queryForObject("SELECT COUNT_BIG(*)"+from,Long.class,args.toArray());
  args.add(paging.getOffset());args.add(paging.getPageSize());
  var rows=db.queryForList("SELECT r.request_id requestId,r.request_code requestCode,r.employee_id employeeId,e.employee_code employeeCode,e.full_name employeeName,r.request_type requestType,"+finalStatus+" status,CONVERT(varchar(23),r.submitted_at,126)+'Z' submittedAt,r.reason,s.stage_status stageStatus,decider.username decidedBy,CONVERT(varchar(23),s.decided_at,126)+'Z' decidedAt,s.note,CONVERT(varchar(23),w.withdrawn_at,126)+'Z' withdrawnAt"+from+" ORDER BY r.submitted_at DESC,r.request_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray());
  return new org.springframework.data.domain.PageImpl<>(rows,paging,total);
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> history(long request,Principal p){
  var rows=db.queryForList("SELECT employee_id FROM dbo.employee_requests WHERE request_id=?",Long.class,request);
  if(rows.isEmpty())throw new com.hrm.exception.ResourceNotFoundException("Không tìm thấy đơn");
  if(!canRead(rows.get(0),p))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Đơn ngoài phòng ban đang phụ trách");
  return db.queryForList("SELECT l.audit_id auditId,l.action,ac.username actor,CONVERT(varchar(23),l.occurred_at,126)+'Z' occurredAt,l.reason,l.before_data_json beforeJson,l.after_data_json afterJson FROM dbo.audit_logs l LEFT JOIN dbo.accounts ac ON ac.account_id=l.actor_account_id WHERE l.target_table='employee_requests' AND l.target_record_key=? ORDER BY l.occurred_at DESC,l.audit_id DESC",Long.toString(request));
 }
 private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
