package com.hrm.service;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ScheduleChangeExpiryTest {
 @SuppressWarnings("unchecked") @Test void expiryIsConditionalAndAudited(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.query(contains("d.original_start<=?"),any(RowMapper.class),any(Object.class))).thenReturn(List.of(12L));
  when(db.update(contains("status='HET_HIEU_LUC'"),eq(12L))).thenReturn(1);
  new ScheduleChangeExpiryJob(db).expirePending();
  verify(db).update(contains("SCHEDULE_CHANGE_EXPIRED"),eq("12"),anyString());
 }
}
