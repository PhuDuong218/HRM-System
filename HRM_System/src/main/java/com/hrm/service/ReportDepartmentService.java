package com.hrm.service;

import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.dto.PayrollStatisticsResponse;
import com.hrm.dto.WorkStatisticsResponse;
import com.hrm.dto.PageResponse;
import com.hrm.repository.projection.EmployeeStatisticsRow;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Read-only report calculations for a selected department. */
@Service
@Transactional(readOnly = true)
public class ReportDepartmentService {
    private final NamedParameterJdbcTemplate db;
    public ReportDepartmentService(NamedParameterJdbcTemplate db) { this.db = db; }

    private record Group(Long groupId, String groupCode, String groupName, Long employeeCount) implements EmployeeStatisticsRow {
        public Long getGroupId() { return groupId; }
        public String getGroupCode() { return groupCode; }
        public String getGroupName() { return groupName; }
        public Long getEmployeeCount() { return employeeCount; }
    }
    private static final String CURRENT = """
        WITH effective AS (SELECT ea.employee_id,ea.department_id,ea.position_id,
          ROW_NUMBER() OVER(PARTITION BY ea.employee_id ORDER BY ea.effective_from DESC,ea.assignment_id DESC) rn
          FROM dbo.employee_assignments ea WHERE ea.effective_from<=:today AND (ea.effective_to IS NULL OR ea.effective_to>:today))
        """;
    private List<EmployeeStatisticsRow> groups(String sql, MapSqlParameterSource p) {
        return db.query(sql,p,(r,n)->new Group(r.getObject(1,Long.class),r.getString(2),r.getString(3),r.getLong(4)));
    }
    public EmployeeStatisticsResponse employees(long departmentId) {
        requireDepartment(departmentId);
        var p = new MapSqlParameterSource("department",departmentId).addValue("today",LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        String base=" FROM dbo.employees e JOIN effective a ON a.employee_id=e.employee_id AND a.rn=1 AND a.department_id=:department ";
        var departments=groups(CURRENT+"SELECT d.department_id,d.department_code,d.department_name,COUNT(DISTINCT e.employee_id)"+base+"JOIN dbo.departments d ON d.department_id=a.department_id GROUP BY d.department_id,d.department_code,d.department_name",p);
        var positions=groups(CURRENT+"SELECT pos.position_id,COALESCE(pos.position_code,'CHUA_PHAN_CONG'),COALESCE(pos.position_name,N'Chưa có chức vụ'),COUNT(DISTINCT e.employee_id)"+base+"LEFT JOIN dbo.positions pos ON pos.position_id=a.position_id GROUP BY pos.position_id,pos.position_code,pos.position_name",p);
        var statuses=groups(CURRENT+"SELECT CAST(NULL AS bigint),e.employment_status,e.employment_status,COUNT(DISTINCT e.employee_id)"+base+"GROUP BY e.employment_status",p);
        var qualifications=groups(CURRENT+"SELECT q.qualification_level_id,q.level_code,q.level_name,COUNT(DISTINCT e.employee_id)"+base+"JOIN dbo.employee_qualifications eq ON eq.employee_id=e.employee_id AND eq.is_primary=1 JOIN dbo.qualification_levels q ON q.qualification_level_id=eq.qualification_level_id GROUP BY q.qualification_level_id,q.level_code,q.level_name",p);
        return new EmployeeStatisticsResponse(departments,positions,statuses,qualifications);
    }

    public record EmployeeDetail(long employeeId, String employeeCode, String employeeName,
            String departmentName, String positionName, String employmentStatus, LocalDate hireDate) {}

    public PageResponse<EmployeeDetail> employeeDetails(String groupBy, Long groupId, String groupCode,
            Long department, int page, int size) {
        validatePage(page, size);
        if (department != null) requireDepartment(department);
        if (groupId != null && groupId < 1) throw new IllegalArgumentException("Nhóm nhân sự không hợp lệ");
        String condition = switch (groupBy) {
            case "department" -> groupId == null ? "d.department_id IS NULL" : "d.department_id=:groupId";
            case "position" -> groupId == null ? "pos.position_id IS NULL" : "pos.position_id=:groupId";
            case "status" -> {
                if (!List.of("DANG_LAM", "DA_NGHI").contains(groupCode == null ? "" : groupCode))
                    throw new IllegalArgumentException("Trạng thái nhân sự không hợp lệ");
                yield "e.employment_status=:groupCode";
            }
            case "qualification" -> {
                if (groupId == null) throw new IllegalArgumentException("Thiếu nhóm trình độ");
                yield "EXISTS(SELECT 1 FROM dbo.employee_qualifications eq JOIN dbo.qualification_levels q ON q.qualification_level_id=eq.qualification_level_id WHERE eq.employee_id=e.employee_id AND eq.is_primary=1 AND q.qualification_level_id=:groupId)";
            }
            default -> throw new IllegalArgumentException("Cách nhóm nhân sự không hợp lệ");
        };
        var p = new MapSqlParameterSource("today", LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))
                .addValue("groupId", groupId).addValue("groupCode", groupCode).addValue("department", department)
                .addValue("offset", (long) page * size).addValue("size", size);
        String base = """
            FROM dbo.employees e LEFT JOIN effective a ON a.employee_id=e.employee_id AND a.rn=1
            LEFT JOIN dbo.departments d ON d.department_id=a.department_id
            LEFT JOIN dbo.positions pos ON pos.position_id=a.position_id
            WHERE
            """ + condition + (department == null ? "" : " AND a.department_id=:department");
        long total = db.queryForObject(CURRENT + "SELECT COUNT(*) " + base, p, Long.class);
        var rows = db.query(CURRENT + "SELECT e.employee_id,e.employee_code,e.full_name,d.department_name,pos.position_name,e.employment_status,e.hire_date "
                + base + " ORDER BY e.employee_code,e.employee_id OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY", p,
                (r,n) -> new EmployeeDetail(r.getLong(1), r.getString(2), r.getString(3), r.getString(4),
                        r.getString(5), r.getString(6), r.getDate(7).toLocalDate()));
        return new PageResponse<>(rows, page, size, total, totalPages(total, size));
    }

    public record PayrollEmployee(long payrollDetailId, long employeeId, String employeeCode,
            String employeeName, PayrollStatisticsResponse.Amounts totals) {}
    public record PayrollLine(long lineId, String componentCode, String componentName, String direction,
            BigDecimal amount, String explanation) {}

    // Use the selected immutable batch, never switch to another version while opening details.
    private String payrollScope(Long department) {
        return """
            FROM dbo.payroll_periods pp JOIN dbo.payroll_batches pb ON pb.period_id=pp.period_id
            JOIN dbo.payroll_details pd ON pd.batch_id=pb.batch_id
            JOIN dbo.payroll_lines pl ON pl.payroll_detail_id=pd.payroll_detail_id
            JOIN dbo.employees e ON e.employee_id=pd.employee_id
            WHERE pb.batch_id=:batchId AND pb.status='DA_CHOT' AND pb.locked_by IS NOT NULL AND pb.locked_at IS NOT NULL
              AND pp.period_start>=:fromDate AND pp.period_end<=:toDate
            """ + (department == null ? "" : """
              AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=pd.employee_id
                AND ea.department_id=:department AND ea.effective_from<=pp.period_start
                AND (ea.effective_to IS NULL OR ea.effective_to>pp.period_end))
            """);
    }

    private MapSqlParameterSource payrollDetailParams(LocalDate from, LocalDate to, long batchId, Long department) {
        validatePayrollDates(from, to);
        if (batchId < 1) throw new IllegalArgumentException("ID bảng lương phải lớn hơn 0");
        if (department != null) requireDepartment(department);
        return params(from, to, department).addValue("batchId", batchId);
    }

    public PageResponse<PayrollEmployee> payrollEmployees(LocalDate from, LocalDate to, long batchId,
            Long department, int page, int size) {
        validatePage(page, size);
        var p = payrollDetailParams(from, to, batchId, department)
                .addValue("offset", (long) page * size).addValue("size", size);
        String base = payrollScope(department);
        long total = db.queryForObject("SELECT COUNT(DISTINCT pd.payroll_detail_id) " + base, p, Long.class);
        var rows = db.query("""
            SELECT pd.payroll_detail_id,pd.employee_id,e.employee_code,e.full_name,
              SUM(CASE WHEN pl.direction_snapshot='ADD' THEN pl.amount ELSE 0 END),
              SUM(CASE WHEN pl.direction_snapshot='ADD' AND pl.component_code_snapshot='PHU_CAP' THEN pl.amount ELSE 0 END),
              SUM(CASE WHEN pl.direction_snapshot='ADD' AND pl.component_code_snapshot='THUONG' THEN pl.amount ELSE 0 END),
              SUM(CASE WHEN pl.direction_snapshot='DEDUCT' THEN pl.amount ELSE 0 END)
            """ + base + " GROUP BY pd.payroll_detail_id,pd.employee_id,e.employee_code,e.full_name"
                + " ORDER BY e.employee_code,pd.payroll_detail_id OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY", p,
                (r,n) -> new PayrollEmployee(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4),
                        amounts(r.getBigDecimal(5), r.getBigDecimal(6), r.getBigDecimal(7), r.getBigDecimal(8))));
        return new PageResponse<>(rows, page, size, total, totalPages(total, size));
    }

    public List<PayrollLine> payrollLines(LocalDate from, LocalDate to, long batchId, long detailId, Long department) {
        if (detailId < 1) throw new IllegalArgumentException("ID chi tiết lương phải lớn hơn 0");
        var p = payrollDetailParams(from, to, batchId, department).addValue("detailId", detailId);
        var rows = db.query("SELECT pl.payroll_line_id,pl.component_code_snapshot,pl.component_name_snapshot,pl.direction_snapshot,pl.amount,pl.explanation "
                + payrollScope(department) + " AND pd.payroll_detail_id=:detailId ORDER BY pl.line_no,pl.payroll_line_id", p,
                (r,n) -> new PayrollLine(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getBigDecimal(5), r.getString(6)));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dòng lương trong phiên bản và phạm vi đã chọn");
        return rows;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Phân trang không hợp lệ");
    }
    private int totalPages(long total, int size) { return (int) ((total + size - 1) / size); }

    public record Movement(String month, long hired, long terminated) {}
    public List<Movement> movements(LocalDate from, LocalDate to, Long department) {
        validate(from,to); if(department!=null)requireDepartment(department);
        var p=params(from,to,department);
        String condition=department==null?"":" AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=e.employee_id AND ea.department_id=:department AND ea.effective_from<=events.event_date AND (ea.effective_to IS NULL OR ea.effective_to>events.event_date))";
        return db.query("""
            SELECT CONVERT(char(7),events.event_date,126),SUM(CASE WHEN events.kind='H' THEN 1 ELSE 0 END),SUM(CASE WHEN events.kind='T' THEN 1 ELSE 0 END)
            FROM dbo.employees e CROSS APPLY (VALUES (e.hire_date,'H'),(e.termination_date,'T')) events(event_date,kind)
            WHERE events.event_date BETWEEN :fromDate AND :toDate
            """+condition+" GROUP BY CONVERT(char(7),events.event_date,126) ORDER BY 1",p,
            (r,n)->new Movement(r.getString(1),r.getLong(2),r.getLong(3)));
    }

    public record Department(long id, String code, String name) {}
    public List<Department> departments() {
        return db.query("SELECT department_id,department_code,department_name FROM dbo.departments ORDER BY department_name", (r,n)->new Department(r.getLong(1),r.getString(2),r.getString(3)));
    }
    public record ContractDetail(long contractId, String employeeCode, String employeeName, LocalDate startDate, LocalDate endDate, String status) {}
    public PageResponse<ContractDetail> contracts(LocalDate from,LocalDate to,LocalDate reportDate,int days,Long department,String kind,int page,int size) {
        validate(from,to); if(reportDate==null||reportDate.getYear()<1||days<0||days>3650||reportDate.plusDays(days).isAfter(LocalDate.of(9999,12,31))||page<0||size<1||size>100)throw new IllegalArgumentException("Bộ lọc hợp đồng không hợp lệ");
        if(department!=null)requireDepartment(department);
        if(!List.of("expiring","expired").contains(kind))throw new IllegalArgumentException("Loại hợp đồng không hợp lệ");
        var p=params(from,to,department).addValue("reportDate",reportDate).addValue("until",reportDate.plusDays(days)).addValue("offset",(long)page*size).addValue("size",size);
        String condition=kind.equals("expiring")?"c.status='DA_KY' AND c.end_date>=:reportDate AND c.end_date<=:until":"c.status IN ('DA_KY','DA_KET_THUC') AND c.end_date<:reportDate";
        String dept=department==null?"":" AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=c.employee_id AND ea.department_id=:department AND ea.effective_from<=:toDate AND (ea.effective_to IS NULL OR ea.effective_to>:fromDate) AND (c.end_date IS NULL OR ea.effective_from<=c.end_date) AND (ea.effective_to IS NULL OR ea.effective_to>c.start_date))";
        String base=" FROM dbo.contracts c JOIN dbo.employees e ON e.employee_id=c.employee_id WHERE "+condition+" AND c.start_date<=:toDate AND (c.end_date IS NULL OR c.end_date>=:fromDate)"+dept;
        Long total=db.queryForObject("SELECT COUNT(*)"+base,p,Long.class);
        var items=db.query("SELECT c.contract_id,e.employee_code,e.full_name,c.start_date,c.end_date,c.status"+base+" ORDER BY c.end_date,c.contract_id OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",p,
            (r,n)->new ContractDetail(r.getLong(1),r.getString(2),r.getString(3),r.getDate(4).toLocalDate(),r.getDate(5).toLocalDate(),r.getString(6)));
        long count=total==null?0:total;
        return new PageResponse<>(items,page,size,count,(int)Math.min(Integer.MAX_VALUE,(count+size-1)/size));
    }

    private record Segment(long id, LocalDateTime start, LocalDateTime end, LocalDateTime originalStart, LocalDateTime originalEnd, int breaks) {}
    private record Totals(long count, BigDecimal hours) {}
    private Totals segments(String source,String id,String employee,String start,String end,String breaks,String predicate,MapSqlParameterSource p) {
        String sql="SELECT "+id+",CASE WHEN "+start+"<CAST(ea.effective_from AS datetime2) THEN CAST(ea.effective_from AS datetime2) ELSE "+start+" END,"
          +"CASE WHEN ea.effective_to IS NOT NULL AND "+end+">CAST(ea.effective_to AS datetime2) THEN CAST(ea.effective_to AS datetime2) ELSE "+end+" END,"
          +start+","+end+","+breaks+" FROM "+source+" JOIN dbo.employee_assignments ea ON ea.employee_id="+employee
          +" AND ea.department_id=:department AND CAST(ea.effective_from AS datetime2)<"+end+" AND (ea.effective_to IS NULL OR CAST(ea.effective_to AS datetime2)>"+start+") WHERE "+predicate
          +" AND "+start+"<:endExclusive AND "+end+">:startInclusive";
        var rows=db.query(sql,p,(r,n)->new Segment(r.getLong(1),r.getObject(2,LocalDateTime.class),r.getObject(3,LocalDateTime.class),r.getObject(4,LocalDateTime.class),r.getObject(5,LocalDateTime.class),r.getInt(6)));
        Set<Long> ids=new HashSet<>(); BigDecimal millis=BigDecimal.ZERO;
        LocalDateTime from=(LocalDateTime)p.getValue("startInclusive"),to=(LocalDateTime)p.getValue("endExclusive");
        for(var s:rows){var a=s.start().isBefore(from)?from:s.start();var b=s.end().isAfter(to)?to:s.end();if(!a.isBefore(b))continue;long overlap=Duration.between(a,b).toMillis(),full=Duration.between(s.originalStart(),s.originalEnd()).toMillis();if(full<=0)continue;ids.add(s.id());var net=BigDecimal.valueOf(full).subtract(BigDecimal.valueOf(s.breaks()).multiply(BigDecimal.valueOf(60_000))).max(BigDecimal.ZERO);millis=millis.add(net.multiply(BigDecimal.valueOf(overlap)).divide(BigDecimal.valueOf(full),MathContext.DECIMAL128));}
        return new Totals(ids.size(),millis.divide(BigDecimal.valueOf(3_600_000),4,RoundingMode.HALF_UP));
    }
    public WorkStatisticsResponse work(LocalDate from,LocalDate to,long department) {
        validate(from,to);requireDepartment(department);
        var p=params(from,to,department).addValue("startInclusive",from.atStartOfDay()).addValue("endExclusive",to.plusDays(1).atStartOfDay());
        Integer invalidColumn=db.getJdbcTemplate().queryForObject("SELECT COL_LENGTH('dbo.attendance_records','invalidated_at')",Integer.class);
        String invalid=invalidColumn==null?"":" AND ar.invalidated_at IS NULL";
        long[] attendance=db.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN ar.attendance_result='DI_LAM' THEN CAST(1 AS bigint) ELSE 0 END),0),
                   COALESCE(SUM(CASE WHEN ar.attendance_result='VANG' THEN CAST(1 AS bigint) ELSE 0 END),0)
            FROM dbo.attendance_records ar WHERE ar.work_date BETWEEN :fromDate AND :toDate
             AND ar.verification_status='DA_XAC_NHAN' AND ar.confirmed_by IS NOT NULL AND ar.confirmed_at IS NOT NULL
            """+invalid+" AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=ar.employee_id AND ea.department_id=:department AND ea.effective_from<=ar.work_date AND (ea.effective_to IS NULL OR ea.effective_to>ar.work_date))",p,(r,n)->new long[]{r.getLong(1),r.getLong(2)});
        Totals leave=segments("dbo.employee_requests er JOIN dbo.leave_request_details ld ON ld.request_id=er.request_id","er.request_id","er.employee_id","ld.leave_start","ld.leave_end","0","er.request_type='NGHI_PHEP' AND er.status='DA_DUYET' AND ld.leave_end>ld.leave_start AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=er.request_id)",p);
        Totals overtime=segments("dbo.overtime_requests o","o.overtime_id","o.employee_id","o.actual_start","o.actual_end","o.actual_break_minutes","o.status='DA_DUYET' AND o.confirmed_by IS NOT NULL AND o.confirmed_at IS NOT NULL AND o.actual_end>o.actual_start",p);
        return new WorkStatisticsResponse(from,to,attendance[0],attendance[1],leave.count(),leave.hours(),overtime.count(),overtime.hours());
    }
    public PayrollStatisticsResponse payroll(LocalDate from,LocalDate to,long department) {
        validatePayrollDates(from,to);requireDepartment(department);
        var p=params(from,to,department);
        var batches=db.query("""
          SELECT pp.period_id,pp.period_code,pp.period_start,pp.period_end,pb.batch_id,pb.version_no,COUNT(DISTINCT pd.employee_id),
           SUM(CASE WHEN pl.direction_snapshot='ADD' THEN pl.amount ELSE 0 END),
           SUM(CASE WHEN pl.direction_snapshot='ADD' AND pl.component_code_snapshot='PHU_CAP' THEN pl.amount ELSE 0 END),
           SUM(CASE WHEN pl.direction_snapshot='ADD' AND pl.component_code_snapshot='THUONG' THEN pl.amount ELSE 0 END),
           SUM(CASE WHEN pl.direction_snapshot='DEDUCT' THEN pl.amount ELSE 0 END)
          FROM dbo.payroll_periods pp JOIN dbo.payroll_batches pb ON pb.period_id=pp.period_id
          JOIN dbo.payroll_details pd ON pd.batch_id=pb.batch_id JOIN dbo.payroll_lines pl ON pl.payroll_detail_id=pd.payroll_detail_id
          WHERE pp.period_start>=:fromDate AND pp.period_end<=:toDate AND pb.status='DA_CHOT' AND pb.locked_by IS NOT NULL AND pb.locked_at IS NOT NULL
           AND NOT EXISTS(SELECT 1 FROM dbo.payroll_batches newer WHERE newer.period_id=pb.period_id AND newer.status='DA_CHOT' AND newer.locked_by IS NOT NULL AND newer.locked_at IS NOT NULL AND newer.version_no>pb.version_no)
           AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=pd.employee_id AND ea.department_id=:department AND ea.effective_from<=pp.period_start AND (ea.effective_to IS NULL OR ea.effective_to>pp.period_end))
          GROUP BY pp.period_id,pp.period_code,pp.period_start,pp.period_end,pb.batch_id,pb.version_no ORDER BY pp.period_start,pp.period_id
          """,p,(r,n)->new PayrollStatisticsResponse.BatchSummary(r.getLong(1),r.getString(2),r.getDate(3).toLocalDate(),r.getDate(4).toLocalDate(),r.getLong(5),r.getInt(6),r.getLong(7),amounts(r.getBigDecimal(8),r.getBigDecimal(9),r.getBigDecimal(10),r.getBigDecimal(11))));
        BigDecimal gross=BigDecimal.ZERO,allowances=BigDecimal.ZERO,bonuses=BigDecimal.ZERO,deductions=BigDecimal.ZERO;long count=0;
        for(var batch:batches){var a=batch.totals();gross=gross.add(a.grossPayroll());allowances=allowances.add(a.allowances());bonuses=bonuses.add(a.bonuses());deductions=deductions.add(a.deductions());count+=batch.employeeCount();}
        return new PayrollStatisticsResponse(from,to,batches.size(),count,amounts(gross,allowances,bonuses,deductions),List.copyOf(batches));
    }
    private PayrollStatisticsResponse.Amounts amounts(BigDecimal gross,BigDecimal allowances,BigDecimal bonuses,BigDecimal deductions){return new PayrollStatisticsResponse.Amounts(gross,allowances,bonuses,deductions,gross.subtract(deductions));}
    private MapSqlParameterSource params(LocalDate from,LocalDate to,Long department){return new MapSqlParameterSource("fromDate",from).addValue("toDate",to).addValue("department",department);}
    private void requireDepartment(long department){if(department<1)throw new IllegalArgumentException("ID phòng ban phải lớn hơn 0");}
    private void validatePayrollDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || from.getYear() < 1 || to.isAfter(LocalDate.of(9999,12,31)))
            throw new IllegalArgumentException("Khoảng ngày báo cáo lương phải nằm từ 0001-01-01 đến 9999-12-31");
    }
    private void validate(LocalDate from,LocalDate to){if(from==null||to==null||from.isAfter(to)||from.getYear()<1||to.isAfter(LocalDate.of(9999,12,30)))throw new IllegalArgumentException("Khoảng ngày báo cáo không hợp lệ");}
}
