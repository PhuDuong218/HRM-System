package com.hrm.service;
import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Value;
@Service public class MaternityLeavePolicy {
 private final JdbcTemplate db;private final int maleDays,femaleMonths;
 public MaternityLeavePolicy(JdbcTemplate db,@Value("${hrm.leave.maternity.male-days:14}") int maleDays,@Value("${hrm.leave.maternity.female-months:6}") int femaleMonths){if(maleDays<1||maleDays>366||femaleMonths<1||femaleMonths>24)throw new IllegalArgumentException("Invalid maternity policy");this.db=db;this.maleDays=maleDays;this.femaleMonths=femaleMonths;}
 public boolean isMaternity(long type){String code=db.queryForObject("SELECT type_code FROM dbo.leave_types WHERE leave_type_id=?",String.class,type);return code!=null&&Set.of("MATERNITY","THAI_SAN").contains(code.toUpperCase(Locale.ROOT));}
 public static void validateLimit(String gender,LocalDateTime start,LocalDateTime end,int maleDays,int femaleMonths){
  if(start==null||end==null||!start.isBefore(end))throw PersonnelWorkflowSupport.bad("Khoảng nghỉ không hợp lệ");
  LocalDateTime limit;if("MALE".equals(gender))limit=start.plusDays(maleDays);else if("FEMALE".equals(gender))limit=start.plusMonths(femaleMonths);else throw PersonnelWorkflowSupport.bad("Cần cập nhật giới tính trong hồ sơ để áp dụng chính sách thai sản");
  if(end.isAfter(limit))throw PersonnelWorkflowSupport.bad("Chính sách trung tâm: nam tối đa "+maleDays+" ngày lịch, nữ tối đa "+femaleMonths+" tháng mỗi đơn thai sản");
 }
 public void validate(long employee,long type,LocalDateTime start,LocalDateTime end){if(!isMaternity(type))return;String gender=db.queryForObject("SELECT gender FROM dbo.employees WHERE employee_id=?",String.class,employee);validateLimit(gender,start,end,maleDays,femaleMonths);}
 public Map<String,Object> options(long employee){String gender=db.queryForObject("SELECT gender FROM dbo.employees WHERE employee_id=?",String.class,employee);return Map.of("gender",gender==null?"":gender,"maleDays",maleDays,"femaleMonths",femaleMonths,"types",db.queryForList("SELECT t.leave_type_id leaveTypeId,t.type_code typeCode,t.type_name typeName,COALESCE(r.evidence_required,0) evidenceRequired FROM dbo.leave_types t LEFT JOIN dbo.personnel_leave_rules r ON r.leave_type_id=t.leave_type_id WHERE t.is_active=1 AND t.type_code IN ('ANNUAL','PHEP_NAM','SICK','NGHI_OM','MATERNITY','THAI_SAN') ORDER BY t.leave_type_id"));}
}
