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

    public ContractTypeService(ContractTypeRepository repository) {
        this.repository = repository;
    }

    public List<ContractTypeResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public ContractTypeResponse getOne(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ContractTypeResponse create(ContractTypeRequest request) {
        String code = request.typeCode().trim();
        if (repository.existsByTypeCode(code)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Mã loại hợp đồng đã tồn tại");
        }
        ContractType entity = new ContractType();
        entity.setTypeCode(code);
        entity.setTypeName(request.typeName().trim());
        entity.setIsActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public ContractTypeResponse update(Long id, ContractTypeRequest request) {
        ContractType entity = require(id);
        if (!entity.getTypeCode().equals(request.typeCode().trim())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Không đổi mã loại sau khi tạo");
        }
        entity.setTypeName(request.typeName().trim());
        if (request.isActive() != null) entity.setIsActive(request.isActive());
        entity.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void deactivate(Long id) {
        ContractType entity = require(id);
        entity.setIsActive(false);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(entity);
    }

    private ContractType require(Long id) {
        return repository.findById(id).orElseThrow(() ->
            new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy loại hợp đồng"));
    }

    private ContractTypeResponse toResponse(ContractType entity) {
        return new ContractTypeResponse(entity.getContractTypeId(), entity.getTypeCode(),
                entity.getTypeName(), entity.getIsActive());
    }
}