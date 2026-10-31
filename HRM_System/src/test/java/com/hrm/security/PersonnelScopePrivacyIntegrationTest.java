package com.hrm.security;
import java.time.*;
import java.util.UUID;
import com.hrm.service.ContractExpiryJob;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

/** Real SQL Server + real JWT/security chain; every fixture rolls back. Never run on hrm_db. */
@SpringBootTest(properties={"hrm.contracts.expiry-job-enabled=false","personnel.resignations.scheduler-enabled=false","hrm.attendance.missing-checkout-scan-enabled=false"})
@Transactional
class PersonnelScopePrivacyIntegrationTest {
 @Autowired JdbcTemplate db;@Autowired JwtService jwt;@Autowired WebApplicationContext context;@Autowired FilterChainProxy security;
 MockMvc mvc;String tag,admin,hr,owner;long inside,outside,hrEmployee,adminEmployee,departmentA,departmentB;
 long employee(String suffix){String code=tag+suffix;db.update("INSERT dbo.employees(employee_code,full_name,hire_date,address,identity_number) VALUES(?,?,?,'PRIVATE_HOME','PRIVATE_ID')",code,code,LocalDate.of(2020,1,1));return db.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,code);}
 String account(long employee,String role,String suffix){String username=tag+suffix;db.update("INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active) SELECT ?,role_id,?,REPLICATE('x',60),1 FROM dbo.roles WHERE role_code=?",employee,username,role);long id=db.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,username);return jwt.issue(id,employee,username,role,0);}
 long department(String suffix){db.update("INSERT dbo.departments(department_code,department_name) VALUES(?,?)",tag+suffix,tag+suffix);return db.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,tag+suffix);}
 @BeforeEach void setup(){
  assertEquals("hrm_test",db.queryForObject("SELECT DB_NAME()",String.class));
  mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();tag="POL-"+UUID.randomUUID().toString().substring(0,8);
  adminEmployee=employee("A");hrEmployee=employee("H");inside=employee("IN");outside=employee("OUT");
  admin=account(adminEmployee,"ADMIN","A");hr=account(hrEmployee,"HR","H");owner=account(inside,"EMPLOYEE","IN");
  long adminAccount=db.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,tag+"A");long hrAccount=db.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,tag+"H");
  departmentA=department("DA");departmentB=department("DB");db.update("INSERT dbo.positions(position_code,position_name) VALUES(?,?)",tag,tag);long position=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,tag);
  for(long employee:new long[]{hrEmployee,inside,outside})db.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from) VALUES(?,?,?,?)",employee,employee==outside?departmentB:departmentA,position,LocalDate.of(2020,1,1));
  db.update("INSERT dbo.hr_department_scopes(hr_account_id,department_id,effective_from,created_by) VALUES(?,?,?,?)",hrAccount,departmentA,LocalDateTime.of(2020,1,1,0,0),adminAccount);
  for(String role:new String[]{"ADMIN","HR"})db.update("INSERT dbo.role_permissions(role_id,permission_id) SELECT r.role_id,p.permission_id FROM dbo.roles r CROSS JOIN dbo.permissions p WHERE r.role_code=? AND p.permission_code='EMPLOYEE_MANAGE' AND NOT EXISTS(SELECT 1 FROM dbo.role_permissions x WHERE x.role_id=r.role_id AND x.permission_id=p.permission_id)",role);
 }
 String auth(String token){return "Bearer "+token;}
 long createdId(String path,String token,String body,String key)throws Exception {
  String response=mvc.perform(post(path).header("Authorization",auth(token)).contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  var json=tools.jackson.databind.json.JsonMapper.builder().build();
  return ((Number)json.readValue(response,java.util.Map.class).get(key)).longValue();
 }
 @Test void adminAndScopedHrCanSelectPrimaryAndKeepOnlyOnePrimary()throws Exception {
  long level=createdId("/qualification-levels",admin,"{\"levelCode\":\""+tag+"Q\",\"levelName\":\"Permission test qualification\",\"isActive\":true}","qualificationLevelId");
  String details="\"qualificationLevelId\":"+level+",\"major\":\"Test degree\",\"graduationYear\":2020";
  long first=createdId("/employees/"+inside+"/qualifications",admin,"{"+details+",\"isPrimary\":true}","employeeQualificationId");
  long second=createdId("/employees/"+inside+"/qualifications",hr,"{"+details+",\"isPrimary\":false}","employeeQualificationId");
  mvc.perform(put("/employee-qualifications/"+second+"/primary").header("Authorization",auth(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.isPrimary").value(true));
  assertEquals(second,db.queryForObject("SELECT employee_qualification_id FROM dbo.employee_qualifications WHERE employee_id=? AND is_primary=1",Long.class,inside));
  mvc.perform(put("/employee-qualifications/"+first+"/primary").header("Authorization",auth(hr))).andExpect(status().isOk()).andExpect(jsonPath("$.isPrimary").value(true));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.employee_qualifications WHERE employee_id=? AND is_primary=1",Integer.class,inside));
  long other=createdId("/employees/"+outside+"/qualifications",admin,"{"+details+"}","employeeQualificationId");
  mvc.perform(put("/employee-qualifications/"+other+"/primary").header("Authorization",auth(hr))).andExpect(status().isForbidden());
  mvc.perform(put("/employee-qualifications/"+second+"/primary").header("Authorization",auth(owner))).andExpect(status().isForbidden());
  mvc.perform(delete("/employee-qualifications/"+second).header("Authorization",auth(admin))).andExpect(status().isNoContent());
  mvc.perform(get("/employee-qualifications/"+second).header("Authorization",auth(admin))).andExpect(status().isNotFound());
  assertEquals(Boolean.TRUE,db.queryForObject("SELECT is_deleted FROM dbo.employee_qualifications WHERE employee_qualification_id=?",Boolean.class,second));
  assertTrue(db.queryForObject("SELECT COUNT(*) FROM dbo.audit_logs WHERE target_table='employee_qualifications' AND action='EMPLOYEE_QUALIFICATION_PRIMARY_CHANGE' AND target_record_key IN (?,?)",Integer.class,Long.toString(first),Long.toString(second))>=2);
 }
 @Test void adminCanTransferAcrossDepartmentsAndPreserveHistory()throws Exception {
  long position=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,tag);
  String date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
  mvc.perform(put("/employees/"+inside+"/transfer").header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON)
   .content("{\"departmentId\":"+departmentB+",\"positionId\":"+position+",\"effectiveFrom\":\""+date+"\",\"reason\":\"Approved department transfer\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.departmentId").value(departmentB));
  mvc.perform(get("/employees/"+inside+"/assignments").header("Authorization",auth(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.employee_assignments WHERE employee_id=? AND effective_to IS NULL",Integer.class,inside));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.audit_logs WHERE action='EMPLOYEE_TRANSFER' AND target_record_key=?",Integer.class,Long.toString(inside)));
  mvc.perform(get("/employees/"+inside+"/detail").header("Authorization",auth(hr))).andExpect(status().isForbidden());
 }
 @Test void scopedHrCanTransferButInvalidReasonAndFutureDateAreRejected()throws Exception {
  long position=createdId("/positions",admin,"{\"positionCode\":\""+tag+"NEW\",\"positionName\":\"Test new position\",\"isActive\":true}","positionId");
  String target="\"departmentId\":"+departmentA+",\"positionId\":"+position;
  LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
  mvc.perform(put("/employees/"+inside+"/transfer").header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content("{"+target+",\"effectiveFrom\":\""+today+"\",\"reason\":\"   \"}")).andExpect(status().isBadRequest());
  mvc.perform(put("/employees/"+inside+"/transfer").header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content("{"+target+",\"effectiveFrom\":\""+today.plusDays(1)+"\",\"reason\":\"Future date\"}")).andExpect(status().isBadRequest());
  mvc.perform(put("/employees/"+inside+"/transfer").header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content("{"+target+",\"effectiveFrom\":\""+today+"\",\"reason\":\"Change work position\"}")).andExpect(status().isOk());
 }
 @Test void adminCatalogCrudUsesSoftDeactivation()throws Exception {
  for(String[] c:new String[][]{{"departments","departmentCode","departmentName","departmentId"},{"positions","positionCode","positionName","positionId"},{"qualification-levels","levelCode","levelName","qualificationLevelId"},{"leave-types","typeCode","typeName","leaveTypeId"}}){
   String path="/"+c[0],body="{\""+c[1]+"\":\""+tag+"CAT\",\""+c[2]+"\":\"Test catalog\",\"isActive\":true,\"isPaid\":true}";
   long id=createdId(path,admin,body,c[3]);
   mvc.perform(put(path+"/"+id).header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON).content(body.replace("Test catalog","Updated catalog"))).andExpect(status().isOk());
   mvc.perform(get(path+"/"+id).header("Authorization",auth(admin))).andExpect(status().isOk()).andExpect(jsonPath("$."+c[2]).value("Updated catalog"));
   mvc.perform(delete(path+"/"+id).header("Authorization",auth(admin))).andExpect(status().is2xxSuccessful());
   mvc.perform(get(path+"/"+id).header("Authorization",auth(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
   mvc.perform(post(path).header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
  }
 }
 @Test void adminCanCreateUpdateAndTerminateWithoutDeletingHistory()throws Exception {
  long position=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,tag);
  String body="{\"employeeCode\":\""+tag+"NEWEMP\",\"fullName\":\"Test employee\",\"hireDate\":\"2020-01-01\",\"departmentId\":"+departmentA+",\"positionId\":"+position+"}";
  long employee=createdId("/employees",admin,body,"employeeId");
  mvc.perform(get("/employees/"+employee+"/detail").header("Authorization",auth(admin))).andExpect(status().isOk());
  mvc.perform(put("/employees/"+employee).header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Updated employee\",\"email\":\"test@example.test\"}")).andExpect(status().isOk());
  mvc.perform(patch("/employees/"+employee+"/contact").header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"0901234567\"}")).andExpect(status().isOk());
  mvc.perform(put("/employees/"+employee).header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/employees/"+employee+"/terminate").header("Authorization",auth(admin))).andExpect(status().isNoContent());
  assertEquals("DA_NGHI",db.queryForObject("SELECT employment_status FROM dbo.employees WHERE employee_id=?",String.class,employee));
  mvc.perform(get("/employees/"+employee+"/assignments").header("Authorization",auth(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get("/g1/audit").param("targetTable","employees").param("recordId",Long.toString(employee)).header("Authorization",auth(admin))).andExpect(status().isOk());
 }
 @Test void entitlementCrudRejectsOverlapZeroHoursAndEmptyPeriods()throws Exception {
  long type=createdId("/leave-types",admin,"{\"typeCode\":\""+tag+"LEAVE\",\"typeName\":\"Test leave\",\"isPaid\":true,\"isActive\":true}","leaveTypeId");
  String body="{\"employeeId\":"+inside+",\"leaveTypeId\":"+type+",\"periodStart\":\"2027-01-01\",\"periodEnd\":\"2028-01-01\",\"entitledHours\":8}";
  long id=createdId("/leave-entitlements",hr,body,"entitlementId");
  mvc.perform(get("/leave-entitlements/"+id).header("Authorization",auth(admin))).andExpect(status().isOk());
  mvc.perform(post("/leave-entitlements").header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
  mvc.perform(put("/leave-entitlements/"+id).header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"entitledHours\":8","\"entitledHours\":16"))).andExpect(status().isOk()).andExpect(jsonPath("$.entitledHours").value(16));
  mvc.perform(post("/leave-entitlements").header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON).content(body.replace("2028-01-01","2027-01-01"))).andExpect(status().isBadRequest());
  mvc.perform(put("/leave-entitlements/"+id).header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"entitledHours\":8","\"entitledHours\":0"))).andExpect(status().isBadRequest());
  mvc.perform(get("/employee-requests/balances/"+inside).header("Authorization",auth(owner))).andExpect(status().isOk());
  mvc.perform(get("/leave-entitlements").param("employeeId",Long.toString(outside)).header("Authorization",auth(hr))).andExpect(status().isForbidden());
 }
 @Test void adminCannotReadOthersPrivateFields()throws Exception{String body=mvc.perform(get("/employees/"+inside+"/detail").header("Authorization",auth(admin))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(body.contains("PRIVATE_ID"));assertFalse(body.contains("PRIVATE_HOME"));}
 @Test void employeeCanReadOwnPrivateFieldsButNotAnotherEmployee()throws Exception{mvc.perform(get("/employees/"+inside+"/detail").header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.identityNumber").value("PRIVATE_ID"));mvc.perform(get("/employees/"+outside+"/detail").header("Authorization",auth(owner))).andExpect(status().isForbidden());}
 @Test void hrSearchIsScopedBeforeCountingAndPaging()throws Exception{String body=mvc.perform(get("/employees/search").param("keyword",tag).param("page","0").param("size","10").header("Authorization",auth(hr))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andReturn().getResponse().getContentAsString();assertTrue(body.contains(tag+"IN"));assertFalse(body.contains(tag+"OUT"));}
 @Test void hrCannotReadOrUpdateOutsideScope()throws Exception{mvc.perform(get("/employees/"+outside+"/detail").header("Authorization",auth(hr))).andExpect(status().isForbidden());mvc.perform(put("/employees/"+outside).header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Blocked update\"}")).andExpect(status().isForbidden());assertEquals(tag+"OUT",db.queryForObject("SELECT full_name FROM dbo.employees WHERE employee_id=?",String.class,outside));}
 @Test void publicContactPatchPreservesPrivateFields()throws Exception{mvc.perform(patch("/employees/"+inside+"/contact").header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"fixture@example.test\"}")).andExpect(status().isOk());assertEquals("PRIVATE_ID",db.queryForObject("SELECT identity_number FROM dbo.employees WHERE employee_id=?",String.class,inside));assertEquals("PRIVATE_HOME",db.queryForObject("SELECT address FROM dbo.employees WHERE employee_id=?",String.class,inside));}
 @Test void adminAndHrCannotEditOwnContacts()throws Exception{mvc.perform(patch("/employees/"+adminEmployee+"/contact").header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"self@example.test\"}")).andExpect(status().isForbidden());mvc.perform(patch("/employees/"+hrEmployee+"/contact").header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"self@example.test\"}")).andExpect(status().isForbidden());}
 @Test void profileUpdateDoesNotEraseHiddenIdentity()throws Exception{mvc.perform(put("/employees/"+inside).header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Allowed update\"}")).andExpect(status().isOk());assertEquals("PRIVATE_ID",db.queryForObject("SELECT identity_number FROM dbo.employees WHERE employee_id=?",String.class,inside));}
 @Test void auditCannotLeakPrivateFields()throws Exception{long actor=db.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,tag+"A");db.update("INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,before_data_json,after_data_json) VALUES(?,'TEST_PRIVATE','employees',?,?,?)",actor,Long.toString(inside),"{\"address\":\"PRIVATE_HOME\",\"identityNumber\":\"PRIVATE_ID\"}","{}");String body=mvc.perform(get("/g1/audit").param("targetTable","employees").param("recordId",Long.toString(inside)).header("Authorization",auth(admin))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(body.contains("PRIVATE_ID"));assertFalse(body.contains("PRIVATE_HOME"));assertTrue(body.contains("REDACTED"));}
 @Test void hrCannotMoveEmployeeToUnassignedDepartment()throws Exception{
  long position=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,tag);
  mvc.perform(put("/employees/"+inside+"/transfer").header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON)
   .content("{\"departmentId\":"+departmentB+",\"positionId\":"+position+",\"effectiveFrom\":\"2026-10-31\",\"reason\":\"Outside scope\"}")).andExpect(status().isForbidden());
 }
 @Test void hrCannotReadOutsideDepartmentOrAudit()throws Exception{
  mvc.perform(get("/departments/"+departmentB).header("Authorization",auth(hr))).andExpect(status().isForbidden());
  mvc.perform(get("/g1/audit").param("targetTable","employees").param("recordId",Long.toString(outside)).header("Authorization",auth(hr))).andExpect(status().isForbidden());
 }
 @Test void managerCannotWriteAnotherEmployeesPrivateFields()throws Exception{
  mvc.perform(patch("/employees/"+inside+"/contact").header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON)
   .content("{\"address\":\"PRIVATE_NEW\"}")).andExpect(status().isForbidden());
  mvc.perform(put("/employees/"+inside).header("Authorization",auth(hr)).contentType(MediaType.APPLICATION_JSON)
   .content("{\"fullName\":\"Blocked private update\",\"identityNumber\":\"PRIVATE_NEW\"}")).andExpect(status().isForbidden());
  assertEquals("PRIVATE_ID",db.queryForObject("SELECT identity_number FROM dbo.employees WHERE employee_id=?",String.class,inside));
 }
 @Test void ownProfileUpdateCannotChangeContactOrEraseHiddenIdentity()throws Exception{
  mvc.perform(put("/employees/"+adminEmployee).header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON)
   .content("{\"fullName\":\"Allowed work update\"}")).andExpect(status().isOk());
  assertEquals("PRIVATE_ID",db.queryForObject("SELECT identity_number FROM dbo.employees WHERE employee_id=?",String.class,adminEmployee));
  for(String contact:new String[]{"\"identityNumber\":\"CHANGED\"","\"email\":\"self@example.test\""})
   mvc.perform(put("/employees/"+adminEmployee).header("Authorization",auth(admin)).contentType(MediaType.APPLICATION_JSON)
    .content("{\"fullName\":\"Blocked contact update\","+contact+"}")).andExpect(status().isForbidden());
 }
 @Test void oversizedRecordIdReturnsBadRequest()throws Exception{
  mvc.perform(get("/employees/999999999999999999999999/detail").header("Authorization",auth(admin))).andExpect(status().isBadRequest());
 }
 @Test void anonymousCannotReadPersonnel()throws Exception{mvc.perform(get("/employees/"+inside+"/detail")).andExpect(status().isUnauthorized());}
}
