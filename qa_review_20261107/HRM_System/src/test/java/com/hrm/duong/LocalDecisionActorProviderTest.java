package com.hrm.duong;

import java.sql.ResultSet;
import java.util.*;
import com.hrm.dto.DecisionActor;
import com.hrm.service.LocalDecisionActorProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

class LocalDecisionActorProviderTest {
    JdbcTemplate jdbc;MockEnvironment env;
    @BeforeEach void setup(){jdbc=mock(JdbcTemplate.class);env=new MockEnvironment();env.setActiveProfiles("local");}
    @Test void disabledSimulationDoesNotTouchJdbc(){status(503,()->new LocalDecisionActorProvider(jdbc,env,false,1L).requireManager());verifyNoInteractions(jdbc);}
    @ParameterizedTest @ValueSource(strings={"prod","production","test"})
    void cannotEnableDevActorOutsideLocal(String profile){env.setActiveProfiles(profile);status(503,()->new LocalDecisionActorProvider(jdbc,env,true,1L).requireManager());verifyNoInteractions(jdbc);}
    @Test void localAlongWithProductionStillBlocked(){env.setActiveProfiles("local","prod");status(503,()->new LocalDecisionActorProvider(jdbc,env,true,1L).requireManager());verifyNoInteractions(jdbc);}
    @Test void unknownInactiveOrNonManagerAccountIsForbidden(){when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<DecisionActor>>any(),eq(10L))).thenReturn(List.of());status(403,()->new LocalDecisionActorProvider(jdbc,env,true,10L).requireManager());}
    @Test void configuredAccountIdIsUsedAndQueryChecksActiveRole(){when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<DecisionActor>>any(),eq(10L))).thenReturn(List.of(new DecisionActor(10L,100L)));assertEquals(10L,new LocalDecisionActorProvider(jdbc,env,true,10L).requireManager().accountId());var sql=org.mockito.ArgumentCaptor.forClass(String.class);verify(jdbc).query(sql.capture(),org.mockito.ArgumentMatchers.<RowMapper<DecisionActor>>any(),eq(10L));assertTrue(sql.getValue().contains("a.is_active=1"));assertTrue(sql.getValue().contains("'ADMIN','HR'"));}
}
