package com.hrm.security;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class AuthenticatedAccountNameTest {
 @Test void springAuthenticationExposesLoginNameInsteadOfRecordToString(){
 var account=new AuthenticatedAccount(1L,2L,"admin.sample","ADMIN");
 var authentication=new UsernamePasswordAuthenticationToken(account,null,List.of());
 assertEquals("admin.sample",authentication.getName());
 assertEquals(account,authentication.getPrincipal());
 }
}
