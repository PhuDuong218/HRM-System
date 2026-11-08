package com.hrm.duong;

import java.util.Optional;
import com.hrm.controller.ContractController;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.model.Contract;
import com.hrm.repository.*;
import com.hrm.service.*;
import com.hrm.exception.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.hrm.duong.TestData.*;

/** Real DTO validation/controller/lifecycle service; no server, credentials or live database. */
class ContractTerminationHttpTest {
    MockMvc mvc;
    PayrollHistoryGuard guard;
    ContractRepository repo;

    @BeforeEach void setup() {
        repo=mock(ContractRepository.class);
        var employees=mock(EmployeeRepository.class);
        var audit=mock(BusinessAuditService.class);
        var reader=mock(ContractService.class);
        guard=mock(PayrollHistoryGuard.class);
        Contract old=contract(3,TODAY.minusDays(30),TODAY.plusDays(30));
        when(repo.findById(3L)).thenReturn(Optional.of(old));
        when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(old.getEmployee()));
        when(reader.getOne(3L)).thenAnswer(ignored -> new ContractResponse(3L,1L,"NV1","Synthetic",2L,"CT","HD3",
            old.getSignedDate(),old.getStartDate(),old.getEndDate(),old.getStatus(),ContractTermKind.XAC_DINH,null,old.getTerminationReasonType()));
        var lifecycle=new ContractLifecycleService(repo,employees,reader,audit,guard,CLOCK);
        ReflectionTestUtils.setField(lifecycle,"em",mock(EntityManager.class));
        mvc=MockMvcBuilders.standaloneSetup(new ContractController(reader,lifecycle))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private String body() {
        return """
            {"terminationDate":"2026-10-21","reason":"Synthetic retrospective termination","reasonType":"THOA_THUAN"}
            """;
    }

    @Test void retroactiveRequestReturnsEndedContractAndReason() throws Exception {
        mvc.perform(post("/contracts/3/terminate").contentType("application/json").content(body()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DA_KET_THUC"))
            .andExpect(jsonPath("$.endDate").value("2026-10-21"))
            .andExpect(jsonPath("$.reasonType").value("THOA_THUAN"));
    }

    @Test void missingReasonTypeReturns400BeforeLoadingContract() throws Exception {
        mvc.perform(post("/contracts/3/terminate").contentType("application/json")
            .content("{\"terminationDate\":\"2026-10-21\",\"reason\":\"Test\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repo,guard);
    }

    @Test void unknownReasonTypeReturns400() throws Exception {
        mvc.perform(post("/contracts/3/terminate").contentType("application/json").content(body().replace("THOA_THUAN","UNKNOWN")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repo,guard);
    }

    @Test void blankReasonReturns400() throws Exception {
        mvc.perform(post("/contracts/3/terminate").contentType("application/json").content(body().replace("Synthetic retrospective termination"," ")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repo,guard);
    }

    @Test void realErrorEnvelopeForClosedPayrollIs409() throws Exception {
        doThrow(new BusinessException(HttpStatus.CONFLICT,"closed payroll")).when(guard).assertMutable(1L,TODAY.minusDays(4),TODAY.plusDays(30));
        mvc.perform(post("/contracts/3/terminate").contentType("application/json").content(body()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.trang_thai").value(409));
        verify(repo,never()).saveAndFlush(any());
    }

    @Test void repeatRequestReturns409() throws Exception {
        mvc.perform(post("/contracts/3/terminate").contentType("application/json").content(body())).andExpect(status().isOk());
        mvc.perform(post("/contracts/3/terminate").contentType("application/json").content(body())).andExpect(status().isConflict());
        verify(repo,times(1)).saveAndFlush(any());
    }
}
