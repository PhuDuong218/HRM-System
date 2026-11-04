package com.hrm.service;
import com.hrm.dto.EmployeeRehireRequest;
import com.hrm.dto.AuditEvent;
import com.hrm.enums.EmploymentStatus;
import com.hrm.model.*;
import com.hrm.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class EmployeeRehireServiceTest {
 @Mock EmployeeRepository employees; @Mock EmployeeAssignmentRepository assignments;
 @Mock DepartmentRepository departments; @Mock PositionRepository positions;
 @Mock AssignmentGuardService guard; @Mock AuditLogService audit; @Mock JdbcTemplate jdbc;
 EmployeeRehireService service;
 Employee employee;
 final LocalDate today=LocalDate.of(2026,10,30);
 @BeforeEach void setup(){
 service=new EmployeeRehireService(employees,assignments,departments,positions,guard,audit,jdbc,Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh")));
 employee=new Employee();employee.setEmployeeId(1L);employee.setEmploymentStatus(EmploymentStatus.DA_NGHI);
 employee.setHireDate(LocalDate.of(2020,1,1));employee.setTerminationDate(today.minusDays(10));
 }
 EmployeeRehireRequest request(LocalDate day){return new EmployeeRehireRequest(day,2L,3L,"Tái tuyển theo thỏa thuận mới");}
 void existing(){when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));}
 void validHistory(){existing();when(assignments.findHistoryByEmployeeId(1L)).thenReturn(List.of());}
 void validCatalog(){
 validHistory();var department=new Department();department.setIsActive(true);var position=new Position();position.setIsActive(true);position.setPositionCode("CHUYEN_VIEN");
 when(departments.findById(2L)).thenReturn(Optional.of(department));when(positions.findById(3L)).thenReturn(Optional.of(position));
 }
 @Test void preservesOriginalHireDateAndRecordsNewAssignmentAndAudit(){
 validCatalog();when(jdbc.queryForObject(anyString(),eq(Long.class),eq(1L))).thenReturn(0L);
 service.rehire(1L,request(today),7L);
 assertEquals(LocalDate.of(2020,1,1),employee.getHireDate());assertNull(employee.getTerminationDate());assertEquals(EmploymentStatus.DANG_LAM,employee.getEmploymentStatus());
 verify(assignments).save(argThat(a->a.getEmployee()==employee&&a.getEffectiveFrom().equals(today)&&a.getEffectiveTo()==null));
 verify(audit).record(argThat(e->e.action().equals("EMPLOYEE_REHIRE")&&e.reason().equals("Tái tuyển theo thỏa thuận mới")&&e.actorAccountId()==7L));
 var order=inOrder(guard,employees,assignments,audit);order.verify(guard).lockTimeline();order.verify(employees).findByIdForUpdate(1L);order.verify(assignments).findHistoryByEmployeeId(1L);order.verify(assignments).save(any());order.verify(employees).save(employee);order.verify(audit).record(any());
 }
 @Test void cannotRehireAnActiveEmployee(){existing();employee.setEmploymentStatus(EmploymentStatus.DANG_LAM);assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(today),7));verifyNoInteractions(assignments,audit);}
 @Test void cannotRehireOnTerminationDay(){existing();assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(employee.getTerminationDate()),7));verifyNoInteractions(assignments,audit);}
 @Test void cannotRehireInFuture(){existing();assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(today.plusDays(1)),7));verifyNoInteractions(assignments,audit);}
 @Test void missingTerminationHistoryNeedsReconciliation(){existing();employee.setTerminationDate(null);assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(today),7));verifyNoInteractions(assignments,audit);}
 @Test void openOldAssignmentBlocksRehire(){existing();var old=new EmployeeAssignment();when(assignments.findHistoryByEmployeeId(1L)).thenReturn(List.of(old));assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(today),7));verify(assignments,never()).save(any());}
 @Test void overlappingOldAssignmentBlocksRehire(){existing();var old=new EmployeeAssignment();old.setEffectiveTo(today.plusDays(1));when(assignments.findHistoryByEmployeeId(1L)).thenReturn(List.of(old));assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(today),7));verify(assignments,never()).save(any());}
 @Test void inactiveDepartmentBlocksRehire(){validCatalog();departments.findById(2L).orElseThrow().setIsActive(false);assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(today),7));verifyNoInteractions(audit);}
 @Test void headAppointmentMustBeSeparate(){validCatalog();positions.findById(3L).orElseThrow().setPositionCode("TRUONG_PHONG");assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(today),7));verifyNoInteractions(audit);}
 @Test void pendingResignationMustBeResolved(){validCatalog();when(jdbc.queryForObject(anyString(),eq(Long.class),eq(1L))).thenReturn(1L);assertThrows(ResponseStatusException.class,()->service.rehire(1L,request(today),7));verify(assignments,never()).save(any());verifyNoInteractions(audit);}
 @Test void actorRequired(){assertThrows(IllegalArgumentException.class,()->service.rehire(1L,request(today),0));verifyNoInteractions(guard,employees,assignments,audit);}
 @Test void reasonRequired(){existing();assertThrows(ResponseStatusException.class,()->service.rehire(1L,new EmployeeRehireRequest(today,2L,3L,"  "),7));verifyNoInteractions(assignments,audit);}
}
