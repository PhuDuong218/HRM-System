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
    private final SalaryRateService salaryRates = mock(SalaryRateService.class);
    EmployeeRepository employees;EmployeeAllowanceRepository repo;AllowancePeriodReader periods;AllowanceWorkSource work;AllowanceCalculationService service;AllowancePeriod period;EmployeeAllowance row;
    @BeforeEach void setup(){employees=mock(EmployeeRepository.class);repo=mock(EmployeeAllowanceRepository.class);periods=mock(AllowancePeriodReader.class);work=mock(AllowanceWorkSource.class);service=calculator("UNCONFIRMED","UNCONFIRMED");when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee(1)));period=new AllowancePeriod(8L,TODAY,TODAY.plusDays(2));when(periods.get(8L)).thenReturn(period);row=allowance(3,TODAY,null,AllowanceCalculationBasis.THEO_NGAY_CONG,"30000.1250");when(repo.findIntersecting(1L,period.from(),period.to())).thenReturn(List.of(row));setWork(List.of(new EligibleAllowanceDay(TODAY,money("1"),money("8")),new EligibleAllowanceDay(TODAY.plusDays(1),money("0.5"),money("4")),new EligibleAllowanceDay(TODAY.plusDays(2),money("0"),money("0"))));}
    AllowanceCalculationService calculator(String policy,String version){return new AllowanceCalculationService(employees,repo,periods,work,salaryRates,policy,version);}
    void setWork(List<EligibleAllowanceDay> days){when(work.load(1L,period.from(),period.to())).thenReturn(new AllowanceWorkData(1L,period.from(),period.to(),days,"SYNTHETIC_WORK","TEST-ONLY-v1"));}
    @Test void dailyCalculationPreservesRawAmountAndRoundsTotal(){var r=service.calculate(1L,8L);assertEquals(0,money("45000.18750").compareTo(r.totalRaw()));assertEquals(money("1.5"),r.items().getFirst().eligibleUnits());assertEquals(money("45000"),r.totalRounded());assertEquals("TOTAL_HALF_UP_VND",r.roundingStatus());}
    @Test void hourlyCalculationUsesHoursInsteadOfDays(){row.setCalculationBasis(AllowanceCalculationBasis.THEO_GIO);assertEquals(0,money("360001.5000").compareTo(service.calculate(1L,8L).totalRaw()));}
    @Test void countsOnlyIntersectionOfAssignmentAndPeriod(){row.setEffectiveFrom(TODAY.plusDays(1));assertEquals(0,money("15000.06250").compareTo(service.calculate(1L,8L).totalRaw()));}
    @Test void noAssignmentsReturnsZeroWithoutCallingWorkSource(){when(repo.findIntersecting(1L,period.from(),period.to())).thenReturn(List.of());assertEquals(0,service.calculate(1L,8L).totalRaw().signum());verify(work,never()).load(anyLong(),any(),any());}
    @Test void unconfirmedPeriodPolicyFailsExplicitly(){row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);assertTrue(status(409,()->service.calculate(1L,8L)).getMessage().contains("chính sách"));}
    private AllowanceCalculationResult calendarResult() {
        return calculator("CALENDAR_DAY_PRORATION","TEST-ONLY-v1").calculate(1L,8L);
    }

    @Test void fullPeriodReceivesFullConfiguredAmount() {
        row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);
        var result = calendarResult();
        assertEquals(0,row.getAmount().compareTo(result.totalRaw()));
        assertEquals(0,BigDecimal.ONE.compareTo(result.items().getFirst().eligibleUnits()));
        verifyNoInteractions(work,salaryRates);
    }

    @Test void partialCalendarCoverageIncludesBothDatesAndReturnsEvidence() {
        row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);
        row.setEffectiveFrom(TODAY.plusDays(1));
        row.setAmount(money("30000"));
        var result = calendarResult();
        var line = result.items().getFirst();
        assertEquals(2,line.effectiveDays());
        assertEquals(3,line.periodDays());
        assertEquals(0,money("20000").compareTo(line.rawAmount()));
        assertEquals("TEST-ONLY-v1",line.policyVersion());
        assertEquals(money("20000"),result.totalRounded());
        verifyNoInteractions(work,salaryRates);
    }

    @Test void twoRatesInTheSamePeriodAreProratedIndependently() {
        row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);
        row.setEffectiveFrom(TODAY.minusDays(1));
        row.setEffectiveTo(TODAY);
        row.setAmount(money("300"));
        var next = allowance(4,TODAY.plusDays(1),null,AllowanceCalculationBasis.THEO_KY,"600");
        when(repo.findIntersecting(1L,period.from(),period.to())).thenReturn(List.of(row,next));

        var result = calendarResult();
        assertEquals(List.of(1,2),result.items().stream().map(AllowanceLine::effectiveDays).toList());
        assertEquals(0,money("100").compareTo(result.items().getFirst().rawAmount()));
        assertEquals(0,money("400").compareTo(result.items().getLast().rawAmount()));
        assertEquals(money("500"),result.totalRounded());
    }

    @Test void partialEmploymentDoesNotChangeTheFullPeriodDivisor() {
        row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);
        row.setAmount(money("300"));
        var employee = employee(1);
        employee.setHireDate(TODAY.plusDays(1));
        employee.setTerminationDate(TODAY.plusDays(1));
        when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        var line = calendarResult().items().getFirst();
        assertEquals(TODAY.plusDays(1),line.from());
        assertEquals(line.from(),line.to());
        assertEquals(1,line.effectiveDays());
        assertEquals(3,line.periodDays());
        assertEquals(0,money("100").compareTo(line.rawAmount()));

        employee.setHireDate(TODAY.plusDays(3));
        employee.setTerminationDate(null);
        assertTrue(calendarResult().items().isEmpty());
    }

    @Test void roundsOnceFromExactSumEvenWhenDisplayedFractionsAreRecurring() {
        row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);
        row.setEffectiveFrom(TODAY.minusDays(1));
        row.setEffectiveTo(TODAY);
        row.setAmount(BigDecimal.ONE);
        var second = allowance(4,TODAY.plusDays(2),null,AllowanceCalculationBasis.THEO_KY,"1");
        var third = allowance(5,TODAY.plusDays(2),null,AllowanceCalculationBasis.THEO_KY,"2.5");
        id(third.getAllowanceType(),"allowanceTypeId",3);
        when(repo.findIntersecting(1L,period.from(),period.to())).thenReturn(List.of(row,second,third));

        var result = calendarResult();
        assertEquals(0,money("1.5").compareTo(result.totalRaw()));
        assertEquals(money("2"),result.totalRounded());
    }

    @Test void percentageLooksUpSalaryAtAssignmentStartEvenIfOutsidePeriod() {
        row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);
        row.setEffectiveFrom(TODAY.minusDays(7));
        row.setAmountType(AllowanceAmountType.PERCENT_BASE_SALARY);
        row.setAmount(money("7.5"));
        var reference = new SalaryRateSegment(1L,2L,4L,row.getEffectiveFrom(),row.getEffectiveFrom(),
                money("10000"),PayBasis.THANG);
        when(salaryRates.getRateAt(1L,row.getEffectiveFrom())).thenReturn(reference);
        var line = calendarResult().items().getFirst();
        assertEquals(reference,line.salaryReference());
        assertEquals(money("7.5"),line.configuredAmount());
        assertEquals(0,money("750").compareTo(line.unitAmount()));
        assertEquals(0,money("750").compareTo(line.rawAmount()));
        verify(salaryRates).getRateAt(1L,row.getEffectiveFrom());
    }

    @Test void calendarProrationStillRequiresPolicyVersion() {
        row.setCalculationBasis(AllowanceCalculationBasis.THEO_KY);
        status(409,()->calculator("CALENDAR_DAY_PRORATION","UNCONFIRMED").calculate(1L,8L));
    }
    @Test void missingOrDuplicateDaysAreRejected(){setWork(List.of(new EligibleAllowanceDay(TODAY,BigDecimal.ONE,BigDecimal.ONE)));status(409,()->service.calculate(1L,8L));setWork(List.of(new EligibleAllowanceDay(TODAY,BigDecimal.ONE,BigDecimal.ONE),new EligibleAllowanceDay(TODAY,BigDecimal.ONE,BigDecimal.ONE)));status(409,()->service.calculate(1L,8L));}
    @Test void invalidUnitsAreRejected(){setWork(List.of(new EligibleAllowanceDay(TODAY,money("1.1"),money("8"))));status(409,()->service.calculate(1L,8L));setWork(List.of(new EligibleAllowanceDay(TODAY,money("1"),money("25"))));status(409,()->service.calculate(1L,8L));}
    @Test void wrongEmployeeWorkDataIsRejected(){when(work.load(1L,period.from(),period.to())).thenReturn(new AllowanceWorkData(2L,period.from(),period.to(),List.of(),"X","Y"));status(409,()->service.calculate(1L,8L));}
    @Test void overlappingSameTypeCannotBePaidTwice(){var other=allowance(4,TODAY.plusDays(1),null,AllowanceCalculationBasis.THEO_GIO,"1");when(repo.findIntersecting(1L,period.from(),period.to())).thenReturn(List.of(row,other));status(409,()->service.calculate(1L,8L));}
    @Test void conditionNoteDoesNotChangeAmount() {
        var withoutNote = service.calculate(1L,8L);
        row.setConditionNote("Ghi chú nghiệp vụ");
        assertEquals(withoutNote, service.calculate(1L,8L));
    }

    @Test void resultReturnsFlagsFromEachAllowanceType() {
        row.getAllowanceType().setTaxable(false);
        row.getAllowanceType().setCountsForSocialInsurance(true);
        var result = service.calculate(1L,8L);
        var json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var line = json.readTree(json.writeValueAsString(result)).path("items").get(0);
        assertEquals(json.getNodeFactory().booleanNode(false),line.path("isTaxable"));
        assertEquals(json.getNodeFactory().booleanNode(true),line.path("countsForSocialInsurance"));
    }
    @Test void missingWorkAdapterIs503(){var adapter=new AllowanceIntegrationConfig().unconfiguredAllowanceWorkSource();status(503,()->adapter.load(1L,TODAY,TODAY));}
}
