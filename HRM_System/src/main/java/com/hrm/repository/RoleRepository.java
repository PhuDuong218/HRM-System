package com.hrm.repository;

import com.hrm.model.Role;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {
    boolean existsByRoleCodeIgnoreCase(String code);

    // Serialize administrative writes, including the check that the actor still has authority.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.roleCode = :code")
    Optional<Role> findByCodeForUpdate(@Param("code") String code);

    @Query("select count(r) > 0 from Role r join r.permissions p where p.permissionId = :id")
    boolean usesPermission(@Param("id") Long id);
}
