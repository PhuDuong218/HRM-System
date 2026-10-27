package com.hrm.duong;

import java.util.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class SalaryRateServiceTest {
    EmployeeRepository employees;ContractRepository contracts;ContractSalaryTermRepository terms;SalaryRateService service;Contract c;
    @BeforeEach void setup(){employees=mock(EmployeeRepository.class);contracts=mock(ContractRepository.class);terms=mock(ContractSalaryTermRepository.class);service=new SalaryRateService(employees,contracts,terms);when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee(1)));c=contract(3,TODAY,TODAY.plusDays(30));when(contracts.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(c));}
    @Test void splitsExactlyAtNewRateAndPreservesDecimals(){var first=term(4,c,TODAY,TODAY.plusDays(9),"10000000.1250");var next=term(5,c,TODAY.plusDays(10),null,"12000000.5000");when(terms.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(first,next));var result=service.getRatesForPeriod(1L,TODAY,TODAY.plusDays(20));assertEquals(2,result.size());assertEquals(TODAY.plusDays(9),result.getFirst().to());assertEquals(TODAY.plusDays(10),result.getLast().from());assertEquals(money("10000000.1250"),result.getFirst().baseSalary());}
    @Test void openRateDoesNotExtendPastContractEnd(){when(terms.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(term(4,c,TODAY,null,"10")));assertEquals(TODAY.plusDays(30),service.getRatesForPeriod(1L,TODAY,TODAY.plusDays(30)).getFirst().to());status(409,()->service.getRatesForPeriod(1L,TODAY,TODAY.plusDays(31)));}
    @Test void dateAtContractEndStillApplies(){when(terms.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(term(4,c,TODAY,null,"10")));assertEquals(money("10"),service.getRateAt(1L,c.getEndDate()).baseSalary());}
    @Test void missingContractIsExplicitConflict(){when(contracts.findIntersecting(eq(1L),any(),any())).thenReturn(List.of());status(409,()->service.getRateAt(1L,TODAY));}
    @Test void duplicateContractsAreRejectedRatherThanChoosingFirst(){when(contracts.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(c,contract(6,TODAY,null)));status(409,()->service.getRateAt(1L,TODAY));}
    @Test void missingSalaryIsExplicitConflict(){status(409,()->service.getRateAt(1L,TODAY));}
    @Test void duplicateSalaryTermsAreRejected(){when(terms.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(term(4,c,TODAY,null,"10"),term(5,c,TODAY,null,"20")));status(409,()->service.getRateAt(1L,TODAY));}
    @Test void salaryGapIsRejected(){when(terms.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(term(4,c,TODAY,TODAY.plusDays(1),"10"),term(5,c,TODAY.plusDays(3),null,"20")));status(409,()->service.getRatesForPeriod(1L,TODAY,TODAY.plusDays(5)));}
    @Test void termsFromOtherContractsCannotBeUsed(){when(terms.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(term(4,contract(9,TODAY,null),TODAY,null,"10")));status(409,()->service.getRateAt(1L,TODAY));}
    @Test void invalidDatesAndOversizedPeriodAre400(){status(400,()->service.getRatesForPeriod(1L,TODAY,TODAY.minusDays(1)));status(400,()->service.getRatesForPeriod(1L,TODAY,TODAY.plusDays(366)));verifyNoInteractions(employees);}
}
