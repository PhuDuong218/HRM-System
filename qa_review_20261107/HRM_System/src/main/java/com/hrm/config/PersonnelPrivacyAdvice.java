package com.hrm.config;
import com.hrm.dto.*;
import com.hrm.service.PersonnelScopeService;
import java.lang.reflect.Type;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Enforces private employee fields in HTTP responses, including historical audit JSON. */
@ControllerAdvice(basePackages="com.hrm.controller")
public class PersonnelPrivacyAdvice extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {
 private final com.hrm.service.AuditReadPolicy auditPolicy;private final PersonnelScopeService scope;private final JdbcTemplate jdbc;private final JsonMapper json=JsonMapper.builder().build();
 public PersonnelPrivacyAdvice(PersonnelScopeService scope,JdbcTemplate jdbc,com.hrm.service.AuditReadPolicy auditPolicy){this.scope=scope;this.jdbc=jdbc;this.auditPolicy=auditPolicy;}
 @Override public boolean supports(MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter){return true;}
 @Override public Object afterBodyRead(Object body,HttpInputMessage message,MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter){
  var actor=PersonnelScopeService.current();if(actor==null)return body;
  if(PersonnelScopeService.isHr()){
   if(body instanceof EmployeeRequestDTO r && r.getDepartmentId()!=null)scope.department(r.getDepartmentId());
   if(body instanceof EmployeeTransferRequestDTO r && r.departmentId()!=null)scope.department(r.departmentId());
   if(body instanceof LeaveEntitlementRequestDTO r && r.getEmployeeId()!=null)scope.employee(r.getEmployeeId(),false);
   if(body instanceof ContractCreateRequest r && r.employeeId()!=null)scope.managedEmployee(r.employeeId());
   if(body instanceof ContractRenewRequest r && r.newContract()!=null && r.newContract().employeeId()!=null)scope.managedEmployee(r.newContract().employeeId());
   if(body instanceof DecisionRequest r && r.employeeId()!=null)scope.managedEmployee(r.employeeId());
  }
  var attrs=RequestContextHolder.getRequestAttributes();if(!(attrs instanceof ServletRequestAttributes servlet))return body;
  var matcher=java.util.regex.Pattern.compile("^/employees/(\\d+)(?:/contact)?$").matcher(servlet.getRequest().getRequestURI());
  if(!matcher.matches())return body;long id=PersonnelPolicyWebConfiguration.recordId(matcher.group(1));
  if(body instanceof EmployeeUpdateRequestDTO r){
   if(!Objects.equals(actor.employeeId(),id))scope.managedEmployee(id);
   if(!scope.canReadPrivate(id))r.ignorePrivateChanges();
   com.hrm.service.EmployeeWriteRules.requiredIfPresent(r.hasEmail(),r.getEmail(),"Email");
   com.hrm.service.EmployeeWriteRules.requiredIfPresent(r.hasIdentityNumber(),r.getIdentityNumber(),"CCCD/CMND");
   com.hrm.service.EmployeeWriteRules.requiredIfPresent(r.hasAddress(),r.getAddress(),"Địa chỉ");
   com.hrm.service.EmployeeWriteRules.identity(r.getIdentityNumber());
   var existing=jdbc.queryForList("SELECT email,phone,identity_number,address FROM dbo.employees WHERE employee_id=?",id);
   if(!existing.isEmpty()){
    var old=existing.get(0);
    if(Objects.equals(actor.employeeId(),id)&&((r.hasEmail()&&!Objects.equals(clean(r.getEmail()),clean((String)old.get("email"))))||(r.hasPhone()&&!Objects.equals(clean(r.getPhone()),clean((String)old.get("phone"))))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự sửa thông tin liên hệ");
    if(Objects.equals(actor.employeeId(),id)) {
     if(r.getIdentityNumber()!=null&&!Objects.equals(clean(r.getIdentityNumber()),clean((String)old.get("identity_number"))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự sửa thông tin liên hệ");
     if(r.getAddress()!=null&&!Objects.equals(clean(r.getAddress()),clean((String)old.get("address"))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự sửa thông tin liên hệ");
    }
   }
  }
  if(body instanceof EmployeeContactPatchDTO r){
   if(!Objects.equals(actor.employeeId(),id)&&(r.getAddress()!=null||r.getIdentityNumber()!=null))scope.managedEmployee(id);
   if(!scope.canReadPrivate(id))r.ignorePrivateChanges();
   com.hrm.service.EmployeeWriteRules.requiredIfPresent(r.hasEmail(),r.getEmail(),"Email");
   com.hrm.service.EmployeeWriteRules.requiredIfPresent(r.hasIdentityNumber(),r.getIdentityNumber(),"CCCD/CMND");
   com.hrm.service.EmployeeWriteRules.requiredIfPresent(r.hasAddress(),r.getAddress(),"Địa chỉ");
   com.hrm.service.EmployeeWriteRules.identity(r.getIdentityNumber());
  }
  return body;
 }
 private static String clean(String v){return v==null||v.isBlank()?null:v.trim();}
 @Override public boolean supports(MethodParameter parameter,Class<? extends HttpMessageConverter<?>> converter){return true;}
 @Override public Object beforeBodyWrite(Object body,MethodParameter parameter,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
  var actor=PersonnelScopeService.current();
  if(body instanceof EmployeeDetailDTO detail){if(!scope.canReadPrivate(detail.getEmployeeId())){detail.setAddress(null);detail.setIdentityNumber(null);}return detail;}
  String controller=parameter.getContainingClass().getSimpleName();
  if(!Set.of("G1AuditController","PersonnelAuditController","AuditLogController").contains(controller))return body;
  if(body instanceof Page<?> page)return page.map(row->scrubAudit(row,request));return body;
 }
 public Map<String,Object> scrubAudit(Object row,ServerHttpRequest request){
  Map<String,Object> map=new LinkedHashMap<>(json.convertValue(row,Map.class));
  var actor=PersonnelScopeService.current();var query=org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
  String table=Objects.toString(map.getOrDefault("targetTable",query.getFirst("targetTable")),"");
  String record=Objects.toString(map.getOrDefault("targetRecordKey",map.getOrDefault("recordKey",query.getFirst("recordId"))),"");
  return auditPolicy.row(table,record,map);
 }
}
