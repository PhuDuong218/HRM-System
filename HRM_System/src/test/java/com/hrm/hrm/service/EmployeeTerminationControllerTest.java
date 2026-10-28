package com.hrm.hrm.service;

import java.security.Principal;

import com.hrm.controller.EmployeeController;
import com.hrm.controller.EmployeeTerminationController;
import com.hrm.exception.GlobalExceptionHandler;
import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.EmployeeCreationAuditService;
import com.hrm.service.EmployeeService;
import com.hrm.service.EmployeeTerminationAuditService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmployeeTerminationControllerTest {

    @Mock
    private EmployeeService service;
@Mock
private EmployeeCreationAuditService creationAudit;
    @Mock
    private DepartmentHeadAccessService access;

    @Mock
    private EmployeeTerminationAuditService terminationAudit;

    private MockMvc mvc;

    private final Principal manager = () -> "hr-test";

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.standaloneSetup(
                new EmployeeTerminationController(
                        terminationAudit, access),
                new EmployeeController(
        service, access, terminationAudit, creationAudit))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void anonymousPostCannotTerminate() throws Exception {
        // Chưa đăng nhập: kiểm tra quyền trả lỗi 401.
        when(access.requireManager(null))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED));

        mvc.perform(post("/employees/100/terminate"))
                .andExpect(status().isUnauthorized());

        // Bị chặn trước khi gọi nghiệp vụ.
        verifyNoInteractions(service, terminationAudit);
    }

    @Test
    void forbiddenPostCannotTerminate() throws Exception {
        // Có đăng nhập nhưng không đủ quyền: trả lỗi 403.
        when(access.requireManager(manager))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN));

        mvc.perform(post("/employees/100/terminate")
                        .principal(manager))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service, terminationAudit);
    }

    @Test
    void authorizedPostTerminatesAndReturnsNoContent() throws Exception {
        // Người đăng nhập đủ quyền, có account ID là 7.
        when(access.requireManager(manager)).thenReturn(7L);

        mvc.perform(post("/employees/100/terminate")
                        .principal(manager))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // Phải kiểm tra quyền trước khi xử lý nghỉ việc.
        var order = inOrder(access, terminationAudit);

        order.verify(access).requireManager(manager);
        order.verify(terminationAudit).terminate(100L, 7L);

        // Controller không gọi trực tiếp nghiệp vụ cấp thấp.
        verifyNoInteractions(service);
    }

    @Test
    void legacyDeleteAlsoRequiresLogin() throws Exception {
        // API DELETE cũ cũng phải yêu cầu đăng nhập.
        when(access.requireManager(null))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED));

        mvc.perform(delete("/employees/100"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service, terminationAudit);
    }

    @Test
    void authorizedLegacyDeleteRemainsAvailable() throws Exception {
        // Người đăng nhập đủ quyền, có account ID là 7.
        when(access.requireManager(manager)).thenReturn(7L);

        mvc.perform(delete("/employees/100")
                        .principal(manager))
                .andExpect(status().isOk())
                .andExpect(content().encoding("UTF-8"))
                .andExpect(content().string(
                        "Đã xử lý nghỉ việc cho nhân viên có ID: 100"));

        // DELETE cũ giữ tính lặp lại an toàn và vẫn ghi audit lần nghỉ việc đầu.
        var order = inOrder(access, terminationAudit);

        order.verify(access).requireManager(manager);
        order.verify(terminationAudit).terminateIfActive(100L, 7L);

        verifyNoInteractions(service);
    }
}
