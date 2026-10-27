package com.hrm.duong;

import java.math.BigDecimal;
import java.time.*;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.exception.BusinessException;
import com.hrm.model.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

final class TestData {
    static final LocalDate TODAY=LocalDate.of(2026,10,26);
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"),ZoneId.of("Asia/Bangkok"));
    static BigDecimal money(String s){return new BigDecimal(s);}
    static void id(Object o,String field,long id){ReflectionTestUtils.setField(o,field,id);}
    static Employee employee(long id){var e=new Employee();e.setEmployeeId(id);e.setEmployeeCode("NV"+id);e.setFullName("Synthetic "+id);e.setHireDate(TODAY.minusYears(1));e.setEmploymentStatus(EmploymentStatus.DANG_LAM);return e;}
    static ContractType contractType(){var t=new ContractType();id(t,"contractTypeId",2);t.setTypeCode("CT_TEST");t.setTypeName("Test");t.setIsActive(true);return t;}
    static Contract contract(long id,LocalDate from,LocalDate to){var c=new Contract();id(c,"contractId",id);c.setEmployee(employee(1));c.setContractType(contractType());c.setContractNumber("HD"+id);c.setSignedDate(TODAY.minusDays(30));c.setStartDate(from);c.setEndDate(to);c.setStatus(ContractStatus.DA_KY);return c;}
    static ContractCreateRequest contractRequest(LocalDate from,LocalDate to){return new ContractCreateRequest(1L,2L,"HD_NEW",TODAY.minusDays(30),from,to);}
    static ContractSalaryTerm term(long id,Contract c,LocalDate from,LocalDate to,String amount){var t=new ContractSalaryTerm();id(t,"salaryTermId",id);t.setContract(c);t.setEffectiveFrom(from);t.setEffectiveTo(to);t.setBaseSalary(money(amount));t.setPayBasis(PayBasis.THANG);return t;}
    static SalaryTermRequest salary(LocalDate from,LocalDate to,String amount){return new SalaryTermRequest(from,to,money(amount),PayBasis.THANG,"DOC_TEST","Synthetic test");}
    static AllowanceType allowanceType(){var t=new AllowanceType();id(t,"allowanceTypeId",2);t.setTypeCode("PC_TEST");t.setTypeName("Synthetic");t.setIsActive(true);return t;}
    static EmployeeAllowance allowance(long id,LocalDate from,LocalDate to,AllowanceCalculationBasis basis,String amount){var a=new EmployeeAllowance();id(a,"employeeAllowanceId",id);a.setEmployee(employee(1));a.setAllowanceType(allowanceType());a.setEffectiveFrom(from);a.setEffectiveTo(to);a.setCalculationBasis(basis);a.setAmount(money(amount));return a;}
    static EmployeeAllowanceRequest allowanceRequest(LocalDate from,LocalDate to,String amount){return new EmployeeAllowanceRequest(2L,from,to,AllowanceCalculationBasis.THEO_NGAY_CONG,money(amount),null,"Synthetic test");}
    static EmployeeDecision decision(long id,DecisionPayrollEffect effect){var d=new EmployeeDecision();id(d,"decisionId",id);d.setEmployee(employee(1));d.setDecisionNumber("QD"+id);d.setDecisionType(DecisionType.KY_LUAT);d.setContent("Synthetic decision");d.setIssuedDate(TODAY);d.setIssuedBy(10L);d.setApprovalStatus(DecisionApprovalStatus.CHO_DUYET);d.setPayrollEffect(effect);if(effect!=DecisionPayrollEffect.NONE){d.setAmount(money("500000.1250"));d.setApplyDate(TODAY);}return d;}
    static DecisionRequest decisionRequest(DecisionPayrollEffect effect){return new DecisionRequest(1L,"QD_NEW",DecisionType.KY_LUAT,"Synthetic decision",TODAY,effect,effect==DecisionPayrollEffect.NONE?null:money("500000.1250"),effect==DecisionPayrollEffect.NONE?null:TODAY,"Synthetic test");}
    static BusinessException status(int expected,Runnable action){var e=assertThrows(BusinessException.class,action::run);assertEquals(expected,e.getStatus().value());return e;}
    private TestData(){}
}
