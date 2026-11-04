package com.hrm.duong;

import com.hrm.controller.MyEmploymentController;
import com.hrm.enums.*;
import com.hrm.exception.GlobalExceptionHandler;
import com.hrm.model.Account;
import com.hrm.model.Role;
import com.hrm.repository.*;
import com.hrm.security.*;
import com.hrm.service.MyEmploymentService;
import jakarta.servlet.Filter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static com.hrm.duong.TestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(MyEmploymentHttpTest.TestConfig.class)
class MyEmploymentHttpTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, MyEmploymentController.class,
            MyEmploymentService.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean JwtService jwt() {
            return new JwtService("synthetic-my-employment-integration-test-signing-key", 3600L);
        }
        @Bean AccountRepository accounts() { return mock(AccountRepository.class); }
        @Bean EmployeeRepository employees() { return mock(EmployeeRepository.class); }
        @Bean PermissionRepository permissions() { return mock(PermissionRepository.class); }
        @Bean ContractRepository contracts() { return mock(ContractRepository.class); }
        @Bean ContractSalaryTermRepository terms() { return mock(ContractSalaryTermRepository.class); }
        @Bean EmployeeAllowanceRepository allowances() { return mock(EmployeeAllowanceRepository.class); }
        @Bean EmployeeDecisionRepository decisions() { return mock(EmployeeDecisionRepository.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @Autowired AccountRepository accounts;
    @Autowired EmployeeRepository employees;
    @Autowired PermissionRepository permissions;
    @Autowired ContractRepository contracts;
    @Autowired ContractSalaryTermRepository terms;
    @Autowired EmployeeAllowanceRepository allowances;
    @Autowired EmployeeDecisionRepository decisions;
    @Autowired MyEmploymentService service;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(accounts,employees,permissions,contracts,terms,allowances,decisions);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private String account(long id, Long employeeId, String roleCode, boolean enabled, boolean working) {
        var account = mock(Account.class);
        var role = mock(Role.class);
        when(account.getAccountId()).thenReturn(id);
        when(account.getEmployeeId()).thenReturn(employeeId);
        when(account.getUsername()).thenReturn("synthetic-" + id);
        when(account.isActive()).thenReturn(enabled);
        when(account.getTokenVersion()).thenReturn(1);
        when(account.getRole()).thenReturn(role);
        when(role.getRoleId()).thenReturn(1L);
        when(role.getRoleCode()).thenReturn(roleCode);
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        when(employees.existsByEmployeeIdAndEmploymentStatus(employeeId,EmploymentStatus.DANG_LAM))
                .thenReturn(working);
        when(permissions.findCodesByRoleId(1L)).thenReturn(Set.of());
        // Deliberately send a different employee and role in the token.
        return "Bearer " + jwt.issue(id, 999L, "synthetic-token", "ADMIN", 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE","ADMIN","HR"})
    void everyRoleGetsOnlyItsOwnRowsEvenWithForeignFilters(String role) throws Exception {
        String auth = account(11,1L,role,true,true);
        var own = contract(101,TODAY,null);
        var allowance = allowance(201,TODAY,null,AllowanceCalculationBasis.THEO_KY,"500");
        var decision = decision(301,DecisionPayrollEffect.NONE);
        decision.setApprovalStatus(DecisionApprovalStatus.DA_DUYET);
        decision.setApprovedBy(22L);
        decision.setContent("Nội dung quyết định\r\n[011: lưu thông tin cũ; payroll_effect=DEDUCT; amount=500; apply_date=NULL]");
        when(contracts.findByEmployee_EmployeeId(eq(1L),any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(own),call.getArgument(1),1));
        when(allowances.findByEmployee_EmployeeIdOrderByEffectiveFromAsc(1L)).thenReturn(List.of(allowance));
        when(decisions.findByEmployee_EmployeeIdAndApprovalStatus(eq(1L),eq(DecisionApprovalStatus.DA_DUYET),any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(decision),call.getArgument(2),1));

        mvc.perform(get("/api/me/contracts").header("Authorization",auth)
                        .param("employeeId","2").param("q","HD102").param("status","DA_KET_THUC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].contractId").value(101));
        mvc.perform(get("/api/me/allowances").header("Authorization",auth)
                        .param("employeeId","2").param("allowanceTypeId","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeAllowanceId").value(201))
                .andExpect(jsonPath("$[0].conditionNote").doesNotHaveJsonPath());
        mvc.perform(get("/api/me/decisions").header("Authorization",auth)
                        .param("employeeId","2").param("status","CHO_DUYET"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].decisionId").value(301))
                .andExpect(jsonPath("$.items[0].approvalStatus").value("DA_DUYET"))
                .andExpect(jsonPath("$.items[0].content").value("Nội dung quyết định"))
                .andExpect(jsonPath("$.items[0].issuedBy").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].approvedBy").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].reason").doesNotHaveJsonPath());
        verify(contracts).findByEmployee_EmployeeId(eq(1L),any(Pageable.class));
        verify(allowances).findByEmployee_EmployeeIdOrderByEffectiveFromAsc(1L);
        verify(decisions).findByEmployee_EmployeeIdAndApprovalStatus(eq(1L),eq(DecisionApprovalStatus.DA_DUYET),any(Pageable.class));
        verifyNoMoreInteractions(contracts,allowances,decisions);
    }

    @Test void foreignAndMissingContractHaveTheSame404AndNoSalaryLookup() throws Exception {
        String auth = account(11,1L,"EMPLOYEE",true,true);
        var foreign = contract(102,TODAY,null);
        foreign.setEmployee(employee(2));
        when(contracts.findByContractIdAndEmployee_EmployeeId(102L,2L)).thenReturn(Optional.of(foreign));

        var foreignResult = mvc.perform(get("/api/me/contracts/102").header("Authorization",auth).param("employeeId","2"))
                .andExpect(status().isNotFound()).andReturn();
        var missingResult = mvc.perform(get("/api/me/contracts/999").header("Authorization",auth))
                .andExpect(status().isNotFound()).andReturn();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var foreignBody = json.readTree(foreignResult.getResponse().getContentAsString());
        var missingBody = json.readTree(missingResult.getResponse().getContentAsString());
        assertEquals(foreignBody.path("loi_chi_tiet"),missingBody.path("loi_chi_tiet"));
        verifyNoInteractions(terms);

        String authB = account(22,2L,"EMPLOYEE",true,true);
        var salary = term(401,foreign,TODAY,null,"20000000");
        salary.setDocumentReference("PRIVATE-DOCUMENT");
        when(terms.findByContract_ContractIdAndContract_Employee_EmployeeIdOrderByEffectiveFromAscSalaryTermIdAsc(102L,2L))
                .thenReturn(List.of(salary));
        mvc.perform(get("/api/me/contracts/102").header("Authorization",authB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contract.contractId").value(102))
                .andExpect(jsonPath("$.salaryTerms[0].baseSalary").value(20000000))
                .andExpect(jsonPath("$.salaryTerms[0].documentReference").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.salaryTerms[0].officialReferenceSalary").doesNotHaveJsonPath());
    }

    @Test void sharedJwtFilterRejectsAnonymousLockedFormerAndUnlinkedAccounts() throws Exception {
        mvc.perform(get("/api/me/contracts")).andExpect(status().isUnauthorized());
        for (String auth : List.of(account(11,1L,"EMPLOYEE",false,true),
                account(22,2L,"HR",true,false),account(33,null,"ADMIN",true,false))) {
            mvc.perform(get("/api/me/contracts").header("Authorization",auth)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(contracts,terms,allowances,decisions);
    }

    @Test void authenticatedButUnlinkedPrincipalIsForbiddenAtThePersonalServiceBoundary() throws Exception {
        // The shared JWT filter normally rejects this account earlier. Test the defensive service boundary separately.
        var endpoint = MockMvcBuilders.standaloneSetup(new MyEmploymentController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccount(11L,null,"synthetic","ADMIN"),null,List.of()));
        endpoint.perform(get("/api/me/allowances")).andExpect(status().isForbidden());
        verifyNoInteractions(contracts,terms,allowances,decisions);
    }

    @Test void invalidPagingIs400BeforeRepositoryAccess() throws Exception {
        String auth = account(11,1L,"EMPLOYEE",true,true);
        mvc.perform(get("/api/me/contracts").header("Authorization",auth).param("size","101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/decisions").header("Authorization",auth).param("page","-1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(contracts,terms,allowances,decisions);
    }
}
