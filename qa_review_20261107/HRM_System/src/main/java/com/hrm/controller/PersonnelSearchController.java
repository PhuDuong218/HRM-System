package com.hrm.controller;

import com.hrm.dto.*;
import com.hrm.service.PersonnelSearchService;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
public class PersonnelSearchController {
 private final PersonnelSearchService service;
 public PersonnelSearchController(PersonnelSearchService service){this.service=service;}
 @GetMapping("/employee-assignments/search")
 public Page<EmployeeAssignmentDTO> assignments(Principal p,@RequestParam(required=false) Long employeeId,@RequestParam(required=false) Long departmentId,@RequestParam(required=false) Long positionId,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) LocalDate asOf,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){
  return service.assignments(p,employeeId,departmentId,positionId,keyword,asOf,page,size);
 }
 @GetMapping("/employee-qualifications/search")
 public Page<EmployeeQualificationDTO> qualifications(Principal p,@RequestParam(required=false) Long employeeId,@RequestParam(required=false) Long qualificationLevelId,@RequestParam(required=false) Boolean primary,@RequestParam(required=false) Integer yearFrom,@RequestParam(required=false) Integer yearTo,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) String validity,@RequestParam(required=false) Integer minimumRank,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){
  return service.qualifications(p,employeeId,qualificationLevelId,primary,yearFrom,yearTo,keyword,validity,minimumRank,page,size);
 }
 public Page<EmployeeQualificationDTO> qualifications(Principal p,Long employeeId,Long qualificationLevelId,Boolean primary,Integer yearFrom,Integer yearTo,String keyword,int page,int size){
  return service.qualifications(p,employeeId,qualificationLevelId,primary,yearFrom,yearTo,keyword,page,size);
 }
}
