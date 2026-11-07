package com.hrm.security;

import com.hrm.HrmApplication;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real HTTP + JWT + SQL Server. All fixtures are QA_ records created through APIs. */
@SpringBootTest(classes=HrmApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.profiles.active=local,hrm-db","spring.jpa.hibernate.ddl-auto=none","spring.jpa.show-sql=false",
 "hrm.contracts.expiry-job-enabled=false","personnel.resignations.scheduler-enabled=false",
 "hrm.attendance.missing-checkout-scan-enabled=false"})
@EnabledIfEnvironmentVariable(named="HRM_VERIFIED_BUGS_IT",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerifiedBugsSqlServerIntegrationTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate db;
 final HttpClient client=HttpClient.newHttpClient();
 final JsonMapper json=JsonMapper.builder().build();
 final String tag="QA_FIX_"+UUID.randomUUID().toString().substring(0,8);
 String admin,password;long employeeA,employeeB,departmentA,departmentB;final Map<String,String> users=new HashMap<>();
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){
  String url=Objects.requireNonNull(System.getenv("HRM_VERIFIED_BUGS_URL"),"Explicit test URL required");
  if(!url.matches("(?is).*databaseName=hrm_test(?:;|$).*"))throw new IllegalStateException("Only hrm_test is permitted");
  r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->System.getenv("HRM_VERIFIED_BUGS_USER"));r.add("spring.datasource.password",()->System.getenv("HRM_VERIFIED_BUGS_PASSWORD"));
  r.add("app.security.jwt-secret",()->System.getenv("JWT_SECRET"));
 }
 HttpResponse<String> request(String method,String path,Object body,String token)throws Exception {
  var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
  if(token!=null)builder.header("Authorization","Bearer "+token);
  return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
 }
 @SuppressWarnings("unchecked") Map<String,Object> object(HttpResponse<String> r){return json.readValue(r.body(),Map.class);}
 Map<String,Object> create(String path,Object body)throws Exception {var r=request("POST",path,body,admin);assertEquals(201,r.statusCode(),path);return object(r);}
 long id(Map<String,Object> value,String key){return ((Number)value.get(key)).longValue();}
 String login(String name)throws Exception {var r=request("POST","/api/auth/login",Map.of("username",name,"password",password),null);assertEquals(200,r.statusCode(),"Test login failed");return object(r).get("accessToken").toString();}
 String token(String key)throws Exception{return login(users.get(key));}
 long employee(String suffix,long department,long position)throws Exception {
  return id(create("/employees",Map.of("employeeCode",tag+suffix,"fullName",tag+suffix,"hireDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(3).toString(),"departmentId",department,"positionId",position)),"employeeId");
 }
 void account(String key,long employee,long role)throws Exception {String username=tag+key;create("/api/admin/accounts",Map.of("employeeId",employee,"roleId",role,"username",username,"password",password,"confirmPassword",password,"active",true));users.put(key,username);}
 @BeforeAll @SuppressWarnings("unchecked") void setup()throws Exception {
  assertEquals("hrm_test",db.queryForObject("SELECT DB_NAME()",String.class));
  password=Objects.requireNonNull(System.getenv("HRM_VERIFIED_BUGS_LOGIN_PASSWORD"));admin=login(Objects.requireNonNull(System.getenv("HRM_VERIFIED_BUGS_LOGIN_USER")));
  var rolesResponse=request("GET","/api/admin/roles",null,admin);assertEquals(200,rolesResponse.statusCode());var roles=(List<Map<String,Object>>)(List<?>)json.readValue(rolesResponse.body(),List.class);Map<String,Long> roleIds=new HashMap<>();roles.forEach(x->roleIds.put(x.get("roleCode").toString(),id(x,"roleId")));
  departmentA=id(create("/departments",Map.of("departmentCode",tag+"A","departmentName",tag+" A","isActive",true)),"departmentId");
  departmentB=id(create("/departments",Map.of("departmentCode",tag+"B","departmentName",tag+" B","isActive",true)),"departmentId");
  long pos=id(create("/positions",Map.of("positionCode",tag+"P","positionName",tag+" Position","isActive",true)),"positionId");
  employeeA=employee("E1",departmentA,pos);employeeB=employee("E2",departmentB,pos);
  account("EMP",employeeA,roleIds.get("EMPLOYEE"));account("OTHER",employeeB,roleIds.get("EMPLOYEE"));
  for(var item:Map.of("HRA",departmentA,"HRB",departmentB).entrySet()){
   long e=employee(item.getKey(),item.getValue(),pos);String name=tag+item.getKey();var a=create("/api/admin/accounts",Map.of("employeeId",e,"roleId",roleIds.get("HR"),"username",name,"password",password,"confirmPassword",password,"active",true));users.put(item.getKey(),name);
   var result=request("POST","/api/admin/hr-scopes",Map.of("hrAccountId",id(a,"accountId"),"departmentId",item.getValue(),"effectiveFrom",LocalDateTime.now(ZoneOffset.UTC).minusHours(1).toString(),"reason",tag+" scope"),admin);assertEquals(200,result.statusCode());
  }
  long type=id(create("/leave-types",Map.of("typeCode",tag+"T","typeName",tag+" Leave","isPaid",true,"isActive",true)),"leaveTypeId");
  create("/leave-entitlements",Map.of("employeeId",employeeA,"leaveTypeId",type,"periodStart","2026-01-01","periodEnd","2027-01-01","entitledHours",16));
 }
 @Test void hrOutsideScopeCannotReadBalancesButOwnerInsideHrAndAdminCan()throws Exception {
  String path="/employee-requests/balances/"+employeeA;
  assertEquals(403,request("GET",path,null,token("HRB")).statusCode());
  assertEquals(403,request("GET",path,null,token("OTHER")).statusCode());
  for(String key:List.of("EMP","HRA")){var r=request("GET",path,null,token(key));assertEquals(200,r.statusCode());assertTrue(r.body().contains("16"));}
  assertEquals(200,request("GET",path,null,admin).statusCode());assertEquals(401,request("GET",path,null,null).statusCode());
 }
 @Test void logoutPersistsVersionAndRejectsEveryOlderToken()throws Exception {
  String old=token("EMP"),otherOld=token("EMP");Integer before=db.queryForObject("SELECT token_version FROM dbo.accounts WHERE username=?",Integer.class,users.get("EMP"));
  assertEquals(204,request("POST","/api/auth/logout",Map.of(),old).statusCode());
  assertEquals(before+1,db.queryForObject("SELECT token_version FROM dbo.accounts WHERE username=?",Integer.class,users.get("EMP")));
  for(String token:List.of(old,otherOld))assertEquals(401,request("GET","/api/auth/me",null,token).statusCode());
  assertEquals(200,request("GET","/api/auth/me",null,token("EMP")).statusCode());
 }
 @Test void missingAndInvalidScheduleDatesAre400WithoutInvalidatingSession()throws Exception {
  Map<String,String> errors=Map.of("","from","?from=2026-11-01","to","?to=2026-11-01","from","?from=not-a-date&to=2026-11-01","from","?from=2026-11-01&to=bad","to");
  for(var error:errors.entrySet()){var r=request("GET","/api/management/schedules/search"+error.getKey(),null,admin);assertEquals(400,r.statusCode());assertTrue(r.body().contains(error.getValue()));assertEquals(200,request("GET","/api/auth/me",null,admin).statusCode());}
  assertEquals(200,request("GET","/api/management/schedules/search?from=2026-11-01&to=2026-11-01",null,admin).statusCode());
  assertEquals(401,request("GET","/api/management/schedules/search",null,null).statusCode());
 }
 @Test void administrativePayrollAccessStillDeniesEmployee()throws Exception {
  assertEquals(403,request("GET","/payroll-batches",null,token("EMP")).statusCode());assertEquals(200,request("GET","/payroll-batches",null,admin).statusCode());
 }

 @Test void catalogSearchFiltersNamesCodesAndInactiveRowsBeforePaging()throws Exception {
  for(String path:List.of("/qualification-levels","/leave-types")){
   boolean level=path.contains("qualification");String code=level?"levelCode":"typeCode",name=level?"levelName":"typeName",key=level?"qualificationLevelId":"leaveTypeId";String label=tag+(level?"QL":"LT");
   var first=create(path,Map.of(code,label+"A",name,label+" Hân","isActive",true,"isPaid",false));
   var second=create(path,Map.of(code,label+"B",name,label+" Hân","isActive",true,"isPaid",false));
   assertEquals(200,request("PUT",path+"/"+id(second,key),Map.of(code,label+"B",name,label+" Hân","isActive",false,"isPaid",false),admin).statusCode());
   var filtered=request("GET",path+"?keyword="+label+"&active=false&size=1",null,admin);assertEquals(200,filtered.statusCode());assertEquals(1,id(object(filtered),"totalElements"));assertTrue(filtered.body().contains(label+"B"));
   var all=request("GET",path+"?keyword="+label+"&size=1",null,admin);assertEquals(2,id(object(all),"totalElements"));
   assertEquals(400,request("GET",path+"?keyword="+label+"&size=0",null,admin).statusCode());
  }
 }
}
