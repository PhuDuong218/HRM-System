package com.hrm.service;

import java.util.*;
import com.hrm.dto.AuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Compatibility facade used by existing employee services; shared writer enforces the audit contract. */
@Service
public class EmployeeAuditService {
    private final AuditLogService audit;
    public EmployeeAuditService(AuditLogService audit){this.audit=audit;}
    private void change(String action,String table,Long actor,Long id,Map<String,String> before,Map<String,String> after) {
        if(actor==null||actor<=0||id==null||id<=0)throw new IllegalArgumentException("Phải xác định tài khoản và đối tượng thực hiện");
        Objects.requireNonNull(before);Objects.requireNonNull(after);
        if(before.equals(after))return;
        audit.record(new AuditEvent(actor,action,table,id.toString(),null,before,after));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordProfileUpdate(Long actor,Long id,Map<String,String> before,Map<String,String> after){change("EMPLOYEE_PROFILE_UPDATE","employees",actor,id,before,after);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordTransfer(Long actor,Long id,Map<String,String> before,Map<String,String> after){change("EMPLOYEE_TRANSFER","employees",actor,id,before,after);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordTransferWithReason(Long actor,Long id,Map<String,String> before,Map<String,String> after,String reason){
        audit.record(new AuditEvent(actor,"EMPLOYEE_TRANSFER","employees",id.toString(),reason,before,after));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordContactUpdate(Long actor,Long id,Map<String,String> before,Map<String,String> after){change("EMPLOYEE_CONTACT_UPDATE","employees",actor,id,before,after);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordTermination(Long actor,Long id,Map<String,String> before,Map<String,String> after){change("EMPLOYEE_TERMINATE","employees",actor,id,before,after);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordCreation(Long actor,Long id,Map<String,String> after){change("EMPLOYEE_CREATE","employees",actor,id,Map.of(),after);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordCatalog(String table,String action,Long actor,Long id,Map<String,String> before,Map<String,String> after){
        if(!("departments".equals(table)&&action.matches("DEPARTMENT_(CREATE|UPDATE|DEACTIVATE)"))&&!("positions".equals(table)&&action.matches("POSITION_(CREATE|UPDATE|DEACTIVATE)")))throw new IllegalArgumentException("Invalid catalog audit");
        change(action,table,actor,id,before,after);
    }
}
