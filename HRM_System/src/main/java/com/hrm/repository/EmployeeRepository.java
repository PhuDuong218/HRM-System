
package com.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hrm.model.Employee;

import jakarta.persistence.LockModeType;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // Kiểm tra mã nhân viên đã tồn tại hay chưa
    boolean existsByEmployeeCode(String employeeCode);

    // Lấy danh sách nhân viên cùng phân công hiện tại,
    // phòng ban và chức vụ tương ứng
    @Query("SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.assignments a " +
            "LEFT JOIN FETCH a.department " +
            "LEFT JOIN FETCH a.position " +
            "WHERE a.effectiveTo IS NULL OR a IS NULL")
    List<Employee> findAllWithCurrentAssignments();

    // Khóa bản ghi nhân viên khi cập nhật để tránh
    // các giao dịch đồng thời sửa cùng một nhân viên
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.employeeId = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);
}
