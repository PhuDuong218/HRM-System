package com.hrm.service;

import java.time.*;
import java.util.*;
import com.hrm.dto.ResignationReadinessDTO.Issue;
import com.hrm.dto.ResignationReadinessDTO.Record;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Shared constraints for direct decisions and approved resignation execution. */
@Service
public class EmploymentExitGuard {
 private final JdbcTemplate db;private final PayrollHistoryGuard payroll;
 public EmploymentExitGuard(JdbcTemplate db,PayrollHistoryGuard payroll){this.db=db;this.payroll=payroll;}
 public void check(long employee,LocalDate last){
  var issues=inspect(employee,last);
  if(!issues.isEmpty())throw PersonnelWorkflowSupport.conflict("Chưa thể duyệt/thực thi nghỉ việc: "+issues.stream().map(i->i.title()+" ("+i.count()+")").collect(java.util.stream.Collectors.joining("; "))+". Mở kiểm tra bàn giao để xử lý, sau đó kiểm tra lại.");
 }
 public List<Issue> inspect(long employee,LocalDate last){
  if(last==null||last.isAfter(LocalDate.of(9999,12,30)))throw PersonnelWorkflowSupport.bad("Ngày làm cuối ngoài phạm vi hỗ trợ");
  var issues=new ArrayList<Issue>();var next=last.plusDays(1);var cutoff=next.atStartOfDay();
  long locked=payroll.lockedPeriodCount(employee,next,null);
  if(locked>0)issues.add(new Issue("PAYROLL","Kỳ lương đã chốt",locked,"Đối soát với kế toán bằng quy trình điều chỉnh; không mở hoặc sửa số liệu đã chốt để duyệt đơn.","/payroll",List.of()));
  group(issues,"HEAD","Nhiệm kỳ trưởng phòng", "Kết thúc nhiệm kỳ hoặc bổ nhiệm người thay; ngày đầu không còn đảm nhiệm phải không muộn hơn ngày sau ngày làm cuối.","/departments",
   "a.assignment_id AS id,p.position_name AS label,a.effective_from AS dateFrom,a.effective_to AS dateTo",
   "FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id WHERE a.employee_id=? AND p.position_code='TRUONG_PHONG' AND (a.effective_to IS NULL OR a.effective_to>?)",employee,next);
  group(issues,"SCHEDULE","Lịch làm việc sau ngày làm cuối","Hủy hoặc phân công lại các lịch sau ngày làm cuối. Với ca qua đêm, đối soát giờ kết thúc ca; không xóa lịch có lịch sử công.","/schedules",
   "schedule_id AS id,CONCAT(N'Lịch #',schedule_id) AS label,planned_start AS dateFrom,planned_end AS dateTo",
   "FROM dbo.work_schedules WHERE cancelled_at IS NULL AND employee_id=? AND planned_end>?",employee,cutoff);
  group(issues,"CONTRACT","Hợp đồng còn hiệu lực vượt ngày làm cuối","Ghi nhận chấm dứt hợp đồng với ngày cuối phù hợp và lý do. Hợp đồng tương lai cần được xử lý theo quy trình hợp đồng.","/contracts",
   "contract_id AS id,contract_number AS label,start_date AS dateFrom,end_date AS dateTo",
   "FROM dbo.contracts WHERE employee_id=? AND status='DA_KY' AND (end_date IS NULL OR end_date>?)",employee,last);
  group(issues,"ASSIGNMENT","Phân công hoặc điều chuyển tương lai","Đối soát lịch phân công có hiệu lực sau ngày làm cuối; phối hợp người phụ trách phân công nếu chưa có thao tác hủy phù hợp. Không ghi đè lịch sử công tác.","/assignments",
   "assignment_id AS id,CONCAT(N'Phân công #',assignment_id) AS label,effective_from AS dateFrom,effective_to AS dateTo",
   "FROM dbo.employee_assignments WHERE employee_id=? AND effective_from>=?",employee,next);
  group(issues,"LEAVE","Đơn phép đã duyệt vượt ngày làm cuối","Hủy hoặc điều chỉnh phép theo quy trình. Phép đã bắt đầu hoặc giao kỳ công/lương khóa cần đối soát riêng; không tự hoàn quỹ bằng sửa DB.","/requests",
   "r.request_id AS id,r.request_code AS label,l.leave_start AS dateFrom,l.leave_end AS dateTo",
   "FROM dbo.employee_requests r JOIN dbo.leave_request_details l ON l.request_id=r.request_id WHERE r.employee_id=? AND r.status='DA_DUYET' AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id) AND l.leave_end>?",employee,cutoff);
  group(issues,"ATTENDANCE","Công đã xác nhận sau ngày làm cuối","Kiểm tra ngày làm cuối và công thực tế. Nếu công sai, dùng quy trình điều chỉnh/xác nhận công đúng thẩm quyền; không xóa công đã xác nhận.","/attendance",
   "attendance_id AS id,CONCAT(N'Công #',attendance_id) AS label,work_date AS dateFrom,work_date AS dateTo",
   "FROM dbo.attendance_records WHERE employee_id=? AND work_date>? AND verification_status='DA_XAC_NHAN' AND invalidated_at IS NULL",employee,last);
  return List.copyOf(issues);
 }
 private static String date(java.sql.ResultSet row,String column)throws java.sql.SQLException{Object value=row.getObject(column);return value==null?null:value.toString().replace(' ','T');}
 private void group(List<Issue> issues,String code,String title,String instruction,String route,String columns,String from,Object...args){
  Long count=db.queryForObject("SELECT COUNT_BIG(*) "+from,Long.class,args);
  if(count==null||count==0)return;
  var records=db.query("SELECT TOP (5) "+columns+" "+from+" ORDER BY id",(r,n)->new Record(r.getLong("id"),r.getString("label"),date(r,"dateFrom"),date(r,"dateTo")),args);
  issues.add(new Issue(code,title,count,instruction,route,records));
 }
}
