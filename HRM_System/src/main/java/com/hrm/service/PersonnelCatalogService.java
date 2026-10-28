package com.hrm.service;

import java.security.Principal;
import java.util.*;
import java.util.function.*;
import com.hrm.model.*;
import jakarta.persistence.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps legacy DTOs/routes while adding authorization, actor metadata, transaction locks and audit. */
@Service
public class PersonnelCatalogService {
    private record Target(String key,Class<? extends BaseEntity> entity,String columns) {}
    private static final Map<String,Target> TARGETS=Map.of(
        "qualification_levels",new Target("qualification_level_id",QualificationLevel.class,"level_code,level_name,description,is_active"),
        "employee_qualifications",new Target("employee_qualification_id",EmployeeQualification.class,"employee_id,qualification_level_id,major,institution,graduation_year,certificate_number,is_primary,is_deleted"),
        "leave_types",new Target("leave_type_id",LeaveType.class,"type_code,type_name,is_paid,is_active"),
        "leave_entitlements",new Target("entitlement_id",LeaveEntitlement.class,"employee_id,leave_type_id,CONVERT(varchar(10),period_start,23) AS period_start,CONVERT(varchar(10),period_end,23) AS period_end,entitled_hours")
    );
    private final JdbcTemplate jdbc;
    private final PersonnelAccessService access;
    private final PersonnelWorkflowSupport support;
    private final EmployeeRequestService requests;
    @PersistenceContext private EntityManager em;
    public PersonnelCatalogService(JdbcTemplate jdbc,PersonnelAccessService access,PersonnelWorkflowSupport support,EmployeeRequestService requests) {
        this.jdbc=jdbc;this.access=access;this.support=support;this.requests=requests;
    }
    private Object snapshot(String table,long id) {
        Target t=TARGETS.get(table);
        var rows=jdbc.queryForList("SELECT "+t.columns()+" FROM dbo."+table+" WHERE "+t.key()+"=?",id);
        return rows.isEmpty()?null:rows.get(0);
    }
    @Transactional
    public <T> T qualification(Principal p,Long employeeId,Long id,String action,Supplier<T> operation,ToLongFunction<T> identify) {
        var actor=access.manager(p);support.lock();
        Long owner=employeeId;
        if(owner==null) {
            var ids=jdbc.queryForList("SELECT employee_id FROM dbo.employee_qualifications WHERE employee_qualification_id=?",Long.class,id);
            if(ids.isEmpty()) throw new com.hrm.exception.ResourceNotFoundException("Không tìm thấy bằng cấp");
            owner=ids.get(0);
        }
        var before=jdbc.queryForList("SELECT employee_qualification_id FROM dbo.employee_qualifications WHERE employee_id=? AND is_primary=1",Long.class,owner);
        T result=write(p,"employee_qualifications",id,action,operation,identify);
        var after=jdbc.queryForList("SELECT employee_qualification_id FROM dbo.employee_qualifications WHERE employee_id=? AND is_primary=1",Long.class,owner);
        if(!before.equals(after)) {
            long key=id==null?identify.applyAsLong(result):id;
            for(Long old:before) if(old!=key) {
                EmployeeQualification previous=em.find(EmployeeQualification.class,old);
                previous.setUpdatedBy(actor.accountId());previous.setUpdatedAt(support.utc());
            }
            em.flush();
            support.audit(actor.accountId(),"EMPLOYEE_QUALIFICATION_PRIMARY_CHANGE","employee_qualifications",key,null,
                Map.of("employeeId",owner,"primaryQualificationIds",before),Map.of("employeeId",owner,"primaryQualificationIds",after));
        }
        return result;
    }
    @Transactional
    public <T> T write(Principal p,String table,Long id,String action,Supplier<T> operation,ToLongFunction<T> identify) {
        var actor=access.manager(p);support.lock();
        Target target=TARGETS.get(table);if(target==null) throw new IllegalArgumentException("Unsupported G2 target");
        Object before=id==null?null:snapshot(table,id);
        T result=operation.get();long key=id==null?identify.applyAsLong(result):id;
        BaseEntity entity=em.find(target.entity(),key);
        if(id==null) {entity.setCreatedBy(actor.accountId());entity.setCreatedAt(support.utc());}
        else {entity.setUpdatedBy(actor.accountId());entity.setUpdatedAt(support.utc());}
        if(entity instanceof EmployeeQualification q&&Boolean.TRUE.equals(q.getIsDeleted())) {q.setDeletedBy(actor.accountId());q.setDeletedAt(support.utc());}
        if(entity instanceof LeaveEntitlement limit) {
            if(requests.used(key).compareTo(limit.getEntitledHours())>0) throw PersonnelWorkflowSupport.conflict("Không thể giảm hạn mức dưới số giờ đã duyệt");
            long outside=jdbc.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.g2_leave_allocations a JOIN dbo.work_schedules s ON s.schedule_id=a.schedule_id
                WHERE a.entitlement_id=? AND (s.work_date<? OR s.work_date>=?)
                """,Long.class,key,limit.getPeriodStart(),limit.getPeriodEnd());
            if(outside>0) throw PersonnelWorkflowSupport.conflict("Không thể đổi kỳ làm mất phạm vi phép đã duyệt");
        }
        em.flush();Object after=snapshot(table,key);
        support.audit(actor.accountId(),action,table,key,null,before,after);return result;
    }
}
