package com.hrm.duong;

import java.time.Clock;
import java.util.List;
import com.hrm.controller.*;
import com.hrm.dto.ExpiringContractResponse;
import com.hrm.exception.GlobalExceptionHandler;
import com.hrm.repository.ContractWarningRepository;
import com.hrm.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.hrm.duong.TestData.*;

/** Real method-security proxies, controllers, validation and service; no server/JWT/database. */
class ContractWarningHttpTest {
    AnnotationConfigApplicationContext context;
    ContractWarningRepository repository;
    ContractService contractReader;
    MockMvc mvc;

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean Clock clock() { return CLOCK; }
        @Bean ContractWarningRepository repository() { return mock(ContractWarningRepository.class); }
        @Bean ContractWarningService service(ContractWarningRepository repo, Clock clock) {
            return new ContractWarningService(repo, clock);
        }
        @Bean ContractWarningController controller(ContractWarningService service) {
            return new ContractWarningController(service);
        }
    }

    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class);
        repository=context.getBean(ContractWarningRepository.class);
        contractReader=mock(ContractService.class);
        mvc=MockMvcBuilders.standaloneSetup(context.getBean(ContractWarningController.class),
            new ContractController(contractReader,mock(ContractLifecycleService.class)),
            new EmployeeController(mock(EmployeeService.class),mock(DepartmentHeadAccessService.class),
                mock(EmployeeTerminationAuditService.class),mock(EmployeeCreationAuditService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        role("ADMIN");
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private void role(String name) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("synthetic-user","unused",
                List.of(new SimpleGrantedAuthority("ROLE_"+name))));
    }

    @Test void defaultsAndLiteralRouteDoNotResolveAsContractId() throws Exception {
        mvc.perform(get("/contracts/expiring")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10)).andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0));
        verify(repository).findExpiring(TODAY,TODAY.plusDays(30),0L,10);
        verifyNoInteractions(contractReader);
    }

    @Test void withoutActiveLiteralRouteWinsOverEmployeeId() throws Exception {
        mvc.perform(get("/employees/without-active-contract")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalPages").value(0));
        verify(repository).findWithoutActive(TODAY,0L,10);
    }

    @Test void paginationAndProjectedFieldsAreReturned() throws Exception {
        when(repository.countExpiring(TODAY,TODAY.plusDays(365))).thenReturn(3L);
        when(repository.findExpiring(TODAY,TODAY.plusDays(365),2L,2))
            .thenReturn(List.of(new ExpiringContractResponse(9L,1L,"SYNTHETIC","Synthetic",TODAY.plusDays(365),365)));
        mvc.perform(get("/contracts/expiring").param("days","365").param("page","1").param("size","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.items[0].daysLeft").value(365))
            .andExpect(jsonPath("$.items[0].employeeCode").value("SYNTHETIC"))
            .andExpect(jsonPath("$.items[0].employeeName").value("Synthetic"));
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","366","abc","2147483648"})
    void invalidDaysAre400(String value) throws Exception {
        mvc.perform(get("/contracts/expiring").param("days",value)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(strings={"/contracts/expiring","/employees/without-active-contract"})
    void badPaginationIs400(String path) throws Exception {
        for (String size:List.of("0","101","-1","abc"))
            mvc.perform(get(path).param("size",size)).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("page","abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(strings={"ADMIN","HR"})
    void bothManagementRolesCanReadBothEndpoints(String allowedRole) throws Exception {
        role(allowedRole);
        mvc.perform(get("/contracts/expiring")).andExpect(status().isOk());
        mvc.perform(get("/employees/without-active-contract")).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings={"/contracts/expiring","/employees/without-active-contract"})
    void employeeGets403BeforeAnyRepositoryRead(String path) throws Exception {
        role("EMPLOYEE");
        mvc.perform(get(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.trang_thai").value(403));
        verifyNoInteractions(repository);
    }

    @Test void serviceBeanAlsoRejectsEmployeeDirectCall() {
        role("EMPLOYEE");
        var service=context.getBean(ContractWarningService.class);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.expiring(30,0,10));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.withoutActive(0,10));
        verifyNoInteractions(repository);
    }

    @Test void missingAuthenticationIs401() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/contracts/expiring")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
}
