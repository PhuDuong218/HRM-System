package com.hrm.service;

import java.util.LinkedHashMap;
import java.util.Map;
import com.hrm.dto.*;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Management writes: business changes, actor metadata and audit share one transaction. */
@Service
public class CatalogAuditService {
    private final DepartmentService departments;
    private final PositionService positions;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final EmployeeAuditService audit;
    private final AssignmentGuardService guard;

    public CatalogAuditService(DepartmentService departments, PositionService positions,
            DepartmentRepository departmentRepository, PositionRepository positionRepository,
            EmployeeAuditService audit, AssignmentGuardService guard) {
        this.departments=departments; this.positions=positions;
        this.departmentRepository=departmentRepository; this.positionRepository=positionRepository;
        this.audit=audit; this.guard=guard;
    }

    private void requireActor(Long actor) {
        if (actor == null || actor <= 0) throw new IllegalArgumentException("Thiếu tài khoản thực hiện");
    }

    @Transactional
    public DepartmentDTO createDepartment(DepartmentRequestDTO request, Long actor) {
        requireActor(actor);
        var result=departments.addDepartment(request);
        var entity=departmentRepository.findById(result.getDepartmentId()).orElseThrow();
        entity.setCreatedBy(actor);
        departmentRepository.flush();
        audit.recordCatalog("departments","DEPARTMENT_CREATE",actor,result.getDepartmentId(),Map.of(),snapshot(result));
        return result;
    }

    @Transactional
    public DepartmentDTO updateDepartment(Long id, DepartmentRequestDTO request, Long actor) {
        requireActor(actor); guard.lockTimeline();
        var before=snapshot(departments.getDepartment(id));
        var entity=departmentRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy phòng ban: "+id));
        entity.setUpdatedBy(actor);
        var result=departments.updateDepartment(id,request);
        audit.recordCatalog("departments","DEPARTMENT_UPDATE",actor,id,before,snapshot(result));
        return result;
    }

    @Transactional
    public void deactivateDepartment(Long id, Long actor) {
        requireActor(actor); guard.lockTimeline();
        var before=snapshot(departments.getDepartment(id));
        var entity=departmentRepository.findById(id).orElseThrow();
        entity.setUpdatedBy(actor);
        departments.deleteDepartment(id);
        audit.recordCatalog("departments","DEPARTMENT_DEACTIVATE",actor,id,before,snapshot(departments.getDepartment(id)));
    }

    @Transactional
    public PositionDTO createPosition(PositionRequestDTO request, Long actor) {
        requireActor(actor);
        var result=positions.addPosition(request);
        var entity=positionRepository.findById(result.getPositionId()).orElseThrow();
        entity.setCreatedBy(actor);
        positionRepository.flush();
        audit.recordCatalog("positions","POSITION_CREATE",actor,result.getPositionId(),Map.of(),snapshot(result));
        return result;
    }

    @Transactional
    public PositionDTO updatePosition(Long id, PositionRequestDTO request, Long actor) {
        requireActor(actor); guard.lockTimeline();
        var before=snapshot(positions.getPositionById(id));
        var entity=positionRepository.findById(id).orElseThrow();
        entity.setUpdatedBy(actor);
        var result=positions.updatePosition(id,request);
        positionRepository.flush();
        audit.recordCatalog("positions","POSITION_UPDATE",actor,id,before,snapshot(result));
        return result;
    }

    @Transactional
    public void deactivatePosition(Long id, Long actor) {
        requireActor(actor); guard.lockTimeline();
        var before=snapshot(positions.getPositionById(id));
        var entity=positionRepository.findById(id).orElseThrow();
        entity.setUpdatedBy(actor);
        positions.deletePosition(id);
        positionRepository.flush();
        audit.recordCatalog("positions","POSITION_DEACTIVATE",actor,id,before,snapshot(positions.getPositionById(id)));
    }

    private Map<String,String> snapshot(DepartmentDTO d) {
        Map<String,String> data=new LinkedHashMap<>();
        data.put("departmentCode",d.getDepartmentCode()); data.put("departmentName",d.getDepartmentName());
        data.put("description",d.getDescription()); data.put("isActive",String.valueOf(d.getIsActive()));
        return data;
    }

    private Map<String,String> snapshot(PositionDTO p) {
        Map<String,String> data=new LinkedHashMap<>();
        data.put("positionCode",p.getPositionCode()); data.put("positionName",p.getPositionName());
        data.put("description",p.getDescription()); data.put("isActive",String.valueOf(p.getIsActive()));
        return data;
    }
}
