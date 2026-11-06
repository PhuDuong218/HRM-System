package com.hrm.service;

import com.hrm.dto.PayrollPeriodDTO;
import com.hrm.dto.PayrollPeriodRequestDTO;
import com.hrm.model.PayrollPeriod;
import com.hrm.repository.PayrollPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PayrollPeriodService {

    private final PayrollPeriodRepository repository;

    public PayrollPeriodService(PayrollPeriodRepository repository) {
        this.repository = repository;
    }

    public List<PayrollPeriodDTO> getAllPeriods() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public PayrollPeriodDTO getPeriodById(Long id) {
        PayrollPeriod entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ lương với ID: " + id));
        return toDTO(entity);
    }

    @Transactional
    public PayrollPeriodDTO createPeriod(PayrollPeriodRequestDTO request) {
        // Kiểm tra hợp lệ: start_date < end_date
        if (!request.getPeriodStart().isBefore(request.getPeriodEnd())) {
            throw new RuntimeException("Ngày bắt đầu kỳ (" + request.getPeriodStart() 
                    + ") phải trước ngày kết thúc (" + request.getPeriodEnd() + ")!");
        }

        String code = request.getPeriodCode().trim().toUpperCase();
        if (repository.existsByPeriodCode(code)) {
            throw new RuntimeException("Mã kỳ lương đã tồn tại: " + code);
        }

        PayrollPeriod entity = new PayrollPeriod();
        entity.setPeriodCode(code);
        entity.setPeriodStart(request.getPeriodStart());
        entity.setPeriodEnd(request.getPeriodEnd());
        entity.setCreatedAt(LocalDateTime.now());

        PayrollPeriod saved = repository.save(entity);
        return toDTO(saved);
    }

    @Transactional
    public PayrollPeriodDTO updatePeriod(Long id, PayrollPeriodRequestDTO request) {
        PayrollPeriod entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ lương với ID: " + id));

        // Kiểm tra hợp lệ: start_date < end_date
        if (!request.getPeriodStart().isBefore(request.getPeriodEnd())) {
            throw new RuntimeException("Ngày bắt đầu kỳ phải trước ngày kết thúc!");
        }

        String newCode = request.getPeriodCode().trim().toUpperCase();
        if (!entity.getPeriodCode().equalsIgnoreCase(newCode) && repository.existsByPeriodCode(newCode)) {
            throw new RuntimeException("Mã kỳ lương mới đã tồn tại: " + newCode);
        }

        entity.setPeriodCode(newCode);
        entity.setPeriodStart(request.getPeriodStart());
        entity.setPeriodEnd(request.getPeriodEnd());
        entity.setUpdatedAt(LocalDateTime.now());

        PayrollPeriod updated = repository.save(entity);
        return toDTO(updated);
    }

    private PayrollPeriodDTO toDTO(PayrollPeriod entity) {
        return new PayrollPeriodDTO(
                entity.getPeriodId(),
                entity.getPeriodCode(),
                entity.getPeriodStart(),
                entity.getPeriodEnd()
        );
    }
}