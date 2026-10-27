package com.hrm.duong;

import java.util.*;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class ContractServiceTest {
    ContractRepository contracts;ContractTypeRepository types;EmployeeRepository employees;
    ContractAuditService audit;PayrollHistoryGuard history;ContractService service;
    @BeforeEach void setup(){contracts=mock(ContractRepository.class);types=mock(ContractTypeRepository.class);employees=mock(EmployeeRepository.class);audit=mock(ContractAuditService.class);history=mock(PayrollHistoryGuard.class);service=new ContractService(contracts,types,employees,audit,history);
        when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee(1)));when(types.findById(2L)).thenReturn(Optional.of(contractType()));
        when(contracts.saveAndFlush(any())).thenAnswer(x->{var c=(com.hrm.model.Contract)x.getArgument(0);id(c,"contractId",3);return c;});}
    @Test void createsSignedDtoAndGuardsHistory(){var r=service.create(contractRequest(TODAY,TODAY.plusDays(30)));assertEquals(3L,r.contractId());assertEquals(ContractStatus.DA_KY,r.status());verify(history).assertMutable(1L,TODAY,TODAY.plusDays(30));verify(audit).record(eq("CONTRACT_CREATE"),eq(3L),anyString(),isNull(),anyString());}
    @Test void duplicateNumberDoesNotSave(){when(contracts.existsByContractNumber("HD_NEW")).thenReturn(true);status(409,()->service.create(contractRequest(TODAY,null)));verify(contracts,never()).saveAndFlush(any());}
    @Test void overlappingDatesDoNotSave(){when(contracts.countOverlapping(1L,TODAY,null)).thenReturn(1L);status(409,()->service.create(contractRequest(TODAY,null)));verify(contracts,never()).saveAndFlush(any());}
    @Test void missingEmployeeIs404(){when(employees.findByIdForUpdate(1L)).thenReturn(Optional.empty());status(404,()->service.create(contractRequest(TODAY,null)));}
    @Test void missingTypeIs404(){when(types.findById(2L)).thenReturn(Optional.empty());status(404,()->service.create(contractRequest(TODAY,null)));}
    @Test void inactiveTypeIsRejected(){var t=contractType();t.setIsActive(false);when(types.findById(2L)).thenReturn(Optional.of(t));status(409,()->service.create(contractRequest(TODAY,null)));}
    @Test void formerEmployeeIsRejected(){var e=employee(1);e.setEmploymentStatus(EmploymentStatus.DA_NGHI);when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(e));status(409,()->service.create(contractRequest(TODAY,null)));}
    @Test void reversedDatesAre400(){status(400,()->service.create(contractRequest(TODAY,TODAY.minusDays(1))));}
    @Test void closedPayrollBlocksCreation(){doThrow(new com.hrm.exception.BusinessException(org.springframework.http.HttpStatus.CONFLICT,"closed")).when(history).assertMutable(1L,TODAY,null);status(409,()->service.create(contractRequest(TODAY,null)));verify(contracts,never()).saveAndFlush(any());}
    @Test void invalidPagingIsRejected(){status(400,()->service.search(1L,null,null,-1,10));status(400,()->service.search(1L,null,null,0,101));status(400,()->service.search(0L,null,null,0,10));}
    @Test void searchEscapesSqlServerWildcardsAndReturnsPaging(){when(contracts.search(any(),any(),anyString(),any())).thenReturn(new PageImpl<>(List.of(),PageRequest.of(2,5),11));var r=service.search(1L,null,"A_[%!]",2,5);assertEquals(11,r.totalElements());assertEquals(2,r.page());verify(contracts).search(eq(1L),isNull(),eq("%a!_![!%!!]%"),any());}
}
