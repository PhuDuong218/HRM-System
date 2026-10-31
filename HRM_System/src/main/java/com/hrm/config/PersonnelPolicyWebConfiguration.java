package com.hrm.config;
import com.hrm.service.PersonnelScopeService;
import com.hrm.security.AuthenticatedAccount;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class PersonnelPolicyWebConfiguration implements WebMvcConfigurer {
 private final PersonnelScopeService scope;
 public PersonnelPolicyWebConfiguration(PersonnelScopeService scope){this.scope=scope;}
 private static final Set<String> CONTROLLERS=Set.of("EmployeeController","EmployeeDetailController","EmployeeTerminationController","EmployeeRehireController","EmployeeAssignmentController","EmployeeQualificationController","LeaveEntitlementController","DepartmentController","DepartmentHeadController","EmployeeRequestController","G1AuditController","PersonnelAuditController");
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
 if(!(handler instanceof HandlerMethod method)||!CONTROLLERS.contains(method.getBeanType().getSimpleName()))return true;
 AuthenticatedAccount actor=PersonnelScopeService.current();if(actor==null)return true; // Authentication filter handles missing JWT.
 String path=request.getRequestURI(),verb=request.getMethod();boolean read="GET".equals(verb);
 var employee=Pattern.compile("^/employees/(\\d+)(?:/.*)?$").matcher(path);
 if(employee.matches()){
  long id=recordId(employee.group(1));scope.employee(id,read);
  if(!read&&path.endsWith("/contact")&&Objects.equals(actor.employeeId(),id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự sửa thông tin liên hệ");
 }
 if("HR".equals(actor.roleCode())){
  var dept=Pattern.compile("^/departments/(\\d+)(?:/.*)?$").matcher(path);if(dept.matches())scope.department(recordId(dept.group(1)));
  for(var entry:Map.of("employee-qualifications",new String[]{"employee_qualifications","employee_qualification_id"},"leave-entitlements",new String[]{"leave_entitlements","entitlement_id"},"employee-requests",new String[]{"employee_requests","request_id"}).entrySet()){
   var match=Pattern.compile("^/"+entry.getKey()+"/(\\d+)(?:/.*)?$").matcher(path);
   if(match.matches())scope.employee(scope.employeeOf(entry.getValue()[0],entry.getValue()[1],recordId(match.group(1))),read);
  }
  for(String key:List.of("employeeId")){String id=request.getParameter(key);if(id!=null&&id.matches("[0-9]+"))scope.employee(recordId(id),read);}
  String table=request.getParameter("targetTable"),id=request.getParameter("recordId");
  if(table!=null&&id!=null&&id.matches("[0-9]+")){
   long record=recordId(id);
   if(table.equals("employees"))scope.employee(record,true);else if(table.equals("departments"))scope.department(record);
   else if(table.equals("employee_qualifications"))scope.employee(scope.employeeOf(table,"employee_qualification_id",record),true);
   else if(table.equals("leave_entitlements"))scope.employee(scope.employeeOf(table,"entitlement_id",record),true);
   else if(table.equals("employee_requests"))scope.employee(scope.employeeOf(table,"request_id",record),true);
  }
 }
 return true;
 }});}
 static long recordId(String value) {
  try {return Long.parseLong(value);}
  catch(NumberFormatException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã bản ghi không hợp lệ");}
 }
}
