package com.hrm.service;
import com.hrm.model.Account;
import com.hrm.repository.*;
import com.hrm.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthLogoutRegressionTest {
 @Test void locksAndFlushesVersionWithoutDependingOnBulkUpdateCount(){
  var repository=mock(AccountRepository.class);var account=new Account();when(repository.findByIdForUpdate(17L)).thenReturn(Optional.of(account));
  var service=new AuthService(repository,mock(EmployeeRepository.class),mock(EmployeeAssignmentRepository.class),mock(AuditLogRepository.class),mock(PasswordEncoder.class),mock(JwtService.class));
  service.logout(17L);assertEquals(1,account.getTokenVersion());assertNotNull(account.getUpdatedAt());verify(repository).saveAndFlush(account);verify(repository,never()).incrementTokenVersion(anyLong());
 }
 @Test void missingAccountIsAnAuthenticationError(){var repository=mock(AccountRepository.class);when(repository.findByIdForUpdate(17L)).thenReturn(Optional.empty());var service=new AuthService(repository,mock(EmployeeRepository.class),mock(EmployeeAssignmentRepository.class),mock(AuditLogRepository.class),mock(PasswordEncoder.class),mock(JwtService.class));assertThrows(BadCredentialsException.class,()->service.logout(17L));verify(repository,never()).saveAndFlush(any());}
}
