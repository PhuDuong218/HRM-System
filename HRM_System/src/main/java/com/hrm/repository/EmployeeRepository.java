package com.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.hrm.model.Employee;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

@Repository
// Chú ý chữ String ở cuối cùng: JpaRepository<Tên_Thực_Thể, Kiểu_Dữ_Liệu_Của_ID>
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    // Ép Hibernate JOIN thẳng vào bảng Phân công, Phòng ban, Chức vụ trong 1 câu SQL
    // Điều kiện: a.effectiveTo IS NULL (Chỉ lấy chức vụ ĐƯƠNG NHIỆM)
    @Query("SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.assignments a " +
            "LEFT JOIN FETCH a.department " +
            "LEFT JOIN FETCH a.position " +
            "WHERE a.effectiveTo IS NULL OR a IS NULL")
    List<Employee> findAllWithCurrentAssignments();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.employeeId = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);
}
