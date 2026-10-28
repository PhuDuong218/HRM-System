package com.hrm.duong;

import java.util.*;
import com.hrm.dto.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class SalaryTermServiceTest {
    ContractRepository contracts;EmployeeRepository employees;ContractSalaryTermRepository terms;PayrollHistoryGuard guard;BusinessAuditService audit;ContractSalaryTermService service;Contract c;ContractSalaryTerm old;
    @BeforeEach void setup(){contracts=mock(ContractRepository.class);employees=mock(EmployeeRepository.class);terms=mock(ContractSalaryTermRepository.class);guard=mock(PayrollHistoryGuard.class);audit=mock(BusinessAuditService.class);service=new ContractSalaryTermService(contracts,employees,terms,guard,audit,CLOCK);ReflectionTestUtils.setField(service,"em",mock(EntityManager.class));c=contract(3,TODAY.minusDays(30),TODAY.plusDays(60));old=term(4,c,TODAY.minusDays(30),null,"10000000");when(contracts.findById(3L)).thenReturn(Optional.of(c));when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(c.getEmployee()));when(terms.findBySalaryTermIdAndContract_ContractId(4L,3L)).thenReturn(Optional.of(old));when(terms.saveAndFlush(any())).thenAnswer(x->{var t=(ContractSalaryTerm)x.getArgument(0);if(t.getSalaryTermId()==null)id(t,"salaryTermId",5);return t;});}
    @Test void changeCreatesNewRowAndPreservesOldAmount(){var r=service.changeRate(3L,4L,new SalaryTermChangeRequest(salary(TODAY.plusDays(10),null,"12000000")));assertEquals(5L,r.salaryTermId());assertEquals(TODAY.plusDays(9),old.getEffectiveTo());assertEquals(money("10000000"),old.getBaseSalary());verify(guard).assertMutable(1L,TODAY.plusDays(10),c.getEndDate());}
    @Test void changeCannotRewritePast(){status(400,()->service.changeRate(3L,4L,new SalaryTermChangeRequest(salary(TODAY.minusDays(1),null,"1"))));verify(terms,never()).saveAndFlush(any());}
    @Test void effectiveRowCannotBeUpdatedOrDeleted(){status(400,()->service.updateFuture(3L,4L,salary(TODAY.plusDays(10),null,"1")));status(400,()->service.deleteFuture(3L,4L,"Test"));}
    @Test void overlappingSalaryIsRejected(){when(terms.countOverlapping(anyLong(),any(),any(),any())).thenReturn(1L);status(409,()->service.create(3L,salary(TODAY,null,"1")));}
    @Test void salaryOutsideContractAndSingleDayAreRejected(){status(400,()->service.create(3L,salary(c.getStartDate().minusDays(1),null,"1")));status(400,()->service.create(3L,salary(TODAY,TODAY,"1")));status(400,()->service.create(3L,salary(TODAY,c.getEndDate().plusDays(1),"1")));}
    @Test void cannotReadTermThroughWrongContract(){status(404,()->service.get(3L,999L));}
    @Test void futureRowCanBeUpdatedAndDeleted(){old.setEffectiveFrom(TODAY.plusDays(5));service.updateFuture(3L,4L,salary(TODAY.plusDays(6),null,"2"));assertEquals(money("2"),old.getBaseSalary());service.deleteFuture(3L,4L,"Test");verify(terms).delete(old);}
    @Test void closedPayrollStopsChangeBeforeMutatingOldRow(){doThrow(new com.hrm.exception.BusinessException(org.springframework.http.HttpStatus.CONFLICT,"closed")).when(guard).assertMutable(anyLong(),any(),any());status(409,()->service.changeRate(3L,4L,new SalaryTermChangeRequest(salary(TODAY.plusDays(10),null,"2"))));assertNull(old.getEffectiveTo());}
}
