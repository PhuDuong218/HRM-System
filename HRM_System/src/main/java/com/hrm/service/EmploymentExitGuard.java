package com.hrm.service;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Shared constraints for direct decisions and approved resignation execution. */
@Service
public class EmploymentExitGuard {
 private final JdbcTemplate db;private final PayrollHistoryGuard payroll;
 public EmploymentExitGuard(JdbcTemplate db,PayrollHistoryGuard payroll){this.db=db;this.payroll=payroll;}
 public void check(long employee,LocalDate last){
  payroll.assertMutable(employee,last.plusDays(1),null);
  long heads=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id WHERE a.employee_id=? AND p.position_code='TRUONG_PHONG' AND (a.effective_to IS NULL OR a.effective_to>?)",Long.class,employee,last.plusDays(1));
  if(heads>0)throw PersonnelWorkflowSupport.conflict("Phải kết thúc/thay trưởng phòng trước ngày nghỉ việc");
  long incompatible=db.queryForObject("""
   SELECT (SELECT COUNT_BIG(*) FROM dbo.work_schedules WHERE employee_id=? AND planned_end>?)
    +(SELECT COUNT_BIG(*) FROM dbo.contracts WHERE employee_id=? AND status='DA_KY' AND (end_date IS NULL OR end_date>?))
    +(SELECT COUNT_BIG(*) FROM dbo.employee_assignments WHERE employee_id=? AND effective_from>=?)
    +(SELECT COUNT_BIG(*) FROM dbo.employee_requests r JOIN dbo.leave_request_details l ON l.request_id=r.request_id WHERE r.employee_id=? AND r.status='DA_DUYET' AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id) AND l.leave_end>?)
    +(SELECT COUNT_BIG(*) FROM dbo.attendance_records WHERE employee_id=? AND work_date>? AND verification_status='DA_XAC_NHAN' AND invalidated_at IS NULL)
   """,Long.class,employee,last.plusDays(1).atStartOfDay(),employee,last,employee,last.plusDays(1),employee,last.plusDays(1).atStartOfDay(),employee,last);
  if(incompatible>0)throw PersonnelWorkflowSupport.conflict("Còn lịch, công xác nhận, hợp đồng, phân công tương lai hoặc phép đã duyệt vượt ngày nghỉ; phối hợp xử lý trước");
 }
}
