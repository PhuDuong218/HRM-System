package com.hrm.service;
import com.hrm.dto.EmployeeTransferRequestDTO;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EmployeeTransferReasonTest {
 @Test void oldCallerRemainsCompatible(){assertNull(new EmployeeTransferRequestDTO(1L,2L,LocalDate.of(2026,1,1)).reason());}
 @Test void newReasonReachesSharedAuditWriter(){var writer=mock(AuditLogService.class);var facade=new EmployeeAuditService(writer);facade.recordTransferWithReason(7L,9L,Map.of("departmentId","1"),Map.of("departmentId","2"),"Thay đổi bộ phận theo quyết định");verify(writer).record(argThat(e->e.action().equals("EMPLOYEE_TRANSFER")&&e.reason().equals("Thay đổi bộ phận theo quyết định")&&e.targetRecordKey().equals("9")));}
}
