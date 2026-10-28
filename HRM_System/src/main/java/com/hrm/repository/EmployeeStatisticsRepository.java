
package com.hrm.repository;

import com.hrm.model.Employee;
import com.hrm.repository.projection.EmployeeStatisticsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public interface EmployeeStatisticsRepository
        extends JpaRepository<Employee, Long> {

    // Thong ke nhan vien theo phong ban tai ngay duoc chon
    @Query(value = """
            WITH effective_assignments AS (
                SELECT ea.employee_id,
                       ea.department_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY ea.employee_id
                           ORDER BY ea.effective_from DESC,
                                    ea.assignment_id DESC
                       ) AS assignmentRank
                FROM employee_assignments ea
                WHERE ea.effective_from <= :asOfDate
                  AND (
                      ea.effective_to IS NULL
                      OR ea.effective_to > :asOfDate
                  )
            )
            SELECT d.department_id AS groupId,
                   COALESCE(d.department_code, 'CHUA_PHAN_CONG') AS groupCode,
                   COALESCE(d.department_name, N'Chưa có phân công hiệu lực') AS groupName,
                   COUNT(DISTINCT e.employee_id) AS employeeCount
            FROM employees e
            LEFT JOIN effective_assignments ea
                ON ea.employee_id = e.employee_id
               AND ea.assignmentRank = 1
            LEFT JOIN departments d
                ON d.department_id = ea.department_id
            GROUP BY d.department_id,
                     d.department_code,
                     d.department_name
            ORDER BY d.department_name
            """, nativeQuery = true)
    List<EmployeeStatisticsRow> countByDepartment(
            @Param("asOfDate") LocalDate asOfDate);

    // Tuong thich voi Service cu cua Nhi
    default List<EmployeeStatisticsRow> countByDepartment() {
        return countByDepartment(
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    // Thong ke nhan vien theo chuc vu tai ngay duoc chon
    @Query(value = """
            WITH effective_assignments AS (
                SELECT ea.employee_id,
                       ea.position_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY ea.employee_id
                           ORDER BY ea.effective_from DESC,
                                    ea.assignment_id DESC
                       ) AS assignmentRank
                FROM employee_assignments ea
                WHERE ea.effective_from <= :asOfDate
                  AND (
                      ea.effective_to IS NULL
                      OR ea.effective_to > :asOfDate
                  )
            )
            SELECT p.position_id AS groupId,
                   COALESCE(p.position_code, 'CHUA_PHAN_CONG') AS groupCode,
                   COALESCE(p.position_name, N'Chưa có phân công hiệu lực') AS groupName,
                   COUNT(DISTINCT e.employee_id) AS employeeCount
            FROM employees e
            LEFT JOIN effective_assignments ea
                ON ea.employee_id = e.employee_id
               AND ea.assignmentRank = 1
            LEFT JOIN positions p
                ON p.position_id = ea.position_id
            GROUP BY p.position_id,
                     p.position_code,
                     p.position_name
            ORDER BY p.position_name
            """, nativeQuery = true)
    List<EmployeeStatisticsRow> countByPosition(
            @Param("asOfDate") LocalDate asOfDate);

    // Tuong thich voi Service cu cua Nhi
    default List<EmployeeStatisticsRow> countByPosition() {
        return countByPosition(
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    // Thong ke theo trang thai lam viec
    @Query(value = """
            SELECT CAST(NULL AS BIGINT) AS groupId,
                   e.employment_status AS groupCode,
                   e.employment_status AS groupName,
                   COUNT(DISTINCT e.employee_id) AS employeeCount
            FROM employees e
            GROUP BY e.employment_status
            ORDER BY e.employment_status
            """, nativeQuery = true)
    List<EmployeeStatisticsRow> countByEmploymentStatus();

    // Thong ke theo trinh do chuyen mon
    @Query(value = """
            SELECT ql.qualification_level_id AS groupId,
                   ql.level_code AS groupCode,
                   ql.level_name AS groupName,
                   COUNT(DISTINCT e.employee_id) AS employeeCount
            FROM employees e
            JOIN employee_qualifications eq
                ON eq.employee_id = e.employee_id
               AND eq.is_primary = 1
            JOIN qualification_levels ql
                ON ql.qualification_level_id = eq.qualification_level_id
            GROUP BY ql.qualification_level_id,
                     ql.level_code,
                     ql.level_name
            ORDER BY ql.level_name
            """, nativeQuery = true)
    List<EmployeeStatisticsRow> countByQualification();
}
