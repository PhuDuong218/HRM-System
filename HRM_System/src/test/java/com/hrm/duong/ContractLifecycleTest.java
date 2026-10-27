package com.hrm.duong;

import java.util.*;
import com.hrm.dto.*;
import com.hrm.model.*;
import com.hrm.enums.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class ContractLifecycleTest {
    ContractRepository repo;EmployeeRepository employees;ContractService contracts;BusinessAuditService audit;PayrollHistoryGuard guard;ContractLifecycleService service;Contract old;
    @BeforeEach void setup(){repo=mock(ContractRepository.class);employees=mock(EmployeeRepository.class);contracts=mock(ContractService.class);audit=mock(BusinessAuditService.class);guard=mock(PayrollHistoryGuard.class);service=new ContractLifecycleService(repo,employees,contracts,audit,guard,CLOCK);ReflectionTestUtils.setField(service,"em",mock(EntityManager.class));old=contract(3,TODAY.minusDays(30),TODAY.plusDays(30));when(repo.findById(3L)).thenReturn(Optional.of(old));when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(old.getEmployee()));}
    @Test void futureTerminationRetainsLastDayAndProtectsOnlyRemovedDays(){service.terminate(3L,new ContractTerminateRequest(TODAY.plusDays(5),"Test"));assertEquals(TODAY.plusDays(5),old.getEndDate());assertEquals(ContractStatus.DA_KY,old.getStatus());verify(guard).assertMutable(1L,TODAY.plusDays(6),TODAY.plusDays(30));}
    @Test void retroactiveTerminationRejected(){status(409,()->service.terminate(3L,new ContractTerminateRequest(TODAY.minusDays(1),"Test")));verify(repo,never()).saveAndFlush(any());}
    @Test void repeatedTerminationRejected(){when(audit.hasAction("contracts",3L,"CONTRACT_TERMINATE")).thenReturn(true);status(409,()->service.terminate(3L,new ContractTerminateRequest(TODAY,"Test")));}
    @Test void cannotTerminateParentWithRenewal(){when(audit.hasAction("contracts",3L,"CONTRACT_RENEW")).thenReturn(true);status(409,()->service.terminate(3L,new ContractTerminateRequest(TODAY,"Test")));}
    @Test void futureRenewalPreservesOldDatesAndStatus(){var next=contractRequest(old.getEndDate().plusDays(1),TODAY.plusDays(90));when(contracts.create(next)).thenReturn(new ContractResponse(4L,1L,"NV1","Synthetic",2L,"CT","HD_NEW",TODAY,next.startDate(),next.endDate(),ContractStatus.DA_KY));assertEquals(4L,service.renew(3L,new ContractRenewRequest(next,"Test")).contractId());assertEquals(TODAY.plusDays(30),old.getEndDate());assertEquals(ContractStatus.DA_KY,old.getStatus());verify(repo,never()).saveAndFlush(old);}
    @Test void renewalCannotStartOnOldLastDay(){status(409,()->service.renew(3L,new ContractRenewRequest(contractRequest(old.getEndDate(),null),"Test")));verify(contracts,never()).create(any());}
    @Test void expiryChangesStatusOnlyAfterLastDay(){old.setEndDate(TODAY);service.closeExpired(3L);assertEquals(ContractStatus.DA_KY,old.getStatus());verify(repo,never()).saveAndFlush(any());old.setEndDate(TODAY.minusDays(1));service.closeExpired(3L);assertEquals(ContractStatus.DA_KET_THUC,old.getStatus());}
    @Test void indefiniteContractCannotRenew(){old.setEndDate(null);status(409,()->service.renew(3L,new ContractRenewRequest(contractRequest(TODAY.plusDays(1),null),"Test")));}
}
