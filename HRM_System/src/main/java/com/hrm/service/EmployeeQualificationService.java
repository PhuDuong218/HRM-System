package com.hrm.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.EmployeeQualificationDTO;
import com.hrm.dto.EmployeeQualificationRequestDTO;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Employee;
import com.hrm.model.EmployeeQualification;
import com.hrm.model.QualificationLevel;
import com.hrm.repository.EmployeeQualificationRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.QualificationLevelRepository;

@Service
public class EmployeeQualificationService {

    private final EmployeeQualificationRepository repository;
    private final EmployeeRepository employeeRepository;
    private final QualificationLevelRepository levelRepository;

    public EmployeeQualificationService(EmployeeQualificationRepository repository,
            EmployeeRepository employeeRepository, QualificationLevelRepository levelRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.levelRepository = levelRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeQualificationDTO> getEmployeeQualificationsByEmployeeId(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Khong tim thay nhan vien: " + employeeId);
        }
        return repository.findAllByEmployeeWithLevel(employeeId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeQualificationDTO getEmployeeQualificationById(Long id) {
        return toDTO(requiredQualification(id));
    }

    @Transactional
    public EmployeeQualificationDTO addEmployeeQualification(Long employeeId, EmployeeQualificationRequestDTO request) {
        // Khoa dong Employee tranh 2 request dong thoi doi is_primary.
        Employee employee = requiredLockedEmployee(employeeId);
        QualificationLevel level = requiredActiveLevel(request.getQualificationLevelId());
        validateGraduationYear(request.getGraduationYear());
        EmployeeQualification q = new EmployeeQualification();
        q.setEmployee(employee);
        q.setQualificationLevel(level);
        applyDetails(q, request);
        q.setIsPrimary(false);
        q.setCreatedAt(LocalDateTime.now());
        // Flush ban ghi moi truoc, sau do (neu can) chuyen primary.
        EmployeeQualification saved = repository.saveAndFlush(q);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            setPrimaryInternal(employeeId, saved);
        }
        return toDTO(saved);
    }

    @Transactional
    public EmployeeQualificationDTO updateEmployeeQualification(Long id, EmployeeQualificationRequestDTO request) {
        // Lay id nhan vien truoc, sau do lock dong employee.
        EmployeeQualification q = requiredQualification(id);
        Long employeeId = q.getEmployee().getEmployeeId();
        requiredLockedEmployee(employeeId);
        QualificationLevel level = requiredActiveLevel(request.getQualificationLevelId());
        validateGraduationYear(request.getGraduationYear());
        q.setQualificationLevel(level);
        applyDetails(q, request);
        q.setUpdatedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            setPrimaryInternal(employeeId, q);
        } else if (Boolean.FALSE.equals(request.getIsPrimary())) {
            q.setIsPrimary(false);
        }
        return toDTO(repository.saveAndFlush(q));
    }

    @Transactional
    public EmployeeQualificationDTO setPrimaryEmployeeQualification(Long id) {
        EmployeeQualification q = requiredQualification(id);
        Long employeeId = q.getEmployee().getEmployeeId();
        requiredLockedEmployee(employeeId);
        if (!Boolean.TRUE.equals(q.getQualificationLevel().getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Khong the chon trinh do da vo hieu hoa lam trinh do chinh");
        }
        setPrimaryInternal(employeeId, q);
        return toDTO(q);
    }

    @Transactional
    public void deleteEmployeeQualification(Long id) {
        // requiredQualification() chi lay ban ghi chua xoa mem.
        EmployeeQualification qualification = requiredQualification(id);
        Long employeeId = qualification.getEmployee().getEmployeeId();

        // Dong bo thao tac xoa voi cac thao tac chuyen trinh do chinh.
        requiredLockedEmployee(employeeId);

        qualification.setIsDeleted(true);
        qualification.setDeletedAt(LocalDateTime.now());
        // Chua co thong tin tai khoan dang nhap: deletedBy giu NULL.
        // Giai phong filtered unique index UX_qualifications_primary.
        qualification.setIsPrimary(false);
        qualification.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(qualification);
    }


    // SQL Server co filtered unique index UX_qualifications_primary (employee_id, WHERE is_primary=1).
    // Ha primary cu + flush truoc khi bat primary moi, tranh vi pham UNIQUE trong cung transaction.
    private void setPrimaryInternal(Long employeeId, EmployeeQualification selected) {
        repository.findPrimaryByEmployeeId(employeeId).ifPresent(old -> {
            if (!old.getEmployeeQualificationId().equals(selected.getEmployeeQualificationId())) {
                old.setIsPrimary(false);
                old.setUpdatedAt(LocalDateTime.now());
                repository.saveAndFlush(old);
            }
        });
        selected.setIsPrimary(true);
        selected.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(selected);
    }

 

    private void applyDetails(EmployeeQualification q, EmployeeQualificationRequestDTO r) {
        q.setMajor(blankToNull(r.getMajor()));
        q.setInstitution(blankToNull(r.getInstitution()));
        q.setGraduationYear(r.getGraduationYear());
        q.setCertificateNumber(blankToNull(r.getCertificateNumber()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateGraduationYear(Integer year) {
        if (year != null && year > Year.now().getValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nam tot nghiep khong duoc lon hon nam hien tai");
        }
    }

    private QualificationLevel requiredActiveLevel(Long levelId) {
        QualificationLevel level = levelRepository.findById(levelId).orElseThrow(()
                -> new ResourceNotFoundException("Khong tim thay cap trinh do: " + levelId));
        if (!Boolean.TRUE.equals(level.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cap trinh do da vo hieu hoa");
        }
        return level;
    }

    private Employee requiredLockedEmployee(Long employeeId) {
        return employeeRepository.findByIdForUpdate(employeeId).orElseThrow(()
                -> new ResourceNotFoundException("Khong tim thay nhan vien: " + employeeId));
    }

    private EmployeeQualification requiredQualification(Long id) {
        return repository.findByIdWithLevel(id).orElseThrow(()
                -> new ResourceNotFoundException("Khong tim thay trinh do cua nhan vien: " + id));
    }

    private EmployeeQualificationDTO toDTO(EmployeeQualification q) {
        QualificationLevel level = q.getQualificationLevel();
        return new EmployeeQualificationDTO(
                q.getEmployeeQualificationId(), q.getEmployee().getEmployeeId(),
                level.getQualificationLevelId(), level.getLevelCode(), level.getLevelName(),
                q.getMajor(), q.getInstitution(), q.getGraduationYear(),
                q.getCertificateNumber(), q.getIsPrimary());
    }
}
