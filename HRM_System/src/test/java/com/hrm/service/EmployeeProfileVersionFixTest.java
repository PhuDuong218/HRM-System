package com.hrm.service;

import com.hrm.dto.*;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeProfileVersionFixTest {
    private Employee employee(){var e=new Employee();e.setEmployeeId(700L);e.setFullName("QAFIX old");e.setRowVersion(new byte[]{0,0,0,0,0,0,0,1});return e;}
    private EmployeeUpdateRequestDTO edit(String version){var r=new EmployeeUpdateRequestDTO();r.setFullName("QAFIX new");r.setVersion(version);r.setReason("  QAFIX correction  ");return r;}
    @Test void staleOrMissingVersionDoesNotWriteOrAudit(){
        var e=employee();var repo=mock(EmployeeRepository.class);var service=mock(EmployeeService.class);var audit=mock(EmployeeAuditService.class);
        when(repo.findByIdForUpdate(700L)).thenReturn(Optional.of(e));var writer=new EmployeeCreationAuditService(service,repo,audit);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->writer.update(700L,edit("0000000000000000"),1L)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->writer.update(700L,edit(null),1L)).getStatusCode().value());
        verifyNoInteractions(service,audit);verify(repo,never()).flush();assertEquals("QAFIX old",e.getFullName());
    }
    @Test void freshVersionReturnsNewVersionThenRejectsTheOldDraft(){
        var e=employee();var repo=mock(EmployeeRepository.class);var service=mock(EmployeeService.class);var audit=mock(EmployeeAuditService.class);
        when(repo.findByIdForUpdate(700L)).thenReturn(Optional.of(e));
        when(service.updateEmployee(eq(700L),any(EmployeeUpdateRequestDTO.class))).thenAnswer(i->{e.setFullName(i.<EmployeeUpdateRequestDTO>getArgument(1).getFullName());return new EmployeeDTO();});
        // Simulates SQL Server generating a new rowversion after each Hibernate flush.
        doAnswer(i->{var bytes=e.getRowVersion().clone();bytes[7]++;e.setRowVersion(bytes);return null;}).when(repo).flush();
        var writer=new EmployeeCreationAuditService(service,repo,audit);
        assertEquals("0000000000000002",writer.update(700L,edit("0000000000000001"),1L).getVersion());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->writer.update(700L,edit("0000000000000001"),2L)).getStatusCode().value());
        assertEquals("0000000000000003",writer.update(700L,edit("0000000000000002"),1L).getVersion());
        verify(audit,times(2)).recordProfileUpdate(eq(1L),eq(700L),anyMap(),anyMap(),eq("QAFIX correction"));
        verify(service,times(2)).updateEmployee(eq(700L),any(EmployeeUpdateRequestDTO.class));
    }
    @Test void blankReasonOrExplicitBlankContactDoesNotWrite(){
        var repo=mock(EmployeeRepository.class);var service=mock(EmployeeService.class);var audit=mock(EmployeeAuditService.class);
        when(repo.findByIdForUpdate(700L)).thenReturn(Optional.of(employee()));var writer=new EmployeeCreationAuditService(service,repo,audit);
        for(String reason:Arrays.asList(null,""," ","a".repeat(1001))){var r=edit("0000000000000001");r.setReason(reason);assertEquals(400,assertThrows(ResponseStatusException.class,()->writer.update(700L,r,1L)).getStatusCode().value());}
        for(String value:Arrays.asList(null,""," ")){var r=edit("0000000000000001");r.setAddress(value);assertEquals(400,assertThrows(ResponseStatusException.class,()->writer.update(700L,r,1L)).getStatusCode().value());}
        verifyNoInteractions(service,audit);
    }
    @Test void genderChangeSharesOneProfileAuditAndReturnsFinalVersion(){
        var e=employee();var repo=mock(EmployeeRepository.class);var service=mock(EmployeeService.class);var audit=mock(EmployeeAuditService.class);var extra=mock(EmployeeSupplement.class);
        when(repo.findByIdForUpdate(700L)).thenReturn(Optional.of(e));when(service.updateEmployee(eq(700L),any(EmployeeUpdateRequestDTO.class))).thenReturn(new EmployeeDTO());
        var gender=new java.util.concurrent.atomic.AtomicReference<>("MALE");when(extra.get(700L)).thenAnswer(i->gender.get());
        doAnswer(i->{gender.set("FEMALE");e.setRowVersion(new byte[]{0,0,0,0,0,0,0,3});return null;}).when(extra).saveProfile(eq(700L),eq("FEMALE"),eq(1L),anyString());
        var writer=new EmployeeCreationAuditService(service,repo,audit);org.springframework.test.util.ReflectionTestUtils.setField(writer,"supplement",extra);
        var r=edit("0000000000000001");r.setGender("FEMALE");assertEquals("0000000000000003",writer.update(700L,r,1L).getVersion());
        verify(audit).recordProfileUpdate(eq(1L),eq(700L),argThat(m->"MALE".equals(m.get("gender"))),argThat(m->"FEMALE".equals(m.get("gender"))),eq("QAFIX correction"));
        verify(extra,never()).save(anyLong(),anyString(),anyLong(),anyString());verifyNoMoreInteractions(audit);
    }
    @Test void validIdentitiesPreserveLeadingZerosAndRejectInvalidLengths(){
        for(String value:List.of("000000001","099900000006"," 099900000006 "))assertDoesNotThrow(()->EmployeeWriteRules.identity(value));
        for(String value:List.of(" ","abc","1".repeat(8),"1".repeat(10),"1".repeat(11),"1".repeat(13),"1".repeat(10000)))assertEquals(400,assertThrows(ResponseStatusException.class,()->EmployeeWriteRules.identity(value)).getStatusCode().value());
        var dto=new EmployeeUpdateRequestDTO();dto.setIdentityNumber(" 099900000006 ");assertEquals("099900000006",dto.getIdentityNumber());
        dto.ignorePrivateChanges();assertFalse(dto.hasIdentityNumber());assertFalse(dto.hasAddress());assertNull(dto.getIdentityNumber());
    }
}
