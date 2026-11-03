package com.hrm.controller;
import com.hrm.dto.EmployeeRehireRequest;
import com.hrm.service.*;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/employees")
public class EmployeeRehireController {
 private final EmployeeRehireService service; private final DepartmentHeadAccessService access;
 public EmployeeRehireController(EmployeeRehireService service,DepartmentHeadAccessService access){this.service=service;this.access=access;}
 @PostMapping("/{id}/rehire")
 @PreAuthorize("hasRole('ADMIN') and hasAuthority('EMPLOYEE_MANAGE')")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 public void rehire(@PathVariable long id,@Valid @RequestBody EmployeeRehireRequest request,Principal principal){service.rehire(id,request,access.requireManager(principal));}
}
