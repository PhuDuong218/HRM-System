package com.hrm.repository;

import com.hrm.model.AttendanceRecord;
import com.hrm.repository.projection.WorkStatisticsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface WorkStatisticsRepository extends JpaRepository<AttendanceRecord, Long> {

  @Query(value = """
      select
        (select count(ar.attendance_id)
           from attendance_records ar
          where ar.work_date between :fromDate and :toDate
            and ar.invalidated_at IS NULL
            and ar.verification_status = 'DA_XAC_NHAN'
            and ar.attendance_result = 'DI_LAM') as confirmedWorkDays,
        (select count(ar.attendance_id)
           from attendance_records ar
          where ar.work_date between :fromDate and :toDate
            and ar.invalidated_at IS NULL
            and ar.verification_status = 'DA_XAC_NHAN'
            and ar.attendance_result = 'VANG') as confirmedAbsentDays,
        (select count(distinct er.request_id)
           from employee_requests er
           join leave_request_details ld on ld.request_id = er.request_id
          where er.request_type = 'NGHI_PHEP'
            and er.status = 'DA_DUYET'
            and ld.leave_start < dateadd(day, 1, :toDate)
            and ld.leave_end >= :fromDate) as approvedLeaveRequests,
        (select coalesce(sum(cast(datediff(minute,
                                case when ld.leave_start < :fromDate
                                     then cast(:fromDate as datetime2) else ld.leave_start end,
                                case when ld.leave_end >= dateadd(day, 1, :toDate)
                                     then dateadd(day, 1, cast(:toDate as datetime2)) else ld.leave_end end) as decimal(19,4))) / 60, 0)
           from employee_requests er
           join leave_request_details ld on ld.request_id = er.request_id
          where er.request_type = 'NGHI_PHEP'
            and er.status = 'DA_DUYET'
            and ld.leave_start < dateadd(day, 1, :toDate)
            and ld.leave_end >= :fromDate) as approvedLeaveHours,
        (select count(orq.overtime_id)
           from overtime_requests orq
          where orq.status = 'DA_DUYET'
            and orq.confirmed_by is not null
            and orq.confirmed_at is not null
            and cast(orq.actual_start as date) <= :toDate
            and cast(orq.actual_end as date) >= :fromDate) as approvedOvertimeRequests,
        (select coalesce(sum(cast(datediff(minute, orq.actual_start, orq.actual_end)
                                - orq.actual_break_minutes as decimal(19,4))) / 60, 0)
           from overtime_requests orq
          where orq.status = 'DA_DUYET'
            and orq.confirmed_by is not null
            and orq.confirmed_at is not null
            and cast(orq.actual_start as date) >= :fromDate
            and cast(orq.actual_end as date) <= :toDate
            and cast(orq.actual_start as date) = cast(orq.actual_end as date)) as approvedOvertimeHours
      """, nativeQuery = true)
  Optional<WorkStatisticsRow> summarize(
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);
}
