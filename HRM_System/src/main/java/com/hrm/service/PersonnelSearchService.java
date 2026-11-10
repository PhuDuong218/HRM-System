package com.hrm.service;
import com.hrm.dto.*;
import com.hrm.util.PersonnelSearch;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly=true)
public class PersonnelSearchService {
 private final com.hrm.repository.EmployeeRepository employees;private final EmployeeAssignmentService assignments;private final EmployeeQualificationService qualifications;private final PersonnelAccessService access;private final PersonnelScopeService scope;
 public PersonnelSearchService(EmployeeAssignmentService a,EmployeeQualificationService q,PersonnelAccessService x,PersonnelScopeService s,com.hrm.repository.EmployeeRepository e){employees=e;assignments=a;qualifications=q;access=x;scope=s;}
 public Page<EmployeeAssignmentDTO> assignments(Principal p,Long employeeId,Long departmentId,Long positionId,String keyword,LocalDate asOf,int page,int size){
  access.manager(p);if(employeeId!=null)scope.employee(employeeId,true);
  var rows=assignments.getAllAssignments().stream().filter(a->employeeId==null||employeeId.equals(a.employeeId())).filter(a->departmentId==null||departmentId.equals(a.departmentId())).filter(a->positionId==null||positionId.equals(a.positionId())).filter(a->asOf==null||(!a.effectiveFrom().isAfter(asOf)&&(a.effectiveTo()==null||a.effectiveTo().isAfter(asOf)))).filter(a->PersonnelSearch.matches(keyword,a.employeeCode(),a.employeeName(),a.departmentName(),a.positionName())).sorted(Comparator.comparing(EmployeeAssignmentDTO::assignmentId).reversed()).toList();
  return PersonnelSearch.page(rows,page,size);
 }
 public Page<EmployeeQualificationDTO> qualifications(Principal p,Long employeeId,Long qualificationLevelId,Boolean primary,Integer yearFrom,Integer yearTo,String keyword,int page,int size){
  return qualifications(p,employeeId,qualificationLevelId,primary,yearFrom,yearTo,keyword,null,null,page,size);
 }
 public Page<EmployeeQualificationDTO> qualifications(Principal p,Long employeeId,Long qualificationLevelId,Boolean primary,Integer yearFrom,Integer yearTo,String keyword,String validity,Integer minimumRank,int page,int size){
  if(validity!=null&&!Set.of("NO_EXPIRY","VALID","EXPIRING","EXPIRED").contains(validity))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái chứng chỉ không hợp lệ");
  if(minimumRank!=null&&(minimumRank<0||minimumRank>1000))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thứ bậc tối thiểu phải từ 0 đến 1000");

  access.manager(p);if(employeeId!=null)scope.employee(employeeId,true);
  if(yearFrom!=null&&yearTo!=null&&yearFrom>yearTo)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Năm bắt đầu phải nhỏ hơn hoặc bằng năm kết thúc");
  var allowed=PersonnelScopeService.isHr()?scope.employees():null;
  var visible=qualifications.searchRows().stream().filter(q->allowed==null||allowed.contains(q.employeeId())).toList();
  var names=employees.findAllById(visible.stream().map(EmployeeQualificationDTO::employeeId).distinct().toList()).stream().collect(java.util.stream.Collectors.toMap(e->e.getEmployeeId(),e->e.getEmployeeCode()+" "+e.getFullName()));
  var rows=visible.stream().filter(q->validity==null||validity.equals(q.validityStatus())).filter(q->minimumRank==null||(q.rankOrder()!=null&&q.rankOrder()>=minimumRank)).filter(q->employeeId==null||employeeId.equals(q.employeeId())).filter(q->qualificationLevelId==null||qualificationLevelId.equals(q.qualificationLevelId())).filter(q->primary==null||primary.equals(Boolean.TRUE.equals(q.isPrimary()))).filter(q->yearFrom==null||(q.graduationYear()!=null&&q.graduationYear()>=yearFrom)).filter(q->yearTo==null||(q.graduationYear()!=null&&q.graduationYear()<=yearTo)).filter(q->PersonnelSearch.matches(keyword,names.get(q.employeeId()),q.levelName(),q.levelCode(),q.major(),q.institution(),q.certificateNumber())).toList();
  return PersonnelSearch.page(rows,page,size);
 }
}
