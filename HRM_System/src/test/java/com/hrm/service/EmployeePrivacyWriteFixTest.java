package com.hrm.service;
import com.hrm.config.PersonnelPrivacyAdvice;
import com.hrm.dto.EmployeeUpdateRequestDTO;
import com.hrm.security.AuthenticatedAccount;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.*;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EmployeePrivacyWriteFixTest {
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();}
 private PersonnelPrivacyAdvice setup(boolean owner,boolean visible){
  var actor=new AuthenticatedAccount(1L,owner?700L:1L,"QAFIX-user","ADMIN");
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
  var request=new MockHttpServletRequest("PUT","/employees/700");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  var scope=mock(PersonnelScopeService.class);when(scope.canReadPrivate(700L)).thenReturn(visible);var db=mock(JdbcTemplate.class);
  when(db.queryForList(anyString(),eq(700L))).thenReturn(List.of(Map.of("email","old@example.invalid","phone","0901234588","identity_number","099900000006","address","QAFIX address")));
  return new PersonnelPrivacyAdvice(scope,db,mock(AuditReadPolicy.class));
 }
 @Test void omittedContactOnOwnProfileDoesNotBecomeAForbiddenChange(){var advice=setup(true,true);var dto=new EmployeeUpdateRequestDTO();assertSame(dto,advice.afterBodyRead(dto,null,null,null,null));assertFalse(dto.hasEmail());assertFalse(dto.hasIdentityNumber());}
 @Test void hiddenPrivateFieldsAreIgnoredRatherThanClearedOrValidated(){var advice=setup(false,false);var dto=new EmployeeUpdateRequestDTO();dto.setIdentityNumber("");dto.setAddress("");advice.afterBodyRead(dto,null,null,null,null);assertFalse(dto.hasIdentityNumber());assertFalse(dto.hasAddress());assertNull(dto.getAddress());}
 @Test void visibleOwnerCannotSendBlankPrivateValue(){var advice=setup(true,true);var dto=new EmployeeUpdateRequestDTO();dto.setAddress(" ");assertEquals(400,assertThrows(ResponseStatusException.class,()->advice.afterBodyRead(dto,null,null,null,null)).getStatusCode().value());}
 @Test void malformedIdentityCheckedBeforeOwnContactAuthorization(){var advice=setup(true,true);var dto=new EmployeeUpdateRequestDTO();dto.setIdentityNumber("CCCDabc");assertEquals(400,assertThrows(ResponseStatusException.class,()->advice.afterBodyRead(dto,null,null,null,null)).getStatusCode().value());}
}
