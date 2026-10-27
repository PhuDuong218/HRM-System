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

class EmployeeAllowanceServiceTest {
    EmployeeAllowanceRepository allowances;AllowanceTypeRepository types;EmployeeRepository employees;BusinessAuditService audit;PayrollHistoryGuard guard;EmployeeAllowanceService service;EmployeeAllowance old;
    @BeforeEach void setup(){allowances=mock(EmployeeAllowanceRepository.class);types=mock(AllowanceTypeRepository.class);employees=mock(EmployeeRepository.class);audit=mock(BusinessAuditService.class);guard=mock(PayrollHistoryGuard.class);service=new EmployeeAllowanceService(allowances,types,employees,audit,guard,CLOCK);ReflectionTestUtils.setField(service,"em",mock(EntityManager.class));old=allowance(3,TODAY.minusDays(30),null,AllowanceCalculationBasis.THEO_NGAY_CONG,"30000");when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(old.getEmployee()));when(types.findByIdForUpdate(2L)).thenReturn(Optional.of(old.getAllowanceType()));when(allowances.findByEmployeeAllowanceIdAndEmployee_EmployeeId(3L,1L)).thenReturn(Optional.of(old));when(allowances.saveAndFlush(any())).thenAnswer(x->{var a=(EmployeeAllowance)x.getArgument(0);if(a.getEmployeeAllowanceId()==null)id(a,"employeeAllowanceId",4);return a;});}
    @Test void duplicateEffectiveIntervalIs409AndDoesNotSave(){when(allowances.countOverlapping(1L,2L,TODAY,null,null)).thenReturn(1L);status(409,()->service.create(1L,allowanceRequest(TODAY,null,"1")));verify(allowances,never()).saveAndFlush(any());}
    @Test void inactiveTypeCannotBeAssigned(){old.getAllowanceType().setIsActive(false);status(400,()->service.create(1L,allowanceRequest(TODAY,null,"1")));}
    @Test void formerEmployeeCannotGetNewAllowance(){old.getEmployee().setEmploymentStatus(EmploymentStatus.DA_NGHI);status(400,()->service.create(1L,allowanceRequest(TODAY,null,"1")));}
    @Test void singleDayAssignmentRejectedBySchemaRule(){status(400,()->service.create(1L,allowanceRequest(TODAY,TODAY,"1")));}
    @Test void effectiveRowCannotBeRewrittenOrDeleted(){status(400,()->service.updateFuture(1L,3L,allowanceRequest(TODAY.plusDays(1),null,"2")));status(400,()->service.deleteFuture(1L,3L,"Test"));}
    @Test void employeeScopePreventsReadingOtherEmployeesRecord(){status(404,()->service.get(2L,3L));}
    @Test void changePreservesOldAmountAndClosesAtPreviousDay(){var r=service.changeFrom(1L,3L,new AllowanceChangeRequest(allowanceRequest(TODAY.plusDays(10),null,"35000")));assertEquals(4L,r.employeeAllowanceId());assertEquals(money("30000"),old.getAmount());assertEquals(TODAY.plusDays(9),old.getEffectiveTo());assertEquals(money("35000"),r.amount());}
    @Test void stopFromDayIsNotPaid(){service.endFrom(1L,3L,new AllowanceEndRequest(TODAY.plusDays(10),"Test"));assertEquals(TODAY.plusDays(9),old.getEffectiveTo());verify(guard).assertMutable(1L,TODAY.plusDays(10),null);}
    @Test void cannotEndRetroactively(){status(400,()->service.endFrom(1L,3L,new AllowanceEndRequest(TODAY.minusDays(1),"Test")));}
    @Test void futureAssignmentCanBeUpdatedAndDeleted(){old.setEffectiveFrom(TODAY.plusDays(5));service.updateFuture(1L,3L,allowanceRequest(TODAY.plusDays(6),null,"2"));assertEquals(money("2"),old.getAmount());service.deleteFuture(1L,3L,"Test");verify(allowances).delete(old);}
}
