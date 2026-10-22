package com.hrm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hrm.model.EmployeeAssignment;

public interface EmployeeAssignmentRepository
        extends JpaRepository<EmployeeAssignment, Long> {

    @Query("""
            SELECT ea
            FROM EmployeeAssignment ea
            JOIN FETCH ea.employee
            JOIN FETCH ea.department
            JOIN FETCH ea.position
            ORDER BY ea.employee.employeeId, ea.effectiveFrom DESC
            """)
    List<EmployeeAssignment> findAllWithDetails();

    @Query("""
            SELECT ea
            FROM EmployeeAssignment ea
            JOIN FETCH ea.employee
            JOIN FETCH ea.department
            JOIN FETCH ea.position
            WHERE ea.employee.employeeId = :employeeId
            ORDER BY ea.effectiveFrom DESC, ea.assignmentId DESC
            """)
    List<EmployeeAssignment> findHistoryByEmployeeId(
            @Param("employeeId") Long employeeId);

    @Query("""  
            SELECT ea
            FROM EmployeeAssignment ea
            JOIN FETCH ea.employee
            JOIN FETCH ea.department
            JOIN FETCH ea.position
            WHERE ea.employee.employeeId = :employeeId
            AND ea.effectiveTo IS NULL
            """)
    List<EmployeeAssignment> findCurrentAssignments(
            @Param("employeeId") Long employeeId);

    @Query("""
        SELECT a
        FROM EmployeeAssignment a
        JOIN FETCH a.department
        JOIN FETCH a.position
        WHERE a.employee.employeeId IN :employeeIds
        AND a.effectiveTo IS NULL
        """)
    List<EmployeeAssignment> findCurrentByEmployeeIds(
            @Param("employeeIds") Collection<Long> employeeIds
    );
}
