package com.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.hrm.model.Employee;
import com.hrm.enums.EmploymentStatus;
import java.util.List;
import java.util.Optional;

@Repository
// Chú ý chữ String ở cuối cùng: JpaRepository<Tên_Thực_Thể, Kiểu_Dữ_Liệu_Của_ID>
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmployeeIdAndEmploymentStatus(Long employeeId, EmploymentStatus employmentStatus);

    // Fetch the full history; filtering a fetched collection would lose employees or history.
    @Query("""
            select distinct e from Employee e
            left join fetch e.assignments a
            left join fetch a.department
            left join fetch a.position
            """)
    List<Employee> findAllWithAssignments();

    @Query("""
            select distinct e from Employee e
            left join fetch e.assignments a
            left join fetch a.department
            left join fetch a.position
            where e.employeeId = :employeeId
            """)
    Optional<Employee> findByIdWithAssignments(@Param("employeeId") Long employeeId);
}
