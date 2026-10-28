package com.hrm.hrm.service;
import java.util.*;
import com.hrm.dto.AuditEvent;
import com.hrm.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class EmployeeAuditServiceTest {
    @Mock AuditLogService writer;
    @InjectMocks EmployeeAuditService service;
    @Test void recordsActorEmployeeAndContactSnapshot(){
        Map<String,String> before=new LinkedHashMap<>();before.put("email","old@example.com");before.put("address",null);
        Map<String,String> after=Map.of("email","new@example.com","address","Nhà \"A\"\nĐường Nguyễn Trãi");
        service.recordContactUpdate(7L,100L,before,after);
        var event=ArgumentCaptor.forClass(AuditEvent.class);verify(writer).record(event.capture());
        assertEquals(7L,event.getValue().actorAccountId());assertEquals("100",event.getValue().targetRecordKey());
        assertEquals("EMPLOYEE_CONTACT_UPDATE",event.getValue().action());assertEquals(before,event.getValue().before());assertEquals(after,event.getValue().after());
    }
    @Test void doesNotRecordUnchangedContact(){var state=Map.of("email","same@example.com");service.recordContactUpdate(7L,100L,state,state);verifyNoInteractions(writer);}
    @Test void rejectsMissingActor(){assertThrows(IllegalArgumentException.class,()->service.recordContactUpdate(null,100L,Map.of(),Map.of("email","new")));verifyNoInteractions(writer);}
}
