package com.hrm.service;
import com.hrm.controller.PersonnelSearchController;
import com.hrm.dto.*;
import com.hrm.repository.EmployeeRepository;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PersonnelSearchControllerTest {
 final EmployeeAssignmentService assignments=mock(EmployeeAssignmentService.class);final EmployeeQualificationService qualifications=mock(EmployeeQualificationService.class);final PersonnelAccessService access=mock(PersonnelAccessService.class);final PersonnelScopeService scope=mock(PersonnelScopeService.class);final EmployeeRepository employees=mock(EmployeeRepository.class);final Principal actor=()->"admin";
 final PersonnelSearchController controller=new PersonnelSearchController(new PersonnelSearchService(assignments,qualifications,access,scope,employees));
 @Test void allQualificationsCanBeListedWithoutEmployeeAndFilteredBeforePaging(){when(employees.findAllById(anyIterable())).thenReturn(List.of());when(qualifications.searchRows()).thenReturn(List.of(new EmployeeQualificationDTO(1L,5L,2L,"DH","Đại học","Kế toán","Trường Hà Nội",2020,"A01",true),new EmployeeQualificationDTO(2L,6L,3L,"CD","Cao đẳng","Kỹ thuật","Trường Huế",2021,"A02",false)));var result=controller.qualifications(actor,null,null,null,null,null,"",0,1);assertEquals(2,result.getTotalElements());assertEquals(1,result.getContent().size());var filtered=controller.qualifications(actor,null,2L,true,2020,2020,"ha noi",0,10);assertEquals(1,filtered.getTotalElements());verify(access,times(2)).manager(actor);}
 @Test void assignmentEffectiveEndIsExclusiveAndNamesAreAccentInsensitive(){when(assignments.getAllAssignments()).thenReturn(List.of(new EmployeeAssignmentDTO(1L,5L,"EMP5","Lê Hân",2L,"Đào tạo",3L,"Giảng viên",LocalDate.of(2020,1,1),LocalDate.of(2021,1,1))));assertEquals(1,controller.assignments(actor,null,2L,3L,"han",LocalDate.of(2020,12,31),0,10).getTotalElements());assertEquals(0,controller.assignments(actor,null,null,null,"han",LocalDate.of(2021,1,1),0,10).getTotalElements());}
 @Test void explicitOutOfScopeEmployeeCannotReadSearch(){doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(scope).employee(77L,true);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.qualifications(actor,77L,null,null,null,null,"",0,10));verifyNoInteractions(qualifications);}
 @Test void hrScopeIsAppliedToTotalsBeforePagination(){
  var authentication=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new com.hrm.security.AuthenticatedAccount(1L,5L,"hr","HR"),null,List.of());
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
  try{when(scope.employees()).thenReturn(List.of(5L));when(employees.findAllById(anyIterable())).thenReturn(List.of());when(qualifications.searchRows()).thenReturn(List.of(new EmployeeQualificationDTO(1L,5L,2L,"DH","Đại học",null,null,2020,null,true),new EmployeeQualificationDTO(2L,99L,2L,"DH","Đại học",null,null,2020,null,true)));var page=controller.qualifications(actor,null,null,null,null,null,"",0,10);assertEquals(1,page.getTotalElements());assertEquals(5L,page.getContent().getFirst().employeeId());}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
 @Test void unauthorizedAndInvertedYearsAreRejected(){doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED)).when(access).manager(null);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.assignments(null,null,null,null,"",null,0,10));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.qualifications(actor,null,null,null,2024,2020,"",0,10));verifyNoInteractions(qualifications);}
}
