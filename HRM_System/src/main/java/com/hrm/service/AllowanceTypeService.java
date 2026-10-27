package com.hrm.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import com.hrm.dto.*;
import com.hrm.exception.BusinessException;
import com.hrm.model.AllowanceType;
import com.hrm.repository.AllowanceTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class AllowanceTypeService {
    private final AllowanceTypeRepository types;
    private final BusinessAuditService audit;
    private final Clock clock;
    public AllowanceTypeService(AllowanceTypeRepository types,BusinessAuditService audit,Clock clock) {
        this.types=types; this.audit=audit; this.clock=clock;
    }
    public List<AllowanceTypeResponse> list() { return types.findAll().stream().map(this::dto).toList(); }
    public AllowanceTypeResponse get(Long id) { return dto(require(id)); }

    @Transactional
    public AllowanceTypeResponse create(AllowanceTypeRequest r) {
        if (types.existsByTypeCode(r.typeCode().trim())) throw conflict("Mã phụ cấp đã tồn tại");
        var t=new AllowanceType();
        t.setTypeCode(r.typeCode().trim()); t.setTypeName(r.typeName().trim());
        t.setDescription(r.description()); t.setIsActive(true); t.setCreatedAt(LocalDateTime.now(clock));
        types.saveAndFlush(t);
        var after=dto(t);
        audit.record("allowance_types",t.getAllowanceTypeId(),"ALLOWANCE_TYPE_CREATE",r.reason().trim(),null,after);
        return after;
    }
    @Transactional
    public AllowanceTypeResponse update(Long id,AllowanceTypeRequest r) {
        var t=lock(id);
        if (!t.getTypeCode().equals(r.typeCode().trim())) throw conflict("Không đổi mã loại sau tạo");
        var before=dto(t);
        t.setTypeName(r.typeName().trim()); t.setDescription(r.description());
        if (r.isActive()!=null) t.setIsActive(r.isActive());
        t.setUpdatedAt(LocalDateTime.now(clock)); types.saveAndFlush(t);
        var after=dto(t);
        audit.record("allowance_types",id,"ALLOWANCE_TYPE_UPDATE",r.reason().trim(),before,after);
        return after;
    }
    @Transactional
    public void deactivate(Long id,String reason) {
        if (reason==null || reason.isBlank() || reason.length()>500) throw conflict("Cần lý do <=500 ký tự");
        var t=lock(id); var before=dto(t);
        t.setIsActive(false); t.setUpdatedAt(LocalDateTime.now(clock)); types.saveAndFlush(t);
        audit.record("allowance_types",id,"ALLOWANCE_TYPE_DEACTIVATE",reason.trim(),before,dto(t));
    }
    private AllowanceType require(Long id) {
        return types.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không có loại phụ cấp"));
    }
    private AllowanceType lock(Long id) {
        return types.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không có loại phụ cấp"));
    }
    private AllowanceTypeResponse dto(AllowanceType t) {
        return new AllowanceTypeResponse(t.getAllowanceTypeId(),t.getTypeCode(),t.getTypeName(),t.getDescription(),t.getIsActive());
    }
    private BusinessException conflict(String m) { return new BusinessException(HttpStatus.CONFLICT,m); }
}