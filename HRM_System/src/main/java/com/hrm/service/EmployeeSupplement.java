package com.hrm.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class EmployeeSupplement {
 private final JdbcTemplate db;private final PersonnelWorkflowSupport support;
 private volatile boolean genderColumnReady = false;
 private volatile Map<Long, String> genderCache = Map.of();
 private volatile long genderCacheAt = 0L;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 public EmployeeSupplement(JdbcTemplate db,PersonnelWorkflowSupport support){this.db=db;this.support=support;}
 private boolean exists(){
  if(genderColumnReady)return true;
  Integer v=db.queryForObject("SELECT CASE WHEN COL_LENGTH('dbo.employees','gender') IS NULL THEN 0 ELSE 1 END",Integer.class);
  boolean found=v!=null&&v==1;
  if(found)genderColumnReady=true;
  return found;
 }
 private Map<Long, String> loadGenderCache() {
  long now = System.currentTimeMillis();
  Map<Long, String> current = genderCache;
  if (now - genderCacheAt < 60_000L && !current.isEmpty()) {
   return current;
  }
  Map<Long, String> loaded = new HashMap<>();
  db.query("SELECT employee_id, gender FROM dbo.employees", rs -> {
   loaded.put(rs.getLong(1), rs.getString(2));
  });
  genderCache = loaded;
  genderCacheAt = now;
  return loaded;
 }
 public String get(long employee){return exists()?loadGenderCache().get(employee):null;}
 public void save(long employee,String gender,long actor,String reason){
  save(employee,gender,actor,reason,true);
 }
 /** Profile writer includes this change in its single before/after audit event. */
 public void saveProfile(long employee,String gender,long actor,String reason){
  save(employee,gender,actor,reason,false);
 }
 private void save(long employee,String gender,long actor,String reason,boolean recordAudit){
  if(gender==null||gender.isBlank())throw PersonnelWorkflowSupport.bad("Vui lòng chọn giới tính");
  if(!Set.of("MALE","FEMALE","OTHER","UNDISCLOSED").contains(gender))throw PersonnelWorkflowSupport.bad("Giới tính chưa hợp lệ");
  if(!exists())throw PersonnelWorkflowSupport.conflict("Cần migration 011 để lưu thông tin giới tính");
  String before=get(employee);if(Objects.equals(before,gender))return;
  db.update("UPDATE dbo.employees SET gender=? WHERE employee_id=?",gender,employee);
  genderCacheAt=0L;
  var entity=em.find(com.hrm.model.Employee.class,employee);if(entity!=null)em.refresh(entity);
  if(recordAudit)support.audit(actor,"EMPLOYEE_SUPPLEMENT_UPDATE","employees",employee,reason,Collections.singletonMap("gender",before),Map.of("gender",gender));
 }
}
