package com.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import com.hrm.dto.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.util.PagingUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class DepartmentService {
    @org.springframework.beans.factory.annotation.Autowired private PersonnelScopeService personnelScope;

    private final DepartmentRepository departments;
    private final EmployeeAssignmentRepository assignments;
    private final AssignmentGuardService guard;
    private final Clock clock;

    public DepartmentService(DepartmentRepository departments, EmployeeAssignmentRepository assignments,
            AssignmentGuardService guard, @Qualifier("hrmBusinessClock") Clock clock) {
        this.departments=departments; this.assignments=assignments; this.guard=guard; this.clock=clock;
    }

    public DepartmentDTO getDepartment(Long id) { return enrich(List.of(required(id))).get(0); }
    public List<DepartmentDTO> getAllDepartment() {
        var allowed = PersonnelScopeService.isHr() ? personnelScope.departments() : null;
        return enrich(departments.findAll().stream().filter(d -> allowed == null || allowed.contains(d.getDepartmentId())).toList());
    }

    public Page<DepartmentDTO> getDepartmentsPage(int page,int size) {
        return searchDepartments(null, null, page, size);
    }

    public Page<DepartmentDTO> searchDepartments(String keyword, Boolean active, int page, int size) {
        String pattern = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        var paging=PagingUtils.create(page,size,Sort.by(Sort.Direction.DESC,"departmentId"));
        var result=PersonnelScopeService.isHr()?departments.searchScoped(pattern,active,personnelScope.departments(),paging):departments.search(pattern,active,paging);
        return new PageImpl<>(enrich(result.getContent()),result.getPageable(),result.getTotalElements());
    }

    @Transactional
    public DepartmentDTO addDepartment(DepartmentRequestDTO request) {
        if (departments.existsByDepartmentCode(request.getDepartmentCode().trim()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã phòng ban đã tồn tại");
        Department d=new Department();
        d.setDepartmentCode(request.getDepartmentCode().trim());
        d.setDepartmentName(request.getDepartmentName().trim());
        d.setDescription(request.getDescription());
        d.setIsActive(true);
        d.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return enrich(List.of(departments.saveAndFlush(d))).get(0);
    }

    @Transactional
    public DepartmentDTO updateDepartment(Long id,DepartmentRequestDTO request) {
        guard.lockTimeline();
        Department d=required(id);
        if(!d.getDepartmentCode().equals(request.getDepartmentCode().trim()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Không được đổi mã phòng ban");
        if(Boolean.FALSE.equals(request.getIsActive())) {
            guard.requireNoOpenHead(id);
            guard.requireNoDepartmentAssignments(id, LocalDate.now(clock));
        }
        d.setDepartmentName(request.getDepartmentName().trim());
        d.setDescription(request.getDescription());
        if(request.getIsActive()!=null) d.setIsActive(request.getIsActive());
        d.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return enrich(List.of(departments.saveAndFlush(d))).get(0);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        guard.lockTimeline();
        Department d=required(id);
        guard.requireNoOpenHead(id);
        guard.requireNoDepartmentAssignments(id, LocalDate.now(clock));
        d.setIsActive(false);
        d.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        departments.saveAndFlush(d);
    }

    private Department required(Long id) {
        return departments.findById(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy phòng ban: "+id));
    }

    private List<DepartmentDTO> enrich(List<Department> rows) {
        if(rows.isEmpty()) return List.of();
        Map<Long,EmployeeAssignment> heads=new HashMap<>();
        for(var a:assignments.findHeadsAt(rows.stream().map(Department::getDepartmentId).toList(),LocalDate.now(clock))) {
            if(heads.put(a.getDepartment().getDepartmentId(),a)!=null)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Dữ liệu trưởng phòng chồng hiệu lực");
        }
        return rows.stream().map(d->{
            DepartmentDTO dto=new DepartmentDTO();
            dto.setDepartmentId(d.getDepartmentId()); dto.setDepartmentCode(d.getDepartmentCode());
            dto.setDepartmentName(d.getDepartmentName()); dto.setDescription(d.getDescription()); dto.setIsActive(d.getIsActive());
            var a=heads.get(d.getDepartmentId());
            if(a!=null) {
                dto.setHeadEmployeeId(a.getEmployee().getEmployeeId());
                dto.setHeadEmployeeCode(a.getEmployee().getEmployeeCode());
                dto.setHeadFullName(a.getEmployee().getFullName()); dto.setHeadEffectiveFrom(a.getEffectiveFrom());
            }
            return dto;
        }).toList();
    }
}
