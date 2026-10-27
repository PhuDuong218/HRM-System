package com.hrm.duong;

import java.math.BigDecimal;
import java.util.*;
import com.hrm.dto.*;
import com.hrm.model.*;
import com.hrm.enums.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import com.hrm.config.AllowanceIntegrationConfig;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class AllowanceCalculationTest {
    EmployeeRepository employees;EmployeeAllowanceRepository repo;AllowancePeriodReader periods;AllowanceWorkSource work;AllowanceCalculationService service;AllowancePeriod period;EmployeeAllowance row;
    @BeforeEach void setup(){employees=mock(EmployeeRepository.class);repo=mock(EmployeeAllowanceRepository.class);periods=mock(AllowancePeriodReader.class);work=mock(AllowanceWorkSource.class);service=calculator("UNCONFIRMED","UNCONFIRMED");when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee(1)));period=new AllowancePeriod(8L,TODAY,TODAY.plusDays(2));when(periods.get(8L)).thenReturn(period);row=allowance(3,TODAY,null,AllowanceCalculationBasis.THEO_NGAY_CONG,"30000.1250");when(repo.findIntersecting(1L,period.from(),period.to())).thenReturn(List.of(row));setWork(List.of(new EligibleAllowanceDay(TODAY,money("1"),money("8")),new EligibleAllowanceDay(TODAY.plusDays(1),money("0.5"),money("4")),new EligibleAllowanceDay(TODAY.plusDays(2),money("0"),money("0"))));}
    AllowanceCalculationService calculator(String policy,String version){return new AllowanceCalculationService(employees,repo,periods,work,policy,version);}
    void setWork(List<EligibleAllowanceDay> days){when(work.load(1L,period.from(),period.to())).thenReturn(new AllowanceWorkData(1L,period.from(),period.to(),days,"SYNTHETIC_WORK","TEST-ONLY-v1"));}
    @Test void dailyCalculationCountsHalfDaysExactlyAndDoesNotRound(){var r=service.calculate(1L,8L);assertEquals(0,money("45000.18750").compareTo(r.totalRaw()));assertEquals(money("1.5"),r.items().getFirst().eligibleUnits());assertEquals("UNROUNDED",r.roundingStatus());}
    @Test void hourlyCalculationUsesHoursInsteadOfDays(){row.setCalculationBasis(AllowanceCalculationBasis.THEO_GIO);assertEquals(0,money("360001.5000").compareTo(service.calculate(1L,8L).totalRaw()));}
    @Test void countsOnlyIntersectionOfAssignmentAndPeriod(){row.setEffectiveFrom(TODAY.plusDays(1));assertEquals(0,money("15000.06250").compareTo(service.calculate(1L,8L).totalRaw()));}
    @Test void noAssignmentsReturnsZeroWithoutCallingWorkSource(){when(repo.findIntersecting(1L,period.from(),period.to())).thenReturn(List.of());assertEquals(0,service.calculate(1L,8L).totalRaw().signum());verify(work,never()).load(anyLong(),any(),any());}
    @Test void unconfirmedPeriodPolicyFailsExplicitly(){row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);assertTrue(status(409,()->service.calculate(1L,8L)).getMessage().contains("chính sách"));}
    @Test void confirmedFullCoverageUsesOneUnitWithSyntheticPolicyOnly(){row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);var r=calculator("FULL_COVERAGE_ONLY","TEST-ONLY-v1").calculate(1L,8L);assertEquals(row.getAmount(),r.totalRaw());verify(work,never()).load(anyLong(),any(),any());}
    @Test void partialPeriodCoverageIsNotSilentlyProrated(){row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);row.setEffectiveFrom(TODAY.plusDays(1));status(409,()->calculator("FULL_COVERAGE_ONLY","TEST-ONLY-v1").calculate(1L,8L));}
    @Test void missingOrDuplicateDaysAreRejected(){setWork(List.of(new EligibleAllowanceDay(TODAY,BigDecimal.ONE,BigDecimal.ONE)));status(409,()->service.calculate(1L,8L));setWork(List.of(new EligibleAllowanceDay(TODAY,BigDecimal.ONE,BigDecimal.ONE),new EligibleAllowanceDay(TODAY,BigDecimal.ONE,BigDecimal.ONE)));status(409,()->service.calculate(1L,8L));}
    @Test void invalidUnitsAreRejected(){setWork(List.of(new EligibleAllowanceDay(TODAY,money("1.1"),money("8"))));status(409,()->service.calculate(1L,8L));setWork(List.of(new EligibleAllowanceDay(TODAY,money("1"),money("25"))));status(409,()->service.calculate(1L,8L));}
    @Test void wrongEmployeeWorkDataIsRejected(){when(work.load(1L,period.from(),period.to())).thenReturn(new AllowanceWorkData(2L,period.from(),period.to(),List.of(),"X","Y"));status(409,()->service.calculate(1L,8L));}
    @Test void overlappingSameTypeCannotBePaidTwice(){var other=allowance(4,TODAY.plusDays(1),null,AllowanceCalculationBasis.THEO_GIO,"1");when(repo.findIntersecting(1L,period.from(),period.to())).thenReturn(List.of(row,other));status(409,()->service.calculate(1L,8L));}
    @Test void unimplementedTextConditionIsNotIgnored(){row.setConditionNote("Needs evidence");status(409,()->service.calculate(1L,8L));verify(work,never()).load(anyLong(),any(),any());}
    @Test void missingWorkAdapterIs503(){var adapter=new AllowanceIntegrationConfig().unconfiguredAllowanceWorkSource();status(503,()->adapter.load(1L,TODAY,TODAY));}
}
