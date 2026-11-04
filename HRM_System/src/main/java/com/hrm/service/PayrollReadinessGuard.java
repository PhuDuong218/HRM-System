package com.hrm.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Called inside the caller's JPA transaction. JDBC shares its DataSource connection.
 * HOLDLOCK keeps source rows/ranges stable until calculation or payroll locking commits. */
@Service
public class PayrollReadinessGuard {
    private final JdbcTemplate db;
    private final Clock clock;
    public PayrollReadinessGuard(JdbcTemplate db){this(db,Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));}
    @org.springframework.beans.factory.annotation.Autowired
    public PayrollReadinessGuard(JdbcTemplate db,@org.springframework.beans.factory.annotation.Qualifier("hrmBusinessClock") Clock clock){this.db=db;this.clock=clock;}
    public static boolean withinClosingWindow(LocalDate periodEnd,LocalDate today){
        LocalDate month=periodEnd.plusMonths(1).withDayOfMonth(1);
        return !today.isBefore(month.withDayOfMonth(3))&&!today.isAfter(month.withDayOfMonth(5));
    }
    public void requireReady(long periodId){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Payroll readiness requires a transaction");
        var periods=db.query("SELECT period_start,period_end FROM dbo.payroll_periods WITH(UPDLOCK,HOLDLOCK) WHERE period_id=?",
            (rs,n)->new LocalDate[]{rs.getDate(1).toLocalDate(),rs.getDate(2).toLocalDate()},periodId);
        if(periods.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy kỳ lương.");
        LocalDate start=periods.get(0)[0],end=periods.get(0)[1];
        var errors=new ArrayList<String>();
        if(!withinClosingWindow(end,LocalDate.now(clock)))errors.add("chỉ được tính/chốt lương từ ngày 3 đến hết ngày 5 tháng kế tiếp ("+end.plusMonths(1).withDayOfMonth(3)+" đến "+end.plusMonths(1).withDayOfMonth(5)+")");
        LocalDateTime from=start.atStartOfDay(),until=end.plusDays(1).atStartOfDay();
        count(errors,"lịch/công chưa xác nhận", """
          SELECT COUNT_BIG(*) FROM dbo.work_schedules s WITH(HOLDLOCK)
          LEFT JOIN dbo.attendance_records a WITH(HOLDLOCK) ON a.schedule_id=s.schedule_id AND a.invalidated_at IS NULL
          WHERE s.cancelled_at IS NULL AND (s.work_date BETWEEN ? AND ? OR s.planned_start<? AND s.planned_end>? OR a.actual_start<? AND a.actual_end>?)
          AND (a.attendance_id IS NULL OR a.verification_status<>'DA_XAC_NHAN'
            OR a.attendance_result='DI_LAM' AND (a.actual_start IS NULL OR a.actual_end IS NULL))
          """,start,end,until,from,until,from);
        count(errors,"yêu cầu bổ sung/điều chỉnh công đang mở", """
          SELECT COUNT_BIG(*) FROM dbo.attendance_portal_requests r WITH(HOLDLOCK)
          JOIN dbo.work_schedules s WITH(HOLDLOCK) ON s.schedule_id=r.schedule_id
          WHERE r.status IN ('CHO_XU_LY','DANG_XU_LY') AND (s.work_date BETWEEN ? AND ? OR s.planned_start<? AND s.planned_end>? OR r.proposed_start<? AND r.proposed_end>?)
          """,start,end,until,from,until,from);
        count(errors,"đơn OT chưa duyệt hoặc chưa xác nhận giờ thực tế", """
          SELECT COUNT_BIG(*) FROM dbo.overtime_requests o WITH(HOLDLOCK)
          WHERE (o.requested_start<? AND o.requested_end>? OR o.actual_start<? AND o.actual_end>?)
            AND (o.status='CHO_DUYET' OR o.status='DA_DUYET' AND
              (o.confirmed_at IS NULL OR o.confirmed_by IS NULL OR o.actual_start IS NULL OR o.actual_end IS NULL))
          """,until,from,until,from);
        count(errors,"yêu cầu bổ sung công OT đang mở", """
          SELECT COUNT_BIG(*) FROM dbo.ot_supplement_requests r WITH(HOLDLOCK)
          JOIN dbo.overtime_requests o WITH(HOLDLOCK) ON o.overtime_id=r.overtime_id
          WHERE r.status='CHO_XU_LY' AND (o.requested_start<? AND o.requested_end>? OR r.proposed_start<? AND r.proposed_end>?)
          """,until,from,until,from);
        count(errors,"yêu cầu OT cần xử lý", """
          SELECT COUNT_BIG(*) FROM dbo.employee_requests r WITH(HOLDLOCK)
          JOIN dbo.overtime_request_details d WITH(HOLDLOCK) ON d.request_id=r.request_id
          JOIN dbo.overtime_requests o WITH(HOLDLOCK) ON o.overtime_id=d.overtime_id
          WHERE r.status='CHO_DUYET' AND (o.requested_start<? AND o.requested_end>? OR o.actual_start<? AND o.actual_end>?)
          """,until,from,until,from);
        count(errors,"yêu cầu đổi lịch chưa kết thúc", """
          SELECT COUNT_BIG(*) FROM dbo.employee_requests r WITH(HOLDLOCK)
          JOIN dbo.schedule_change_request_details d WITH(HOLDLOCK) ON d.request_id=r.request_id
          WHERE r.status='CHO_DUYET' AND d.original_start<? AND d.original_end>?
          """,until,from);
        if(!errors.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa được tính/chốt lương: "+String.join("; ",errors)+". Hoàn tất xử lý và xác nhận trước.");
    }
    private void count(List<String> errors,String label,String sql,Object... args){
        Long n=db.queryForObject(sql,Long.class,args);
        if(n==null)throw new IllegalStateException("Cannot verify payroll inputs");
        if(n>0)errors.add(n+" "+label);
    }
}
