package com.hrm.service;

import com.hrm.dto.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Additive metadata; old databases remain readable until the migration is applied. */
@Service
public class QualificationMetadata {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 private final JdbcTemplate db;private final PersonnelWorkflowSupport support;
 private volatile boolean metadataColumnsReady = false;
 private record QualDates(LocalDate issued, LocalDate expiry) {}
 private record Snapshot(Map<Long, QualDates> datesByQualId, Map<Long, Integer> rankByLevelId, Map<Long, Integer> maxRankByEmpId, long loadedAt) {}
 private volatile Snapshot cachedSnapshot = null;
 public QualificationMetadata(JdbcTemplate db,PersonnelWorkflowSupport support){this.db=db;this.support=support;}
 private boolean available(){
  if(metadataColumnsReady)return true;
  Integer v=db.queryForObject("SELECT CASE WHEN COL_LENGTH('dbo.employee_qualifications','issued_date') IS NOT NULL AND COL_LENGTH('dbo.qualification_levels','rank_order') IS NOT NULL THEN 1 ELSE 0 END",Integer.class);
  boolean found=v!=null&&v==1;
  if(found)metadataColumnsReady=true;
  return found;
 }
 private Snapshot snapshot() {
  long now = System.currentTimeMillis();
  Snapshot s = cachedSnapshot;
  if (s != null && now - s.loadedAt() < 60_000L) return s;
  Map<Long, QualDates> dates = new HashMap<>();
  db.query("SELECT employee_qualification_id, issued_date, expiry_date FROM dbo.employee_qualifications", rs -> {
   java.sql.Date iss = rs.getDate(2);
   java.sql.Date exp = rs.getDate(3);
   dates.put(rs.getLong(1), new QualDates(iss == null ? null : iss.toLocalDate(), exp == null ? null : exp.toLocalDate()));
  });
  Map<Long, Integer> ranks = new HashMap<>();
  db.query("SELECT qualification_level_id, rank_order FROM dbo.qualification_levels", rs -> {
   int r = rs.getInt(2);
   ranks.put(rs.getLong(1), rs.wasNull() ? null : r);
  });
  Map<Long, Integer> maxRanks = new HashMap<>();
  db.query("SELECT q.employee_id, MAX(l.rank_order) FROM dbo.employee_qualifications q JOIN dbo.qualification_levels l ON l.qualification_level_id=q.qualification_level_id WHERE COALESCE(q.is_deleted,0)=0 AND (q.expiry_date IS NULL OR q.expiry_date>=?) GROUP BY q.employee_id", rs -> {
   int m = rs.getInt(2);
   if (!rs.wasNull()) maxRanks.put(rs.getLong(1), m);
  }, support.today());
  Snapshot next = new Snapshot(dates, ranks, maxRanks, now);
  cachedSnapshot = next;
  return next;
 }
 public void validate(EmployeeQualificationRequestDTO r){
  if(r.getIssuedDate()!=null&&r.getIssuedDate().isAfter(support.today()))throw PersonnelWorkflowSupport.bad("Ngày cấp không được ở tương lai");
  if(r.getExpiryDate()!=null&&(r.getIssuedDate()==null||r.getExpiryDate().isBefore(r.getIssuedDate())))throw PersonnelWorkflowSupport.bad("Ngày hết hạn phải từ ngày cấp trở đi");
 }
 public void save(long id,EmployeeQualificationRequestDTO r){
  validate(r);if(!r.hasValidityDates())return;
  if(!available()){if(r.getIssuedDate()!=null||r.getExpiryDate()!=null)throw PersonnelWorkflowSupport.conflict("Cần migration 007 để lưu thời hạn bằng cấp");return;}
  db.update("UPDATE dbo.employee_qualifications SET issued_date=?,expiry_date=? WHERE employee_qualification_id=?",r.getIssuedDate(),r.getExpiryDate(),id);
  cachedSnapshot = null;
  var entity=em.find(com.hrm.model.EmployeeQualification.class,id);if(entity!=null)em.refresh(entity);
 }
 public void rank(long level,Integer rank){if(rank==null)return;if(rank<0||rank>1000)throw PersonnelWorkflowSupport.bad("Thứ bậc phải từ 0 đến 1000");if(!available())throw PersonnelWorkflowSupport.conflict("Cần migration 007 để xếp thứ bậc trình độ");db.update("UPDATE dbo.qualification_levels SET rank_order=? WHERE qualification_level_id=?",rank,level);cachedSnapshot=null;var entity=em.find(com.hrm.model.QualificationLevel.class,level);if(entity!=null)em.refresh(entity);}
 public Integer rank(long level){return available()?snapshot().rankByLevelId().get(level):null;}
 public EmployeeQualificationDTO enrich(EmployeeQualificationDTO q){
  if(!available())return q;
  Snapshot snap = snapshot();
  QualDates d = snap.datesByQualId().get(q.employeeQualificationId());
  LocalDate issued = d == null ? null : d.issued();
  LocalDate expiry = d == null ? null : d.expiry();
  Integer rank = snap.rankByLevelId().get(q.qualificationLevelId());
  Integer max = snap.maxRankByEmpId().get(q.employeeId());
  String status=expiry==null?"NO_EXPIRY":expiry.isBefore(support.today())?"EXPIRED":expiry.isAfter(support.today().plusDays(30))?"VALID":"EXPIRING";
  return new EmployeeQualificationDTO(q.employeeQualificationId(),q.employeeId(),q.qualificationLevelId(),q.levelCode(),q.levelName(),q.major(),q.institution(),q.graduationYear(),q.certificateNumber(),q.isPrimary(),issued,expiry,status,rank,rank!=null&&rank.equals(max)&&!"EXPIRED".equals(status));
 }
}
