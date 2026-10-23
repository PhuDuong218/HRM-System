package com.hrm.repository;

import com.hrm.model.Employee;
import com.hrm.repository.projection.EmployeeStatisticsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmployeeStatisticsRepository extends JpaRepository<Employee, Long> {

    @Query(value = """
            select d.department_id as groupId,
                   d.department_code as groupCode,
                   d.department_name as groupName,
                   count(distinct e.employee_id) as employeeCount
            from employees e
            join employee_assignments ea on ea.employee_id = e.employee_id
                and ea.effective_to is null
            join departments d on d.department_id = ea.department_id
            group by d.department_id, d.department_code, d.department_name
            order by d.department_name
            """, nativeQuery = true)
    List<EmployeeStatisticsRow> countByDepartment();

    @Query(value = """
            select p.position_id as groupId,
                   p.position_code as groupCode,
                   p.position_name as groupName,
                   count(distinct e.employee_id) as employeeCount
            from employees e
            join employee_assignments ea on ea.employee_id = e.employee_id
                and ea.effective_to is null
            join positions p on p.position_id = ea.position_id
            group by p.position_id, p.position_code, p.position_name
            order by p.position_name
            """, nativeQuery = true)
    List<EmployeeStatisticsRow> countByPosition();

    @Query(value = """
            select cast(null as bigint) as groupId,
                   e.employment_status as groupCode,
                   e.employment_status as groupName,
                   count(distinct e.employee_id) as employeeCount
            from employees e
            group by e.employment_status
            order by e.employment_status
            """, nativeQuery = true)
    List<EmployeeStatisticsRow> countByEmploymentStatus();

    @Query(value = """
            select ql.qualification_level_id as groupId,
                   ql.level_code as groupCode,
                   ql.level_name as groupName,
                   count(distinct e.employee_id) as employeeCount
            from employees e
            join employee_qualifications eq
                on eq.employee_id = e.employee_id
                and eq.is_primary = 1
            join qualification_levels ql
                on ql.qualification_level_id = eq.qualification_level_id
            group by ql.qualification_level_id, ql.level_code, ql.level_name
            order by ql.level_name
            """, nativeQuery = true)
    List<EmployeeStatisticsRow> countByQualification();
}
