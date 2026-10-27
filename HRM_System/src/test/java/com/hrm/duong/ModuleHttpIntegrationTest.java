package com.hrm.duong;

import java.util.*;
import com.hrm.controller.*;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import com.hrm.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

/** Real HTTP conversion/validation/controllers/services; repositories are doubles, not SQL Server. */
class ModuleHttpIntegrationTest {
    MockMvc mvc;ContractRepository contracts;EmployeeDecisionRepository decisions;DecisionActorProvider actors;EmployeeDecision pending;
    @BeforeEach void setup(){
        contracts=mock(ContractRepository.class);var types=mock(ContractTypeRepository.class);var employees=mock(EmployeeRepository.class);
        when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee(1)));when(types.findById(2L)).thenReturn(Optional.of(contractType()));
        when(contracts.saveAndFlush(any())).thenAnswer(x->{var c=(Contract)x.getArgument(0);id(c,"contractId",3);return c;});
        var contractService=new ContractService(contracts,types,employees,mock(ContractAuditService.class),mock(PayrollHistoryGuard.class));
        decisions=mock(EmployeeDecisionRepository.class);actors=mock(DecisionActorProvider.class);when(actors.requireManager()).thenReturn(new DecisionActor(20L,200L));
        pending=decision(3,DecisionPayrollEffect.NONE);when(decisions.findEmployeeIdById(3L)).thenReturn(Optional.of(1L));when(decisions.findByIdForUpdate(3L)).thenReturn(Optional.of(pending));when(decisions.findById(3L)).thenReturn(Optional.of(pending));
        when(decisions.saveAndFlush(any())).thenAnswer(x->{var d=(EmployeeDecision)x.getArgument(0);if(d.getDecisionId()==null)id(d,"decisionId",4);return d;});
        var decisionService=new EmployeeDecisionService(decisions,employees,actors,mock(DecisionAuditService.class),mock(PayrollHistoryGuard.class),new DecisionPayrollPolicy("TEST-ONLY-v1"),CLOCK);
        mvc=MockMvcBuilders.standaloneSetup(new ContractController(contractService,mock(ContractLifecycleService.class)),new EmployeeDecisionController(decisionService))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    String contractJson(){return """
        {"employeeId":1,"contractTypeId":2,"contractNumber":"HD_HTTP","signedDate":"2026-10-01","startDate":"2026-10-26","endDate":"2026-11-26"}
        """;}
    @Test void createContractRunsRealServiceAndSerializesDto() throws Exception {mvc.perform(post("/contracts").contentType("application/json").content(contractJson())).andExpect(status().isCreated()).andExpect(jsonPath("$.contractId").value(3)).andExpect(jsonPath("$.status").value("DA_KY")).andExpect(jsonPath("$.assignments").doesNotExist()).andExpect(jsonPath("$.rowVersion").doesNotExist());}
    @Test void duplicateContractUsesGlobal409Envelope() throws Exception {when(contracts.existsByContractNumber("HD_HTTP")).thenReturn(true);mvc.perform(post("/contracts").contentType("application/json").content(contractJson())).andExpect(status().isConflict()).andExpect(jsonPath("$.trang_thai").value(409)).andExpect(jsonPath("$.thoi_gian").exists()).andExpect(jsonPath("$.loi_chi_tiet").isString());}
    @Test void invalidDtoRejectedBeforeRepository() throws Exception {mvc.perform(post("/contracts").contentType("application/json").content(contractJson().replace("HD_HTTP",""))).andExpect(status().isBadRequest());verifyNoInteractions(contracts);}
    @Test void malformedJsonIs400() throws Exception {mvc.perform(post("/contracts").contentType("application/json").content("{" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.trang_thai").value(400));}
    @Test void invalidEnumAndQueryNumberAre400() throws Exception {mvc.perform(get("/contracts").param("status","UNKNOWN")).andExpect(status().isBadRequest());mvc.perform(get("/contracts").param("page","abc")).andExpect(status().isBadRequest());}
    @Test void forbiddenMethodIs405() throws Exception {mvc.perform(put("/contracts/3").contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());}
    @Test void createDecisionIgnoresClientApprovalBecauseDtoHasNoSuchFields() throws Exception {mvc.perform(post("/employee-decisions").contentType("application/json").content("""
        {"employeeId":1,"decisionNumber":"QD_HTTP","decisionType":"KY_LUAT","content":"Synthetic","issuedDate":"2026-10-26","payrollEffect":"NONE","amount":null,"applyDate":null,"reason":"Test"}
        """)).andExpect(status().isCreated()).andExpect(jsonPath("$.approvalStatus").value("CHO_DUYET")).andExpect(jsonPath("$.issuedBy").value(20)).andExpect(jsonPath("$.approvedBy").isEmpty());}
    @Test void reviewHttpFlowSetsServerReviewerAndBlocksSecondReview() throws Exception {mvc.perform(post("/employee-decisions/3/approve").contentType("application/json").content("{\"reason\":\"Test\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("DA_DUYET")).andExpect(jsonPath("$.approvedBy").value(20));mvc.perform(post("/employee-decisions/3/approve").contentType("application/json").content("{\"reason\":\"Again\"}")).andExpect(status().isConflict());}
    @Test void forgedHeaderDoesNotAllowIssuerToApprove() throws Exception {when(actors.requireManager()).thenReturn(new DecisionActor(10L,100L));mvc.perform(post("/employee-decisions/3/approve").header("X-Actor-Id","20").contentType("application/json").content("{\"reason\":\"Test\"}")).andExpect(status().isForbidden());assertEquals(DecisionApprovalStatus.CHO_DUYET,pending.getApprovalStatus());}
    @Test void missingIdentityProduces503WithoutFetchingDecision() throws Exception {when(actors.requireManager()).thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"not configured"));mvc.perform(get("/employee-decisions/3")).andExpect(status().isServiceUnavailable());verify(decisions,never()).findById(any());}
    @Test void futureDecisionDateIsRejectedByDtoValidation() throws Exception {mvc.perform(post("/employee-decisions").contentType("application/json").content("""
        {"employeeId":1,"decisionNumber":"QD_FUTURE","decisionType":"KY_LUAT","content":"Synthetic","issuedDate":"2999-01-01","payrollEffect":"NONE","reason":"Test"}
        """)).andExpect(status().isBadRequest());verify(decisions,never()).saveAndFlush(any());}
    @Test void deleteDraftReturns204WithoutBody() throws Exception {mvc.perform(delete("/employee-decisions/3").param("reason","Test")).andExpect(status().isNoContent()).andExpect(content().string(""));verify(decisions).delete(pending);}
}
