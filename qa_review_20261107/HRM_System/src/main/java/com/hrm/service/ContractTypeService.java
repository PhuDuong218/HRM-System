package com.hrm.service;

import java.time.LocalDateTime;
import java.util.List;
import com.hrm.dto.ContractTypeRequest;
import com.hrm.dto.ContractTypeResponse;
import com.hrm.exception.BusinessException;
import com.hrm.model.ContractType;
import com.hrm.repository.ContractTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContractTypeService {
    private final ContractTypeRepository repository;
    private final BusinessAuditService audit;
    private final java.time.Clock clock;

    public ContractTypeService(ContractTypeRepository repository, BusinessAuditService audit, java.time.Clock clock) {
        this.repository = repository;
        this.audit=audit; this.clock=clock;
    }

    public List<ContractTypeResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public ContractTypeResponse getOne(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ContractTypeResponse create(ContractTypeRequest request) {
        validate(request);
        if(request.termKind()==com.hrm.enums.ContractTermKind.CTV_CAN_RA_SOAT)
            throw new BusinessException(HttpStatus.CONFLICT,"CTV_CAN_RA_SOAT chỉ giữ metadata hồ sơ cũ; cần phân loại nghiệp vụ trước khi tạo mới");
        String code = request.typeCode().trim();
        if (repository.existsByTypeCode(code)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Mã loại hợp đồng đã tồn tại");
        }
        ContractType entity = new ContractType();
        entity.setTypeCode(code);
        entity.setTermKind(request.termKind());
        entity.setMaxMonths(request.maxMonths());
        entity.setTypeName(request.typeName().trim());
        entity.setIsActive(true);
        entity.setCreatedAt(LocalDateTime.now(clock));
        var result=toResponse(repository.saveAndFlush(entity));
        audit.record("contract_types",result.contractTypeId(),"CONTRACT_TYPE_CREATE",request.reason().trim(),null,result);
        return result;
    }

    @Transactional
    public ContractTypeResponse update(Long id, ContractTypeRequest request) {
        ContractType entity = require(id);
        var before=toResponse(entity);
        if (!entity.getTypeCode().equals(request.typeCode().trim())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Không đổi mã loại sau khi tạo");
        }
        validate(request);
        if (entity.getTermKind()!=request.termKind() || !java.util.Objects.equals(entity.getMaxMonths(),request.maxMonths()))
            throw new BusinessException(HttpStatus.CONFLICT,"Không đổi quy tắc loại đã tạo; tạo loại mới để giữ lịch sử");
        entity.setTypeName(request.typeName().trim());
        if (request.isActive() != null) entity.setIsActive(request.isActive());
        entity.setUpdatedAt(LocalDateTime.now(clock));
        var result=toResponse(repository.saveAndFlush(entity));
        audit.record("contract_types",id,"CONTRACT_TYPE_UPDATE",request.reason().trim(),before,result);
        return result;
    }

    @Transactional
    public void deactivate(Long id) {
        ContractType entity = require(id);
        entity.setIsActive(false);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(entity);
    }

    private void validate(ContractTypeRequest r) {
        if(r.reason()==null || r.reason().isBlank() || r.reason().trim().length()>500) throw new BusinessException(HttpStatus.BAD_REQUEST,"Cần lý do tối đa 500 ký tự");
        if(r.termKind()==null) throw new BusinessException(HttpStatus.BAD_REQUEST,"Bắt buộc termKind");
        if(r.termKind()==com.hrm.enums.ContractTermKind.XAC_DINH) {
            if(r.maxMonths()==null || r.maxMonths()<1 || r.maxMonths()>36) throw new BusinessException(HttpStatus.BAD_REQUEST,"XAC_DINH cần maxMonths từ 1 đến 36");
        } else if(r.maxMonths()!=null) throw new BusinessException(HttpStatus.BAD_REQUEST,"maxMonths chỉ dành cho XAC_DINH");
    }
    private ContractType require(Long id) {
        return repository.findById(id).orElseThrow(() ->
            new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy loại hợp đồng"));
    }

    private ContractTypeResponse toResponse(ContractType entity) {
        return new ContractTypeResponse(entity.getContractTypeId(), entity.getTypeCode(),
                entity.getTypeName(), entity.getIsActive(), entity.getTermKind(), entity.getMaxMonths());
    }
}
