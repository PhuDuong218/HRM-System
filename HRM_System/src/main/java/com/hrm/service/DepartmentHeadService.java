package com.hrm.service;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.hrm.dto.*;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.util.PagingUtils;

/** JDBC shares the Spring transaction with JPA; SQL Server owns rowversion values. */
@Service
@Transactional(readOnly=true)
public class DepartmentHeadService {
    private final JdbcTemplate jdbc;
    private final AssignmentGuardService guard;
    private final DepartmentHeadAccessService access;
    private final Clock clock;
    private static final String HEAD_SELECT = """
        SELECT a.assignment_id,a.department_id,a.employee_id,e.employee_code,e.full_name,
               a.effective_from,a.effective_to
        FROM dbo.employee_assignments a
        JOIN dbo.positions p ON p.position_id=a.position_id
        JOIN dbo.employees e ON e.employee_id=a.employee_id
        WHERE p.position_code='TRUONG_PHONG'
        """;
    private static final RowMapper<DepartmentHeadDTO> HEAD_MAPPER = (r,n) -> new DepartmentHeadDTO(
        r.getLong("assignment_id"),r.getLong("department_id"),r.getLong("employee_id"),
        r.getString("employee_code"),r.getString("full_name"),
        r.getObject("effective_from",LocalDate.class),r.getObject("effective_to",LocalDate.class));

    public DepartmentHeadService(JdbcTemplate jdbc, AssignmentGuardService guard,
            DepartmentHeadAccessService access, @Qualifier("hrmBusinessClock") Clock clock) {
        this.jdbc=jdbc; this.guard=guard; this.access=access; this.clock=clock;
    }

    public DepartmentHeadDTO getHead(Long departmentId, LocalDate asOf, Principal principal) {
        access.requireManager(principal);
        requireDepartment(departmentId, false);
        return headAt(departmentId, asOf == null ? LocalDate.now(clock) : asOf);
    }

    public PageResponse<DepartmentHeadDTO> history(Long departmentId, int page, int size, Principal principal) {
        access.requireManager(principal);
        requireDepartment(departmentId, false);
        PagingUtils.create(page,size,org.springframework.data.domain.Sort.unsorted());
        long total=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id WHERE a.department_id=? AND p.position_code='TRUONG_PHONG'",Long.class,departmentId);
        var items=jdbc.query(HEAD_SELECT+" AND a.department_id=? ORDER BY a.effective_from DESC,a.assignment_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                HEAD_MAPPER,departmentId,(long)page*size,size);
        return new PageResponse<>(items,page,size,total,(int)((total+size-1)/size));
    }

    public PageResponse<EmployeeAssignmentDTO> candidates(Long departmentId, int page, int size, Principal principal) {
        access.requireManager(principal);
        requireDepartment(departmentId, true);
        PagingUtils.create(page,size,org.springframework.data.domain.Sort.unsorted());
        LocalDate today=LocalDate.now(clock);
        String from="""
            FROM dbo.employee_assignments a JOIN dbo.employees e ON e.employee_id=a.employee_id
            JOIN dbo.departments d ON d.department_id=a.department_id
            JOIN dbo.positions p ON p.position_id=a.position_id
            WHERE a.department_id=? AND a.effective_to IS NULL AND a.effective_from<=?
              AND e.employment_status='DANG_LAM' AND e.hire_date<=?
              AND p.position_code<>'TRUONG_PHONG' AND p.is_active=1
            """;
        long total=jdbc.queryForObject("SELECT COUNT_BIG(*) "+from,Long.class,departmentId,today,today);
        var items=jdbc.query("SELECT a.*,e.employee_code,e.full_name,d.department_name,p.position_name "+from+
                " ORDER BY e.employee_code,a.assignment_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (r,n)->new EmployeeAssignmentDTO(r.getLong("assignment_id"),r.getLong("employee_id"),
                    r.getString("employee_code"),r.getString("full_name"),r.getLong("department_id"),
                    r.getString("department_name"),r.getLong("position_id"),r.getString("position_name"),
                    r.getObject("effective_from",LocalDate.class),r.getObject("effective_to",LocalDate.class)),
                departmentId,today,today,(long)page*size,size);
        return new PageResponse<>(items,page,size,total,(int)((total+size-1)/size));
    }

    @Transactional
    public DepartmentHeadDTO appoint(Long departmentId, DepartmentHeadRequest request, Principal principal) {
        Long actor=access.requireManager(principal);
        guard.lockTimeline();
        requireDepartment(departmentId,true);
        validateDate(request.effectiveFrom());
        Assignment candidate=requireAssignment(request.employeeId(),departmentId,request.effectiveFrom());
        Long headPosition=activeHeadPosition();
        if (candidate.positionId().equals(headPosition)) throw conflict("Nhân viên này đang là trưởng phòng");
        // An open future head must not be silently overwritten by a backdated appointment.
        var existing=openHeads(departmentId);
        if (existing.size()>1) throw conflict("Dữ liệu có nhiều trưởng phòng mở; cần đối soát trước");
        DepartmentHeadDTO old=existing.isEmpty()?null:existing.get(0);
        if (old!=null) {
            if (Objects.equals(old.employeeId(),request.employeeId())) throw conflict("Nhân viên này đang là trưởng phòng");
            if (request.previousHeadPositionId()==null) throw bad("Phải chọn chức vụ tiếp theo cho trưởng phòng cũ");
            requireNonHeadPosition(request.previousHeadPositionId());
            Assignment previous=requireAssignment(old.employeeId(),departmentId,request.effectiveFrom());
            change(previous,request.previousHeadPositionId(),request.effectiveFrom(),actor);
        } else if(request.previousHeadPositionId()!=null) {
            throw bad("Phòng chưa có trưởng phòng; không gửi chức vụ của người cũ");
        }
        change(candidate,headPosition,request.effectiveFrom(),actor);
        DepartmentHeadDTO result=headAt(departmentId,request.effectiveFrom());
        audit(actor,departmentId,old,result,request.reason(),"DEPARTMENT_HEAD_APPOINT");
        return result;
    }

    @Transactional
    public void end(Long departmentId, DepartmentHeadEndRequest request, Principal principal) {
        Long actor=access.requireManager(principal);
        guard.lockTimeline();
        requireDepartment(departmentId,false);
        validateDate(request.effectiveFrom());
        requireNonHeadPosition(request.nextPositionId());
        var heads=openHeads(departmentId);
        if(heads.size()!=1) throw conflict("Phòng phải có đúng một trưởng phòng đang đảm nhiệm");
        var old=heads.get(0);
        change(requireAssignment(old.employeeId(),departmentId,request.effectiveFrom()),
                request.nextPositionId(),request.effectiveFrom(),actor);
        audit(actor,departmentId,old,null,request.reason(),"DEPARTMENT_HEAD_END");
    }

    private record Assignment(Long id, Long employeeId, Long departmentId, Long positionId, LocalDate start) {}

    private Assignment requireAssignment(Long employeeId, Long departmentId, LocalDate date) {
        var employees=jdbc.queryForList("SELECT employment_status,hire_date FROM dbo.employees WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=?",employeeId);
        if(employees.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy nhân viên: "+employeeId);
        if(!"DANG_LAM".equals(employees.get(0).get("employment_status"))) throw conflict("Nhân viên đã nghỉ việc");
        var rows=jdbc.query("""
            SELECT assignment_id,employee_id,department_id,position_id,effective_from
            FROM dbo.employee_assignments WITH (UPDLOCK,HOLDLOCK)
            WHERE employee_id=? AND effective_to IS NULL
            """,(r,n)->new Assignment(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getObject(5,LocalDate.class)),employeeId);
        if(rows.size()!=1 || !rows.get(0).departmentId().equals(departmentId)) throw conflict("Nhân viên phải có một phân công mở thuộc phòng này");
        Assignment a=rows.get(0);
        if(!date.isAfter(a.start())) throw bad("Ngày hiệu lực phải sau ngày bắt đầu phân công hiện tại; không tạo khoảng rỗng");
        return a;
    }

    private void change(Assignment old, Long position, LocalDate date, Long actor) {
        jdbc.update("UPDATE dbo.employee_assignments SET effective_to=?,updated_at=SYSUTCDATETIME(),updated_by=? WHERE assignment_id=?",date,actor,old.id());
        jdbc.update("""
            INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from,created_by)
            VALUES (?,?,?,?,?)
            """,old.employeeId(),old.departmentId(),position,date,actor);
    }

    private void requireDepartment(Long id, boolean active) {
        var flags=jdbc.queryForList("SELECT is_active FROM dbo.departments WHERE department_id=?",Boolean.class,id);
        if(flags.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy phòng ban: "+id);
        if(active && !Boolean.TRUE.equals(flags.get(0))) throw conflict("Phòng ban đã ngừng hoạt động");
    }

    private Long activeHeadPosition() {
        var ids=jdbc.queryForList("SELECT position_id FROM dbo.positions WHERE position_code='TRUONG_PHONG' AND is_active=1",Long.class);
        if(ids.size()!=1) throw conflict("Chưa có chức vụ TRUONG_PHONG đang hoạt động; hãy chạy migration");
        return ids.get(0);
    }

    private void requireNonHeadPosition(Long id) {
        var codes=jdbc.queryForList("SELECT position_code FROM dbo.positions WHERE position_id=? AND is_active=1",String.class,id);
        if(codes.isEmpty()) throw new ResourceNotFoundException("Chức vụ tiếp theo không tồn tại hoặc đã ngừng hoạt động");
        if(AssignmentGuardService.HEAD_CODE.equals(codes.get(0))) throw bad("Chức vụ tiếp theo phải khác TRUONG_PHONG");
    }

    private void validateDate(LocalDate date) {
        if(date==null || date.isAfter(LocalDate.now(clock))) throw bad("Ngày hiệu lực bắt buộc và không được ở tương lai");
    }

    private List<DepartmentHeadDTO> openHeads(Long departmentId) {
        return jdbc.query(HEAD_SELECT+" AND a.department_id=? AND a.effective_to IS NULL",HEAD_MAPPER,departmentId);
    }

    private DepartmentHeadDTO headAt(Long departmentId, LocalDate date) {
        var rows=jdbc.query(HEAD_SELECT+" AND a.department_id=? AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?)",HEAD_MAPPER,departmentId,date,date);
        if(rows.size()>1) throw conflict("Dữ liệu trưởng phòng bị chồng hiệu lực");
        return rows.isEmpty()?null:rows.get(0);
    }

    private void audit(Long actor,Long departmentId,DepartmentHeadDTO before,DepartmentHeadDTO after,String reason,String action) {
        if(reason==null || reason.isBlank() || reason.length()>1000) throw bad("Lý do bắt buộc, tối đa 1000 ký tự");
        // Snapshot only contains server-generated numeric IDs and dates, no interpolated user text.
        jdbc.update("""
            INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json,correlation_id)
            VALUES (?,?,'departments',?,?,?,?,NEWID())
            """,actor,action,departmentId.toString(),reason.trim(),snapshot(before),snapshot(after));
    }

    private String snapshot(DepartmentHeadDTO h) {
        return h==null?null:"{\"assignmentId\":"+h.assignmentId()+",\"employeeId\":"+h.employeeId()+",\"effectiveFrom\":\""+h.effectiveFrom()+"\"}";
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
