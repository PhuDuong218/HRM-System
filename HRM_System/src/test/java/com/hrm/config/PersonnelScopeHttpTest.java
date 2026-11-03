package com.hrm.config;

import com.hrm.controller.ContractController;
import com.hrm.controller.EmployeeRequestController;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonnelScopeHttpTest {
    PersonnelScopeService scope;
    ContractService contracts;
    EmployeeRequestService requests;
    MockMvc mvc;

    @BeforeEach void setup() {
        scope = mock(PersonnelScopeService.class);
        contracts = mock(ContractService.class);
        requests = mock(EmployeeRequestService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new ContractController(contracts, mock(ContractLifecycleService.class)),
                new EmployeeRequestController(requests, mock(PersonnelAccessService.class), mock(ResignationExecutionService.class)))
            .addInterceptors(new PersonnelPolicyWebConfiguration(scope).personnelInterceptor())
            .setControllerAdvice(new PersonnelPrivacyAdvice(scope, mock(JdbcTemplate.class)))
            .build();
        actor("HR");
    }

    void actor(String role) {
        var principal = new AuthenticatedAccount(10L, 20L, "test", role);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    void deny(long employeeId, boolean read) {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(scope).employee(employeeId, read);
    }
    @AfterEach void clear(){ SecurityContextHolder.clearContext(); }

    @Test void contractDetailOutsideScopeIsRejectedBeforeController() throws Exception {
        when(scope.employeeOf("contracts", "contract_id", 42L)).thenReturn(7L);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(scope).managedEmployee(7L);
        mvc.perform(get("/contracts/42")).andExpect(status().isForbidden());
        verifyNoInteractions(contracts);
    }
    @Test void contractCreationOutsideScopeIsRejectedFromBody() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(scope).managedEmployee(7L);
        mvc.perform(post("/contracts").contentType(MediaType.APPLICATION_JSON).content("""
            {"employeeId":7,"contractTypeId":1,"contractNumber":"ABC","signedDate":"2020-01-01","startDate":"2020-01-01"}
            """)).andExpect(status().isForbidden());
        verifyNoInteractions(contracts);
    }
    @Test void outOfScopeLeaveBalanceIsRejected() throws Exception {
        deny(7L,true);
        mvc.perform(get("/employee-requests/balances/7")).andExpect(status().isForbidden());
        verifyNoInteractions(requests);
    }
    @Test void administratorContractAccessIsNotScoped() throws Exception {
        actor("ADMIN");
        mvc.perform(get("/contracts/42")).andExpect(status().isOk());
        verify(contracts).getOne(42L);
        verifyNoInteractions(scope);
    }
    @Test void contractInScopeCanBeRead() throws Exception {
        when(scope.employeeOf("contracts", "contract_id", 42L)).thenReturn(7L);
        mvc.perform(get("/contracts/42")).andExpect(status().isOk());
        verify(scope).managedEmployee(7L);
        verify(contracts).getOne(42L);
    }
}
