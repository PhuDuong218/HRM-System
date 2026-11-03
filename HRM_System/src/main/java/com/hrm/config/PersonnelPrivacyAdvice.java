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
 private final PersonnelScopeService scope;private final JdbcTemplate jdbc;private final JsonMapper json=JsonMapper.builder().build();
 public PersonnelPrivacyAdvice(PersonnelScopeService scope,JdbcTemplate jdbc){this.scope=scope;this.jdbc=jdbc;}
 @Override public boolean supports(MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter){return true;}
 @Override public Object afterBodyRead(Object body,HttpInputMessage message,MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter){
  var actor=PersonnelScopeService.current();if(actor==null)return body;
  if(PersonnelScopeService.isHr()){
   if(body instanceof EmployeeRequestDTO r && r.getDepartmentId()!=null)scope.department(r.getDepartmentId());
   if(body instanceof EmployeeTransferRequestDTO r && r.departmentId()!=null)scope.department(r.departmentId());
   if(body instanceof LeaveEntitlementRequestDTO r && r.getEmployeeId()!=null)scope.employee(r.getEmployeeId(),false);
  }
  var attrs=RequestContextHolder.getRequestAttributes();if(!(attrs instanceof ServletRequestAttributes servlet))return body;
  var matcher=java.util.regex.Pattern.compile("^/employees/(\\d+)(?:/contact)?$").matcher(servlet.getRequest().getRequestURI());
  if(!matcher.matches())return body;long id=PersonnelPolicyWebConfiguration.recordId(matcher.group(1));
  if(body instanceof EmployeeUpdateRequestDTO r){
   var existing=jdbc.queryForList("SELECT email,phone,identity_number FROM dbo.employees WHERE employee_id=?",id);
   if(!existing.isEmpty()){
    var old=existing.get(0);
    if(Objects.equals(actor.employeeId(),id)&&(!Objects.equals(clean(r.getEmail()),clean((String)old.get("email")))||!Objects.equals(clean(r.getPhone()),clean((String)old.get("phone")))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự sửa thông tin liên hệ");
    if(Objects.equals(actor.employeeId(),id)) {
     if(r.getIdentityNumber()!=null&&!Objects.equals(clean(r.getIdentityNumber()),clean((String)old.get("identity_number"))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự sửa thông tin liên hệ");
    } else if(r.getIdentityNumber()!=null)throw privateWrite();
    r.setIdentityNumber((String)old.get("identity_number"));
   }
  }
  if(body instanceof EmployeeContactPatchDTO r&&!Objects.equals(actor.employeeId(),id)&&(r.getAddress()!=null||r.getIdentityNumber()!=null))throw privateWrite();
  return body;
 }
 private static String clean(String v){return v==null||v.isBlank()?null:v.trim();}
 private static ResponseStatusException privateWrite(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"CCCD và địa chỉ nhà chỉ dành cho chủ hồ sơ");}
 @Override public boolean supports(MethodParameter parameter,Class<? extends HttpMessageConverter<?>> converter){return true;}
 @Override public Object beforeBodyWrite(Object body,MethodParameter parameter,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
  var actor=PersonnelScopeService.current();
  if(body instanceof EmployeeDetailDTO detail){boolean own=actor!=null&&Objects.equals(actor.employeeId(),detail.getEmployeeId());if(!own){detail.setAddress(null);detail.setIdentityNumber(null);}return detail;}
  String controller=parameter.getContainingClass().getSimpleName();
  if(!Set.of("G1AuditController","PersonnelAuditController","AuditLogController").contains(controller))return body;
  if(body instanceof Page<?> page)return page.map(row->scrubAudit(row,request));return body;
 }
 public Map<String,Object> scrubAudit(Object row,ServerHttpRequest request){
  Map<String,Object> map=new LinkedHashMap<>(json.convertValue(row,Map.class));
  var actor=PersonnelScopeService.current();var query=org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
  String table=Objects.toString(map.getOrDefault("targetTable",query.getFirst("targetTable")),"");
  String record=Objects.toString(map.getOrDefault("targetRecordKey",map.getOrDefault("recordKey",query.getFirst("recordId"))),"");
  boolean own=actor!=null&&"employees".equals(table)&&Objects.equals(String.valueOf(actor.employeeId()),record);
  if(!own)for(String field:List.of("beforeDataJson","afterDataJson")){Object value=map.get(field);if(value instanceof String text){try{map.put(field,json.writeValueAsString(redact(json.readValue(text,Object.class))));}catch(RuntimeException ex){map.put(field,null);}}}
  return map;
 }
 private Object redact(Object value){if(value instanceof Map<?,?> map){var result=new LinkedHashMap<String,Object>();map.forEach((k,v)->{String key=k.toString(),normalized=key.replace("_","").toLowerCase(Locale.ROOT);result.put(key,Set.of("identitynumber","address","homeaddress").contains(normalized)?"[REDACTED]":redact(v));});return result;}if(value instanceof Collection<?> list)return list.stream().map(this::redact).toList();return value;}
}
