package com.hrm.service;

import com.hrm.model.QualificationLevel;
import jakarta.persistence.EntityManager;
import java.security.Principal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonnelChangeReasonTest {
    @Test void suppliedReasonReachesAuditWithoutChangingLegacySignature() {
        var access=mock(PersonnelAccessService.class);
        var support=mock(PersonnelWorkflowSupport.class);
        var db=mock(JdbcTemplate.class);
        var em=mock(EntityManager.class);
        var service=new PersonnelCatalogService(db,access,support,mock(EmployeeRequestService.class));
        ReflectionTestUtils.setField(service,"em",em);
        Principal actor=()->"admin";
        when(access.manager(actor)).thenReturn(new PersonnelAccessService.Actor(1L,2L,"ADMIN"));
        when(support.utc()).thenReturn(LocalDateTime.of(2026,11,2,0,0));
        // Legacy schema fixture: optional qualification metadata columns are absent.
        when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(0);
        when(em.find(QualificationLevel.class,5L)).thenReturn(new QualificationLevel());
        assertEquals(5L,service.write(actor,"qualification_levels",null,"QUALIFICATION_LEVEL_CREATE",()->5L,Long::longValue,"  Verified correction  "));
        verify(support).audit(1L,"QUALIFICATION_LEVEL_CREATE","qualification_levels",5L,"Verified correction",null,null);
    }
}
