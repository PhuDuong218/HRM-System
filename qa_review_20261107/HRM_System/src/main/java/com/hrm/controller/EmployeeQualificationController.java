package com.hrm.controller;

import java.security.Principal;
import com.hrm.dto.*;
import com.hrm.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class EmployeeQualificationController {
    private final EmployeeQualificationService service;
    private final PersonnelAccessService access;
    private final PersonnelCatalogService writes;
    public EmployeeQualificationController(EmployeeQualificationService service,PersonnelAccessService access,PersonnelCatalogService writes){this.service=service;this.access=access;this.writes=writes;}
    @GetMapping("/employees/{employeeId}/qualifications")
    public Page<EmployeeQualificationDTO> getEmployeeQualificationsByEmployeeId(@PathVariable Long employeeId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal p){access.ownerOrManager(p,employeeId);return service.getQualificationsPage(employeeId,page,size);}
    @GetMapping("/employee-qualifications/{id}") public EmployeeQualificationDTO getEmployeeQualificationById(@PathVariable Long id,Principal p){access.actor(p);var result=service.getEmployeeQualificationById(id);access.ownerOrManager(p,result.employeeId());return result;}
    @PostMapping("/employees/{employeeId}/qualifications") @ResponseStatus(HttpStatus.CREATED)
    public EmployeeQualificationDTO addEmployeeQualification(@PathVariable Long employeeId,@Valid @RequestBody EmployeeQualificationRequestDTO request,Principal p){return writes.qualification(p,employeeId,null,"EMPLOYEE_QUALIFICATION_CREATE",()->service.addEmployeeQualification(employeeId,request),EmployeeQualificationDTO::employeeQualificationId);}
    @PutMapping("/employee-qualifications/{id}") public EmployeeQualificationDTO updateEmployeeQualification(@PathVariable Long id,@Valid @RequestBody EmployeeQualificationRequestDTO request,Principal p){return writes.qualification(p,null,id,"EMPLOYEE_QUALIFICATION_UPDATE",()->service.updateEmployeeQualification(id,request),EmployeeQualificationDTO::employeeQualificationId);}
    @DeleteMapping("/employee-qualifications/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEmployeeQualification(@PathVariable Long id,Principal p){writes.qualification(p,null,id,"EMPLOYEE_QUALIFICATION_DELETE",()->{service.deleteEmployeeQualification(id);return id;},Long::longValue);}
    @PutMapping("/employee-qualifications/{id}/primary") public EmployeeQualificationDTO setPrimaryEmployeeQualification(@PathVariable Long id,Principal p){return writes.qualification(p,null,id,"EMPLOYEE_QUALIFICATION_PRIMARY",()->service.setPrimaryEmployeeQualification(id),EmployeeQualificationDTO::employeeQualificationId);}
}
