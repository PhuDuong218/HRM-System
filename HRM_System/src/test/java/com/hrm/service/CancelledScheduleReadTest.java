package com.hrm.service;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CancelledScheduleReadTest {
    private WorkScheduleService service;
    private final LocalDate date=LocalDate.of(2030,1,1);
    @BeforeEach void setup(){
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE TABLE dbo.work_schedules(schedule_id bigint PRIMARY KEY,employee_id bigint,shift_id bigint,work_date date,planned_start timestamp,planned_end timestamp,planned_break_minutes int,note varchar(100),cancelled_at timestamp)");
        db.update("INSERT INTO dbo.work_schedules VALUES(1,7,2,'2030-01-01','2030-01-01 08:00:00','2030-01-01 12:00:00',0,'Hủy','2029-12-31 00:00:00')");
        db.update("INSERT INTO dbo.work_schedules VALUES(2,7,2,'2030-01-01','2030-01-01 13:00:00','2030-01-01 17:00:00',0,'Đang hiệu lực',NULL)");
        service=new WorkScheduleService(db,mock(ScheduleWritePermissionService.class));
    }
    @Test void activeScheduleListExcludesCancelledRows(){var rows=service.list(7,date,date);assertEquals(1,rows.size());assertEquals(2,rows.get(0).scheduleId());}
    @Test void cancelledScheduleIsStillAvailableForHistory(){assertEquals(1,service.get(1).scheduleId());}
    @Test void cancelledTimeDoesNotBlockReplacementButActiveTimeDoes(){
        assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(service,"assertNoOverlap",7L,date.atTime(8,0),date.atTime(12,0),-1L));
        assertThrows(ResponseStatusException.class,()->ReflectionTestUtils.invokeMethod(service,"assertNoOverlap",7L,date.atTime(13,0),date.atTime(17,0),-1L));
    }
}
