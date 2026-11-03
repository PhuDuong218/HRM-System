package com.hrm.service;

import com.hrm.dto.*;
import com.hrm.dto.PayrollStatisticsResponse.Amounts;
import com.hrm.dto.PayrollStatisticsResponse.BatchSummary;
import com.hrm.repository.projection.EmployeeStatisticsRow;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** HR aggregates are filtered in the database before counting or exporting. */
@Service
public class HrScopedReportService {
    private final JdbcTemplate db;
    private final PersonnelScopeService scope;
    private static final BigDecimal HOUR = BigDecimal.valueOf(3_600_000);
    private static final String GRANT = "EXISTS (SELECT 1 FROM dbo.hr_department_scopes hs WHERE hs.department_id=ea.department_id AND hs.hr_account_id=? AND hs.revoked_at IS NULL AND hs.effective_from<=SYSUTCDATETIME() AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME()))";

    public HrScopedReportService(JdbcTemplate db, PersonnelScopeService scope) { this.db=db; this.scope=scope; }

    private long actor() {
        var actor = PersonnelScopeService.current();
        if (actor == null || !"HR".equals(actor.roleCode()) || actor.accountId() == null)
            throw new org.springframework.security.access.AccessDeniedException("Không xác định được phạm vi HR");
        return actor.accountId();
    }

    private record Group(Long groupId, String groupCode, String groupName, Long employeeCount) implements EmployeeStatisticsRow {
        public Long getGroupId() { return groupId; }
        public String getGroupCode() { return groupCode; }
        public String getGroupName() { return groupName; }
        public Long getEmployeeCount() { return employeeCount; }
    }

    private List<EmployeeStatisticsRow> groups(String sql, Object... args) {
        return db.query(sql, (rs,n) -> new Group(rs.getObject(1,Long.class),rs.getString(2),rs.getString(3),rs.getLong(4)),args);
    }

    public EmployeeStatisticsResponse employees(LocalDate today) {
        actor();
        String employees = scope.sqlEmployees("e.employee_id");
        String assignment = """
            WITH effective AS (SELECT ea.employee_id,ea.department_id,ea.position_id,
             ROW_NUMBER() OVER (PARTITION BY ea.employee_id ORDER BY ea.effective_from DESC,ea.assignment_id DESC) rn
             FROM dbo.employee_assignments ea WHERE ea.effective_from<=? AND (ea.effective_to IS NULL OR ea.effective_to>?))
            """;
        var departments=groups(assignment+"SELECT d.department_id,COALESCE(d.department_code,'CHUA_PHAN_CONG'),COALESCE(d.department_name,N'Chưa có phân công hiệu lực'),COUNT(DISTINCT e.employee_id) FROM dbo.employees e LEFT JOIN effective a ON a.employee_id=e.employee_id AND a.rn=1 LEFT JOIN dbo.departments d ON d.department_id=a.department_id WHERE "+employees+" GROUP BY d.department_id,d.department_code,d.department_name ORDER BY d.department_name",today,today);
        var positions=groups(assignment+"SELECT p.position_id,COALESCE(p.position_code,'CHUA_PHAN_CONG'),COALESCE(p.position_name,N'Chưa có phân công hiệu lực'),COUNT(DISTINCT e.employee_id) FROM dbo.employees e LEFT JOIN effective a ON a.employee_id=e.employee_id AND a.rn=1 LEFT JOIN dbo.positions p ON p.position_id=a.position_id WHERE "+employees+" GROUP BY p.position_id,p.position_code,p.position_name ORDER BY p.position_name",today,today);
        var statuses=groups("SELECT CAST(NULL AS bigint),e.employment_status,e.employment_status,COUNT(DISTINCT e.employee_id) FROM dbo.employees e WHERE "+employees+" GROUP BY e.employment_status ORDER BY e.employment_status");
        var qualifications=groups("SELECT q.qualification_level_id,q.level_code,q.level_name,COUNT(DISTINCT e.employee_id) FROM dbo.employees e JOIN dbo.employee_qualifications eq ON eq.employee_id=e.employee_id AND eq.is_primary=1 JOIN dbo.qualification_levels q ON q.qualification_level_id=eq.qualification_level_id WHERE "+employees+" GROUP BY q.qualification_level_id,q.level_code,q.level_name ORDER BY q.level_name");
        return new EmployeeStatisticsResponse(departments,positions,statuses,qualifications);
    }

    public ContractStatisticsResponse contracts(LocalDate from, LocalDate to, LocalDate date, int days, Long department) {
        long hr=actor();
        if (department!=null) scope.department(department);
        String departmentFilter=department==null?"":" AND ea.department_id=?";
        List<Object> args=new ArrayList<>(List.of(date,date,date,days,date,to,from,hr,to,from));
        if(department!=null) args.add(department);
        return db.queryForObject("""
            SELECT COUNT(c.contract_id),COALESCE(SUM(CASE WHEN c.start_date<=? AND (c.end_date IS NULL OR c.end_date>=?) THEN 1 ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN c.end_date>=? AND c.end_date<=DATEADD(day,?,CAST(? AS date)) THEN 1 ELSE 0 END),0)
            FROM dbo.contracts c WHERE c.status='DA_KY' AND c.start_date<=? AND (c.end_date IS NULL OR c.end_date>=?)
              AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=c.employee_id AND
            """+GRANT+" AND ea.effective_from<=? AND (ea.effective_to IS NULL OR ea.effective_to>?) AND (c.end_date IS NULL OR ea.effective_from<=c.end_date) AND (ea.effective_to IS NULL OR ea.effective_to>c.start_date)"+departmentFilter+")",
            (rs,n)->new ContractStatisticsResponse(from,to,date,department,days,rs.getLong(1),rs.getLong(2),rs.getLong(3)),args.toArray());
    }

    private record Segment(long id, LocalDateTime start, LocalDateTime end, LocalDateTime originalStart, LocalDateTime originalEnd, int breaks) {}
    private record Totals(long count, BigDecimal hours) {}
    private Totals segments(String source, String id, String employee, String start, String end, String breaks, String predicate, long hr, LocalDateTime from, LocalDateTime to) {
        String sql="SELECT "+id+",CASE WHEN "+start+"<CAST(ea.effective_from AS datetime2) THEN CAST(ea.effective_from AS datetime2) ELSE "+start+" END,"
            +"CASE WHEN ea.effective_to IS NOT NULL AND "+end+">CAST(ea.effective_to AS datetime2) THEN CAST(ea.effective_to AS datetime2) ELSE "+end+" END,"
            +start+","+end+","+breaks+" FROM "+source+" JOIN dbo.employee_assignments ea ON ea.employee_id="+employee
            +" AND CAST(ea.effective_from AS datetime2)<"+end+" AND (ea.effective_to IS NULL OR CAST(ea.effective_to AS datetime2)>"+start+") WHERE "+predicate
            +" AND "+start+"<? AND "+end+">? AND "+GRANT;
        List<Segment> rows=db.query(sql,(rs,n)->new Segment(rs.getLong(1),rs.getObject(2,LocalDateTime.class),rs.getObject(3,LocalDateTime.class),rs.getObject(4,LocalDateTime.class),rs.getObject(5,LocalDateTime.class),rs.getInt(6)),to,from,hr);
        Set<Long> ids=new HashSet<>(); BigDecimal millis=BigDecimal.ZERO;
        for(Segment s:rows){
            LocalDateTime a=s.start().isBefore(from)?from:s.start(), b=s.end().isAfter(to)?to:s.end();
            if(!a.isBefore(b)) continue;
            long overlap=Duration.between(a,b).toMillis(), full=Duration.between(s.originalStart(),s.originalEnd()).toMillis();
            if(full<=0) continue;
            ids.add(s.id());
            BigDecimal net=BigDecimal.valueOf(full).subtract(BigDecimal.valueOf(s.breaks()).multiply(BigDecimal.valueOf(60_000))).max(BigDecimal.ZERO);
            millis=millis.add(net.multiply(BigDecimal.valueOf(overlap)).divide(BigDecimal.valueOf(full),MathContext.DECIMAL128));
        }
        return new Totals(ids.size(),millis.divide(HOUR,4,RoundingMode.HALF_UP));
    }

    public WorkStatisticsResponse work(LocalDate from, LocalDate to, boolean invalidationColumn) {
        long hr=actor();
        String invalid=invalidationColumn?" AND ar.invalidated_at IS NULL":"";
        long[] attendance=db.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN ar.attendance_result='DI_LAM' THEN CAST(1 AS bigint) ELSE 0 END),0),
                   COALESCE(SUM(CASE WHEN ar.attendance_result='VANG' THEN CAST(1 AS bigint) ELSE 0 END),0)
            FROM dbo.attendance_records ar WHERE ar.work_date BETWEEN ? AND ? AND ar.verification_status='DA_XAC_NHAN'
             AND ar.confirmed_by IS NOT NULL AND ar.confirmed_at IS NOT NULL
            """+invalid+" AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=ar.employee_id AND ea.effective_from<=ar.work_date AND (ea.effective_to IS NULL OR ea.effective_to>ar.work_date) AND "+GRANT+")",
            (rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},from,to,hr);
        LocalDateTime start=from.atStartOfDay(),end=to.plusDays(1).atStartOfDay();
        Totals leave=segments("dbo.employee_requests er JOIN dbo.leave_request_details ld ON ld.request_id=er.request_id","er.request_id","er.employee_id","ld.leave_start","ld.leave_end","0","er.request_type='NGHI_PHEP' AND er.status='DA_DUYET' AND ld.leave_end>ld.leave_start",hr,start,end);
        Totals overtime=segments("dbo.overtime_requests o","o.overtime_id","o.employee_id","o.actual_start","o.actual_end","o.actual_break_minutes","o.status='DA_DUYET' AND o.confirmed_by IS NOT NULL AND o.confirmed_at IS NOT NULL AND o.actual_end>o.actual_start",hr,start,end);
        return new WorkStatisticsResponse(from,to,attendance[0],attendance[1],leave.count(),leave.hours(),overtime.count(),overtime.hours());
    }

    public PayrollStatisticsResponse payroll(LocalDate from, LocalDate to) {
        long hr=actor();
        List<BatchSummary> batches=db.query("""
            SELECT pp.period_id,pp.period_code,pp.period_start,pp.period_end,pb.batch_id,pb.version_no,COUNT(DISTINCT pd.employee_id),
             SUM(CASE WHEN pl.direction_snapshot='ADD' THEN pl.amount ELSE 0 END),
             SUM(CASE WHEN pl.direction_snapshot='ADD' AND pl.component_code_snapshot='PHU_CAP' THEN pl.amount ELSE 0 END),
             SUM(CASE WHEN pl.direction_snapshot='ADD' AND pl.component_code_snapshot='THUONG' THEN pl.amount ELSE 0 END),
             SUM(CASE WHEN pl.direction_snapshot='DEDUCT' THEN pl.amount ELSE 0 END)
            FROM dbo.payroll_periods pp JOIN dbo.payroll_batches pb ON pb.period_id=pp.period_id
            JOIN dbo.payroll_details pd ON pd.batch_id=pb.batch_id JOIN dbo.payroll_lines pl ON pl.payroll_detail_id=pd.payroll_detail_id
            WHERE pp.period_start>=? AND pp.period_end<=? AND pb.status='DA_CHOT' AND pb.locked_by IS NOT NULL AND pb.locked_at IS NOT NULL
              AND NOT EXISTS(SELECT 1 FROM dbo.payroll_batches newer WHERE newer.period_id=pb.period_id AND newer.status='DA_CHOT' AND newer.locked_by IS NOT NULL AND newer.locked_at IS NOT NULL AND newer.version_no>pb.version_no)
              AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=pd.employee_id
                  AND ea.effective_from<=pp.period_start AND (ea.effective_to IS NULL OR ea.effective_to>=pp.period_end) AND
            """+GRANT+") GROUP BY pp.period_id,pp.period_code,pp.period_start,pp.period_end,pb.batch_id,pb.version_no ORDER BY pp.period_start,pp.period_id",
            (rs,n)->new BatchSummary(rs.getLong(1),rs.getString(2),rs.getDate(3).toLocalDate(),rs.getDate(4).toLocalDate(),rs.getLong(5),rs.getInt(6),rs.getLong(7),amounts(rs.getBigDecimal(8),rs.getBigDecimal(9),rs.getBigDecimal(10),rs.getBigDecimal(11))),from,to.plusDays(1),hr);
        BigDecimal gross=BigDecimal.ZERO,allowances=BigDecimal.ZERO,bonuses=BigDecimal.ZERO,deductions=BigDecimal.ZERO;long count=0;
        for(var batch:batches){var a=batch.totals();gross=gross.add(a.grossPayroll());allowances=allowances.add(a.allowances());bonuses=bonuses.add(a.bonuses());deductions=deductions.add(a.deductions());count+=batch.employeeCount();}
        return new PayrollStatisticsResponse(from,to,batches.size(),count,amounts(gross,allowances,bonuses,deductions),List.copyOf(batches));
    }
    private static Amounts amounts(BigDecimal gross,BigDecimal allowances,BigDecimal bonuses,BigDecimal deductions){return new Amounts(gross,allowances,bonuses,deductions,gross.subtract(deductions));}
}
