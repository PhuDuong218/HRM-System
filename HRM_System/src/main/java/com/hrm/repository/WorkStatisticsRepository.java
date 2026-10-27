package com.hrm.repository;

import com.hrm.model.AttendanceRecord;
import com.hrm.repository.projection.OvertimeIntervalRow;
import com.hrm.repository.projection.WorkIntervalRow;
import com.hrm.repository.projection.WorkStatisticsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkStatisticsRepository extends JpaRepository<AttendanceRecord, Long> {

    @Query(value = """
            select
              coalesce(sum(cast(case when ar.attendance_result = 'DI_LAM' then 1 else 0 end as bigint)), 0) as confirmedWorkDays,
              coalesce(sum(cast(case when ar.attendance_result = 'VANG' then 1 else 0 end as bigint)), 0) as confirmedAbsentDays
            from attendance_records ar
            where ar.work_date between :fromDate and :toDate
              and ar.verification_status = 'DA_XAC_NHAN'
              and ar.confirmed_by is not null
              and ar.confirmed_at is not null
            """, nativeQuery = true)
    Optional<WorkStatisticsRow> summarizeAttendance(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(value = """
            select ld.leave_start as startsAt, ld.leave_end as endsAt
            from employee_requests er
            join leave_request_details ld on ld.request_id = er.request_id
            where er.request_type = 'NGHI_PHEP'
              and er.status = 'DA_DUYET'
              and ld.leave_end > ld.leave_start
              and ld.leave_start < :endExclusive
              and ld.leave_end > :startInclusive
            """, nativeQuery = true)
    List<WorkIntervalRow> findApprovedLeaveIntervals(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query(value = """
            select orq.actual_start as startsAt, orq.actual_end as endsAt,
                   orq.actual_break_minutes as breakMinutes
            from overtime_requests orq
            where orq.status = 'DA_DUYET'
              and orq.confirmed_by is not null
              and orq.confirmed_at is not null
              and orq.actual_end > orq.actual_start
              and orq.actual_start < :endExclusive
              and orq.actual_end > :startInclusive
            """, nativeQuery = true)
    List<OvertimeIntervalRow> findConfirmedOvertimeIntervals(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);
}
