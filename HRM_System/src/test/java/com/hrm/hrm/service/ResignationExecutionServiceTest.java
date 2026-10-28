package com.hrm.hrm.service;

import java.util.*;
import java.util.stream.LongStream;
import com.hrm.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResignationExecutionServiceTest {
    @Test void blockedFirstPageDoesNotStarveLaterRequests() {
        EmployeeRequestService service=mock(EmployeeRequestService.class);
        var first=LongStream.rangeClosed(1,100).boxed().toList();
        when(service.due(0)).thenReturn(first);when(service.due(100)).thenReturn(List.of(101L));
        for(long id:first) when(service.execute(id,null)).thenThrow(PersonnelWorkflowSupport.conflict("Pending handover"));
        when(service.execute(101L,null)).thenReturn(Map.of("requestId",101L));
        var result=new ResignationExecutionService(service,false).run(null);
        assertEquals(101,result.size());assertEquals("BLOCKED",result.get(0).get("result"));
        assertEquals("APPLIED",result.get(100).get("result"));verify(service).execute(101L,null);
    }
}
