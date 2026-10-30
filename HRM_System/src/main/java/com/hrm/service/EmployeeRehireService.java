package com.hrm.service;
import com.hrm.dto.*;
import com.hrm.enums.EmploymentStatus;
import com.hrm.model.EmployeeAssignment;
import com.hrm.repository.*;
import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class EmployeeRehireService {
 private final EmployeeRepository employees;
 private final EmployeeAssignmentRepository assignments;
 private final DepartmentRepository departments;
 private final PositionRepository positions;
 private final AssignmentGuardService guard;
 private final AuditLogService audit;
 private final Clock clock;
 private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 public EmployeeRehireService(EmployeeRepository employees,EmployeeAssignmentRepository assignments,
 DepartmentRepository departments,PositionRepository positions,AssignmentGuardService guard,
 AuditLogService audit,org.springframework.jdbc.core.JdbcTemplate jdbc,@Qualifier("hrmBusinessClock") Clock clock){
 this.employees=employees;this.assignments=assignments;this.departments=departments;
 this.positions=positions;this.guard=guard;this.audit=audit;this.jdbc=jdbc;this.clock=clock;}
 @Transactional
 public void rehire(long id,EmployeeRehireRequest request,long actor){
 if(actor<=0)throw new IllegalArgumentException("Thiếu tài khoản thực hiện");
 guard.lockTimeline();
 var employee=employees.findByIdForUpdate(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Không tìm thấy nhân viên"));
 if(employee.getEmploymentStatus()!=EmploymentStatus.DA_NGHI)throw fail(HttpStatus.CONFLICT,"Chỉ tiếp nhận lại nhân viên đã nghỉ việc");
 var day=request.effectiveDate();
 if(day==null||day.isAfter(LocalDate.now(clock)))throw fail(HttpStatus.BAD_REQUEST,"Ngày tiếp nhận lại không được ở tương lai");
 if(employee.getTerminationDate()==null||!day.isAfter(employee.getTerminationDate()))throw fail(HttpStatus.CONFLICT,"Ngày tiếp nhận lại phải sau ngày nghỉ việc đã ghi nhận");
 if(request.reason()==null||request.reason().isBlank())throw fail(HttpStatus.BAD_REQUEST,"Phải nhập lý do tiếp nhận lại");
 if(assignments.findHistoryByEmployeeId(id).stream().anyMatch(a->a.getEffectiveTo()==null||a.getEffectiveTo().isAfter(day)))throw fail(HttpStatus.CONFLICT,"Phân công cũ hoặc tương lai chưa kết thúc; cần đối chiếu lịch sử trước");
 var department=departments.findById(request.departmentId()).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Không tìm thấy phòng ban"));
 var position=positions.findById(request.positionId()).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Không tìm thấy chức vụ"));
 if(!Boolean.TRUE.equals(department.getIsActive())||!Boolean.TRUE.equals(position.getIsActive()))throw fail(HttpStatus.CONFLICT,"Phòng ban và chức vụ phải đang hoạt động");
 if(AssignmentGuardService.HEAD_CODE.equals(position.getPositionCode()))throw fail(HttpStatus.CONFLICT,"Tiếp nhận với chức vụ thường rồi bổ nhiệm trưởng phòng riêng");
 Long pending=jdbc.queryForObject("""
 SELECT COUNT_BIG(*) FROM dbo.employee_requests r
 LEFT JOIN dbo.g2_resignation_executions x ON x.request_id=r.request_id
 WHERE r.employee_id=? AND r.request_type='NGHI_VIEC'
 AND (r.status='CHO_DUYET' OR (r.status='DA_DUYET' AND x.request_id IS NULL))
 AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
 """,Long.class,id);
 if(pending==null||pending>0)throw fail(HttpStatus.CONFLICT,"Còn đơn nghỉ việc chưa hoàn tất; cần xử lý trước khi tiếp nhận lại");
 var before=Map.of("employmentStatus","DA_NGHI","terminationDate",employee.getTerminationDate().toString());
 var assignment=new EmployeeAssignment();
 assignment.setEmployee(employee);assignment.setDepartment(department);assignment.setPosition(position);
 assignment.setEffectiveFrom(day);assignment.setCreatedAt(LocalDateTime.now(clock));assignment.setCreatedBy(actor);
 assignments.save(assignment);
 employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);employee.setTerminationDate(null);
 employee.setUpdatedAt(LocalDateTime.now(clock));employee.setUpdatedBy(actor);employees.save(employee);
 audit.record(new AuditEvent(actor,"EMPLOYEE_REHIRE","employees",Long.toString(id),request.reason().trim(),before,
 Map.of("employmentStatus","DANG_LAM","effectiveDate",day.toString(),"departmentId",request.departmentId(),"positionId",request.positionId())));
 // Preserve original hire date and history. Revoked accounts, contracts and leave balances are not reactivated.
 }
 private static ResponseStatusException fail(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
