package com.hrm.service;

import com.hrm.controller.WorkShiftController;
import com.hrm.controller.WorkShiftManagementWriteController;
import com.hrm.security.AuthenticatedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkShiftApiCompatibilityTest {
    @Test void fullTaskApiKeepsCapabilitiesAndActivateContract() {
        var shifts=mock(WorkShiftService.class);var writes=mock(WorkShiftManagementWriteService.class);
        var permissions=mock(WorkShiftPermissionService.class);
        var controller=new WorkShiftController(shifts,mock(DetailedWorkTimeService.class),writes,permissions);
        var actor=new AuthenticatedAccount(1L,2L,"admin","ADMIN");
        assertTrue(controller.capabilities(actor).get("canManage"));
        controller.activate(actor,3L,new WorkShiftManagementWriteController.ReasonRequest("Reopen"));
        verify(writes).setActive(actor,3L,true,"Reopen");
        controller.deactivate(actor,3L,new WorkShiftManagementWriteController.ReasonRequest("Close"));
        verify(writes).setActive(actor,3L,false,"Close");
    }
    @Test void fullTaskApiDoesNotBypassReadPermissions() {
        var shifts=mock(WorkShiftService.class);var permissions=mock(WorkShiftPermissionService.class);
        var controller=new WorkShiftController(shifts,mock(DetailedWorkTimeService.class),mock(WorkShiftManagementWriteService.class),permissions);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(permissions).requireView(null);
        assertThrows(ResponseStatusException.class,()->controller.list(null));verifyNoInteractions(shifts);
    }
}
