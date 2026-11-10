package com.hrm.service;

import com.hrm.dto.*;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Additive metadata; old databases remain readable until the migration is applied. */
@Service
public class QualificationMetadata {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 private final JdbcTemplate db;private final PersonnelWorkflowSupport support;
 public QualificationMetadata(JdbcTemplate db,PersonnelWorkflowSupport support){this.db=db;this.support=support;}
 private boolean available(){return db.queryForObject("SELECT CASE WHEN COL_LENGTH('dbo.employee_qualifications','issued_date') IS NOT NULL AND COL_LENGTH('dbo.qualification_levels','rank_order') IS NOT NULL THEN 1 ELSE 0 END",Integer.class)==1;}
 public void validate(EmployeeQualificationRequestDTO r){
  if(r.getIssuedDate()!=null&&r.getIssuedDate().isAfter(support.today()))throw PersonnelWorkflowSupport.bad("Ngày cấp không được ở tương lai");
  if(r.getExpiryDate()!=null&&(r.getIssuedDate()==null||r.getExpiryDate().isBefore(r.getIssuedDate())))throw PersonnelWorkflowSupport.bad("Ngày hết hạn phải từ ngày cấp trở đi");
 }
 public void save(long id,EmployeeQualificationRequestDTO r){
  validate(r);if(!r.hasValidityDates())return;
  if(!available()){if(r.getIssuedDate()!=null||r.getExpiryDate()!=null)throw PersonnelWorkflowSupport.conflict("Cần migration 007 để lưu thời hạn bằng cấp");return;}
  db.update("UPDATE dbo.employee_qualifications SET issued_date=?,expiry_date=? WHERE employee_qualification_id=?",r.getIssuedDate(),r.getExpiryDate(),id);
  var entity=em.find(com.hrm.model.EmployeeQualification.class,id);if(entity!=null)em.refresh(entity);
 }
 public void rank(long level,Integer rank){if(rank==null)return;if(rank<0||rank>1000)throw PersonnelWorkflowSupport.bad("Thứ bậc phải từ 0 đến 1000");if(!available())throw PersonnelWorkflowSupport.conflict("Cần migration 007 để xếp thứ bậc trình độ");db.update("UPDATE dbo.qualification_levels SET rank_order=? WHERE qualification_level_id=?",rank,level);var entity=em.find(com.hrm.model.QualificationLevel.class,level);if(entity!=null)em.refresh(entity);}
 public Integer rank(long level){return available()?db.queryForObject("SELECT rank_order FROM dbo.qualification_levels WHERE qualification_level_id=?",Integer.class,level):null;}
 public EmployeeQualificationDTO enrich(EmployeeQualificationDTO q){
  if(!available())return q;
  var dates=db.queryForMap("SELECT issued_date,expiry_date FROM dbo.employee_qualifications WHERE employee_qualification_id=?",q.employeeQualificationId());
  LocalDate issued=dates.get("issued_date")==null?null:((java.sql.Date)dates.get("issued_date")).toLocalDate();
  LocalDate expiry=dates.get("expiry_date")==null?null:((java.sql.Date)dates.get("expiry_date")).toLocalDate();
  Integer rank=rank(q.qualificationLevelId());
  Integer max=db.queryForObject("SELECT MAX(l.rank_order) FROM dbo.employee_qualifications q JOIN dbo.qualification_levels l ON l.qualification_level_id=q.qualification_level_id WHERE q.employee_id=? AND COALESCE(q.is_deleted,0)=0 AND (q.expiry_date IS NULL OR q.expiry_date>=?)",Integer.class,q.employeeId(),support.today());
  String status=expiry==null?"NO_EXPIRY":expiry.isBefore(support.today())?"EXPIRED":expiry.isAfter(support.today().plusDays(30))?"VALID":"EXPIRING";
  return new EmployeeQualificationDTO(q.employeeQualificationId(),q.employeeId(),q.qualificationLevelId(),q.levelCode(),q.levelName(),q.major(),q.institution(),q.graduationYear(),q.certificateNumber(),q.isPrimary(),issued,expiry,status,rank,rank!=null&&rank.equals(max)&&!"EXPIRED".equals(status));
 }
}
