package com.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hrm.model.EmployeeQualification;

public interface EmployeeQualificationRepository extends JpaRepository<EmployeeQualification, Long> {

       @Query("SELECT q FROM EmployeeQualification q JOIN FETCH q.qualificationLevel " +
                     "WHERE q.employee.employeeId = :employeeId AND q.isDeleted = false " +
                     "ORDER BY q.isPrimary DESC, q.employeeQualificationId DESC")
       List<EmployeeQualification> findAllByEmployeeWithLevel(@Param("employeeId") Long employeeId);

       @Query("SELECT q FROM EmployeeQualification q JOIN FETCH q.qualificationLevel " +
                     "WHERE q.employeeQualificationId = :id AND q.isDeleted = false")
       Optional<EmployeeQualification> findByIdWithLevel(@Param("id") Long id);

       @Query("SELECT q FROM EmployeeQualification q " +
                     "WHERE q.employee.employeeId = :employeeId AND q.isPrimary = true AND q.isDeleted = false")
       Optional<EmployeeQualification> findPrimaryByEmployeeId(@Param("employeeId") Long employeeId);

       @Query(value = """
                     SELECT q
                     FROM EmployeeQualification q
                     JOIN FETCH q.qualificationLevel
                     WHERE q.employee.employeeId = :employeeId
                     AND (q.isDeleted = false OR q.isDeleted IS NULL)
                     """, countQuery = """
                     SELECT COUNT(q)
                     FROM EmployeeQualification q
                     WHERE q.employee.employeeId = :employeeId
                     AND (q.isDeleted = false OR q.isDeleted IS NULL)
                     """)
       Page<EmployeeQualification> findPagedByEmployeeId(
                     @Param("employeeId") Long employeeId,
                     Pageable pageable);

}
