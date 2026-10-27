package com.hrm.service;

import java.time.*;
import java.util.*;
import com.hrm.dto.*;
import com.hrm.model.EmployeeAssignment;
import com.hrm.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeTransferAuditService {
    private final EmployeeAssignmentRepository assignments;
    private final EmployeeAssignmentService service;
    private final EmployeeAuditService audit;
    private final AssignmentGuardService guard;
    private final Clock clock;
    private final EmployeeRepository employees;

    public EmployeeTransferAuditService(EmployeeAssignmentRepository assignments,
            EmployeeAssignmentService service, EmployeeAuditService audit,
            AssignmentGuardService guard, @Qualifier("hrmBusinessClock") Clock clock, EmployeeRepository employees) {
        this.assignments=assignments; this.service=service; this.audit=audit; this.guard=guard; this.clock=clock;
        this.employees=employees;
    }

    @Transactional
    public EmployeeAssignmentDTO transfer(Long employeeId, EmployeeTransferRequestDTO request, Long actor) {
        if(actor==null || actor<=0) throw new IllegalArgumentException("Thiếu tài khoản điều chuyển");
        guard.lockTimeline();
        employees.findByIdForUpdate(employeeId).orElseThrow(()->new com.hrm.exception.ResourceNotFoundException("Không tìm thấy nhân viên: "+employeeId));
        var current=assignments.findCurrentAssignments(employeeId,LocalDate.now(clock));
        if(current.size()!=1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Nhân viên phải có đúng một phân công hiện tại");
        }
        var old=current.get(0);
        var before=snapshot(old);
        var result=service.transferEmployee(employeeId,request);
        old.setUpdatedBy(actor);
        var next=assignments.findById(result.assignmentId()).orElseThrow();
        next.setCreatedBy(actor);
        assignments.flush();
        audit.recordTransfer(actor,employeeId,before,snapshot(next));
        return result;
    }

    private Map<String,String> snapshot(EmployeeAssignment assignment) {
        Map<String,String> data=new LinkedHashMap<>();
        data.put("assignmentId",String.valueOf(assignment.getAssignmentId()));
        data.put("departmentId",String.valueOf(assignment.getDepartment().getDepartmentId()));
        data.put("positionId",String.valueOf(assignment.getPosition().getPositionId()));
        data.put("effectiveFrom",assignment.getEffectiveFrom().toString());
        data.put("effectiveTo",assignment.getEffectiveTo()==null?null:assignment.getEffectiveTo().toString());
        return data;
    }
}
