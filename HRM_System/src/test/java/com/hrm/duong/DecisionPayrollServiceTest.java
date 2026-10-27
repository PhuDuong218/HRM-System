package com.hrm.duong;

import java.util.*;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class DecisionPayrollServiceTest {
    EmployeeRepository employees;EmployeeDecisionRepository repo;AllowancePeriodReader periods;JdbcTemplate jdbc;DecisionPayrollService service;EmployeeDecision add,deduct;AllowancePeriod period;
    @BeforeEach void setup(){employees=mock(EmployeeRepository.class);repo=mock(EmployeeDecisionRepository.class);periods=mock(AllowancePeriodReader.class);jdbc=mock(JdbcTemplate.class);service=new DecisionPayrollService(employees,repo,periods,new DecisionPayrollPolicy("TEST-ONLY-v1"),jdbc);when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee(1)));period=new AllowancePeriod(8L,TODAY.withDayOfMonth(1),TODAY.withDayOfMonth(31));when(periods.get(8L)).thenReturn(period);when(jdbc.queryForObject(anyString(),eq(Long.class),eq(8L),any(java.sql.Date.class),any(java.sql.Date.class))).thenReturn(0L);add=decision(3,DecisionPayrollEffect.ADD);deduct=decision(4,DecisionPayrollEffect.DEDUCT);add.setApprovalStatus(DecisionApprovalStatus.DA_DUYET);deduct.setApprovalStatus(DecisionApprovalStatus.DA_DUYET);deduct.setAmount(money("100000"));rows(add,deduct);}
    void rows(EmployeeDecision... rows){when(repo.findPayrollCandidates(1L,DecisionApprovalStatus.DA_DUYET,DecisionPayrollEffect.NONE,period.from(),period.to())).thenReturn(List.of(rows));}
    @Test void totalsAreExactAndDirectionIsNotInferredFromDecisionType(){var r=service.getForPeriod(1L,8L);assertEquals(0,money("500000.1250").compareTo(r.totalAdd()));assertEquals(0,money("100000").compareTo(r.totalDeduct()));assertEquals(0,money("400000.1250").compareTo(r.netAdjustment()));assertEquals(DecisionType.KY_LUAT,r.items().getFirst().decisionType());assertEquals(DecisionPayrollEffect.ADD,r.items().getFirst().direction());}
    @Test void repeatedReadIsStableAndDoesNotSaveOrConsume(){assertEquals(service.getForPeriod(1L,8L),service.getForPeriod(1L,8L));verify(repo,never()).save(any());assertEquals("employee_decisions/3",service.getForPeriod(1L,8L).items().getFirst().sourceKey());}
    @Test void duplicateDecisionIdCannotBeCountedTwice(){rows(add,add);status(409,()->service.getForPeriod(1L,8L));}
    @Test void wrongEmployeeIsRejectedEvenIfRepositoryReturnsIt(){add.setEmployee(employee(2));status(409,()->service.getForPeriod(1L,8L));}
    @Test void unapprovedOrNoneSourceIsRejected(){add.setApprovalStatus(DecisionApprovalStatus.CHO_DUYET);status(409,()->service.getForPeriod(1L,8L));add.setApprovalStatus(DecisionApprovalStatus.DA_DUYET);add.setPayrollEffect(DecisionPayrollEffect.NONE);status(409,()->service.getForPeriod(1L,8L));}
    @Test void outOfPeriodDecisionIsRejected(){add.setApplyDate(period.to().plusDays(1));status(409,()->service.getForPeriod(1L,8L));}
    @Test void bothPeriodBoundaryDatesAreIncluded(){add.setApplyDate(period.from());deduct.setApplyDate(period.to());assertEquals(2,service.getForPeriod(1L,8L).items().size());}
    @Test void overlappingPayrollPeriodsAreNotGuessed(){when(jdbc.queryForObject(anyString(),eq(Long.class),eq(8L),any(java.sql.Date.class),any(java.sql.Date.class))).thenReturn(1L);status(409,()->service.getForPeriod(1L,8L));verify(repo,never()).findPayrollCandidates(anyLong(),any(),any(),any(),any());}
    @Test void noEligibleItemsReturnsZeroEvenWhenMoneyPolicyUnconfirmed(){rows();var s=new DecisionPayrollService(employees,repo,periods,new DecisionPayrollPolicy("UNCONFIRMED"),jdbc);assertEquals(0,s.getForPeriod(1L,8L).netAdjustment().signum());}
    @Test void monetaryItemsRequireConfirmedPolicy(){var s=new DecisionPayrollService(employees,repo,periods,new DecisionPayrollPolicy("UNCONFIRMED"),jdbc);status(409,()->s.getForPeriod(1L,8L));}
}
