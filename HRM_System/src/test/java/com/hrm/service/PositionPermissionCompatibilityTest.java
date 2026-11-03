package com.hrm.service;
import com.hrm.controller.PositionController;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PositionPermissionCompatibilityTest {
 AnnotationConfigApplicationContext context;
 PositionController controller;
 @Configuration @EnableMethodSecurity(proxyTargetClass=true)
 static class Config {
  @Bean PositionController controller(){return new PositionController(mock(PositionService.class),mock(DepartmentHeadAccessService.class),mock(CatalogAuditService.class));}
 }
 @BeforeEach void setup(){context=new AnnotationConfigApplicationContext(Config.class);controller=context.getBean(PositionController.class);}
 @AfterEach void close(){SecurityContextHolder.clearContext();context.close();}
 void authority(String code){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test","",List.of(new SimpleGrantedAuthority(code))));}
 @Test void existingManagePermissionCanRead(){authority("POSITION_MANAGE");assertDoesNotThrow(()->controller.getAllPositions());}
 @Test void detailedReadPermissionStillWorks(){authority("position.view");assertDoesNotThrow(()->controller.getAllPositions());}
 @Test void roleAloneDoesNotBypassPermission(){authority("ROLE_ADMIN");assertThrows(AccessDeniedException.class,()->controller.getAllPositions());}
}
