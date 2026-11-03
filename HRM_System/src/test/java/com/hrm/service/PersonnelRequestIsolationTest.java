package com.hrm.service;

import com.hrm.exception.ResourceNotFoundException;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonnelRequestIsolationTest {
    @Test void personnelDetailOnlyResolvesPersonnelRequestTypes() {
        var db=mock(JdbcTemplate.class);
        var access=mock(PersonnelAccessService.class);
        var service=new EmployeeRequestService(db,access,mock(PersonnelWorkflowSupport.class),mock(AssignmentGuardService.class));
        Principal principal=()->"admin";
        assertThrows(ResourceNotFoundException.class,()->service.detail(123,principal));
        verify(db).queryForList(contains("r.request_type IN ('NGHI_PHEP','NGHI_VIEC')"),eq(123L));
        verify(access,never()).ownerOrManager(any(),anyLong());
    }
}
