package com.hrm.repository;

import com.hrm.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    boolean existsByPermissionCodeIgnoreCase(String code);

    @Query(value = """
            select p.permission_code
            from permissions p
            join role_permissions rp on rp.permission_id = p.permission_id
            where rp.role_id = :roleId
            """, nativeQuery = true)
    Set<String> findCodesByRoleId(@Param("roleId") Long roleId);
}
