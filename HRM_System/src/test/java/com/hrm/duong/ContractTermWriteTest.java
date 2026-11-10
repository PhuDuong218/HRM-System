package com.hrm.duong;
import com.hrm.dto.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import com.hrm.enums.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

/** Real create and renew services, employee-lock/repositories replaced by doubles. */
class ContractTermWriteTest {
 ContractRepository repo;ContractTypeRepository types;EmployeeRepository employees;ContractService create;
 ContractLifecycleService lifecycle;Contract first,second;
 @BeforeEach void setup(){
  repo=mock(ContractRepository.class);types=mock(ContractTypeRepository.class);employees=mock(EmployeeRepository.class);
  var guard=mock(PayrollHistoryGuard.class);when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee(1)));
  when(types.findById(2L)).thenReturn(Optional.of(contractType()));
  when(repo.saveAndFlush(any())).thenAnswer(x->{var c=(Contract)x.getArgument(0);if(c.getContractId()==null)id(c,"contractId",9);return c;});
  create=new ContractService(repo,types,employees,mock(ContractAuditService.class),guard,new ContractTermPolicy(repo,30,180,60,30,6));
  var numbers=mock(ContractNumberService.class);ReflectionTestUtils.setField(create,"numbers",numbers);when(numbers.next("CT_TEST")).thenReturn("CT_TEST-000001");
  lifecycle=new ContractLifecycleService(repo,employees,create,mock(BusinessAuditService.class),guard,CLOCK);
  ReflectionTestUtils.setField(lifecycle,"em",mock(EntityManager.class));
  first=contract(3,TODAY.minusDays(40),TODAY.minusDays(21));second=contract(4,TODAY.minusDays(20),TODAY.plusDays(10));
  when(repo.findById(3L)).thenReturn(Optional.of(first));when(repo.findById(4L)).thenReturn(Optional.of(second));
 }
 @Test void createRejectsThirdWithoutSaving(){when(repo.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L)).thenReturn(List.of(first,second));status(409,()->create.create(contractRequest(TODAY.plusDays(11),TODAY.plusDays(20))));verify(repo,never()).saveAndFlush(any());verify(employees).findByIdForUpdate(1L);}
 @Test void renewAllowsSecondAndRejectsThirdThroughSamePolicy(){when(repo.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L)).thenReturn(List.of(first));assertEquals(9L,lifecycle.renew(3L,new ContractRenewRequest(contractRequest(TODAY.minusDays(20),TODAY.plusDays(10)),"Test")).contractId());when(repo.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L)).thenReturn(List.of(first,second));status(409,()->lifecycle.renew(4L,new ContractRenewRequest(contractRequest(TODAY.plusDays(11),TODAY.plusDays(20)),"Test")));}
 @Test void probationCreateAndRenewUseDayLimitAndOnceRule(){var trial=contractType();trial.setTermKind(ContractTermKind.THU_VIEC);trial.setMaxMonths(null);when(types.findById(2L)).thenReturn(Optional.of(trial));var request=new ContractCreateRequest(1L,2L,"TRIAL",TODAY,TODAY,TODAY.plusDays(5),ProbationGroup.CONG_VIEC_KHAC);assertEquals(ProbationGroup.CONG_VIEC_KHAC,create.create(request).probationGroup());first.setContractType(trial);when(repo.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(1L)).thenReturn(List.of(first));status(409,()->lifecycle.renew(3L,new ContractRenewRequest(request,"Test")));}
}
