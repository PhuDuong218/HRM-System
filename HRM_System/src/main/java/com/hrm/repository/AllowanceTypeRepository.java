package com.hrm.repository;

import java.util.Optional;
import com.hrm.model.AllowanceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AllowanceTypeRepository extends JpaRepository<AllowanceType,Long> {
    boolean existsByTypeCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AllowanceType t where t.allowanceTypeId=:id")
    Optional<AllowanceType> findByIdForUpdate(@Param("id") Long id);
}