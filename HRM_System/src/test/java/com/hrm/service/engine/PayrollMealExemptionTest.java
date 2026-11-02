package com.hrm.service.engine;

import com.hrm.dto.AttendanceSummaryDTO;
import com.hrm.dto.EmployeeSalaryProfileDTO;
import com.hrm.enums.SalaryComponentDirection;
import com.hrm.model.SalaryComponent;
import com.hrm.repository.SalaryComponentRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollMealExemptionTest {
    BigDecimal tax(String... mealAmounts) {
        var repo=mock(SalaryComponentRepository.class);
        var components=new ArrayList<SalaryComponent>();
        for(String code:List.of("LUONG_CONG","TANG_CA","PHU_CAP","KHAU_TRU")) {
            var c=new SalaryComponent(code,code,code.equals("KHAU_TRU")?SalaryComponentDirection.DEDUCT:SalaryComponentDirection.ADD,true);
            c.setComponentId((long)components.size()+1);components.add(c);
        }
        when(repo.findAll()).thenReturn(components);
        var salary=new EmployeeSalaryProfileDTO(); salary.setBaseSalary(new BigDecimal("15000000"));
        var allowances=new ArrayList<EmployeeSalaryProfileDTO.AllowanceItemDTO>();
        for(String amount:mealAmounts) {
            var a=new EmployeeSalaryProfileDTO.AllowanceItemDTO(); a.setAllowanceCode("AN_TRUA");
            a.setAllowanceName("QA meal");a.setAmount(new BigDecimal(amount));allowances.add(a);
        }
        salary.setAllowances(allowances);
        var attendance=new AttendanceSummaryDTO();attendance.setStandardWorkDays(new BigDecimal("22"));
        attendance.setActualWorkDays(new BigDecimal("22"));attendance.setPaidLeaveDays(BigDecimal.ZERO);
        attendance.setOvertimeHours(BigDecimal.ZERO);
        var result=new PayrollCalculationEngine(repo).calculatePayrollLines(1L,salary,attendance);
        return result.stream().filter(l->l.getExplanation().startsWith("Thuế TNCN"))
                .map(l->l.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    @Test void excessMealAllowanceIsTaxableUnderProjectSpecification() {
        assertEquals(0,new BigDecimal("124750").compareTo(tax("800000")));
    }
    @Test void exemptionCapAppliesToSumOfMealLines() {
        assertEquals(0,new BigDecimal("124750").compareTo(tax("400000","400000")));
    }
    @Test void mealAtOrBelowCapRemainsExempt() {
        assertEquals(0,new BigDecimal("121250").compareTo(tax("730000")));
        assertEquals(0,new BigDecimal("121250").compareTo(tax("500000")));
    }
}
