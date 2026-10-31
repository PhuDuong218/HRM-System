
package com.hrm.repository;

import com.hrm.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

        boolean existsByUsernameIgnoreCaseAndAccountIdNot(String username, Long accountId);
        boolean existsByEmployeeId(Long employeeId);
        boolean existsByRoleRoleId(Long roleId);

        @Query("""
                select a from Account a join Employee e on e.employeeId = a.employeeId
                where (:keyword = '' or lower(a.username) like :keyword escape '!'
                       or lower(e.employeeCode) like :keyword escape '!'
                       or lower(e.fullName) like :keyword escape '!')
                  and (:active is null or a.active = :active)
                  and (:roleId is null or a.role.roleId = :roleId)
                """)
        org.springframework.data.domain.Page<Account> search(
                @Param("keyword") String keyword, @Param("active") Boolean active,
                @Param("roleId") Long roleId, org.springframework.data.domain.Pageable pageable);

        @Modifying(flushAutomatically = true)
        @Query("""
                update Account a set a.tokenVersion = a.tokenVersion + 1,
                       a.updatedAt = CURRENT_TIMESTAMP, a.updatedBy = :actor
                where a.role.roleId = :roleId
                """)
        int revokeRoleTokens(@Param("roleId") Long roleId, @Param("actor") Long actor);

        Optional<Account> findByUsername(String username);

        // Vo hieu hoa tai khoan khi nhan vien nghi viec
        // Dong thoi tang tokenVersion de thu hoi JWT cu
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update Account a
                           set a.active = false,
                               a.tokenVersion = a.tokenVersion + 1,
                               a.updatedAt = CURRENT_TIMESTAMP
                         where a.employeeId = :employeeId
                        """)
        int deactivateAndRevokeByEmployeeId(
                        @Param("employeeId") Long employeeId);

        // Vo hieu hoa cac JWT hien tai khi dang xuat
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update Account a
                           set a.tokenVersion = a.tokenVersion + 1,
                               a.updatedAt = CURRENT_TIMESTAMP
                         where a.accountId = :accountId
                        """)
        int incrementTokenVersion(
                        @Param("accountId") Long accountId);

        // Cap nhat mat khau va thu hoi JWT cu
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update Account a
                           set a.passwordHash = :passwordHash,
                               a.tokenVersion = a.tokenVersion + 1,
                               a.updatedAt = CURRENT_TIMESTAMP
                         where a.accountId = :accountId
                           and a.active = true
                        """)
        int updatePasswordAndIncrementTokenVersion(
                        @Param("accountId") Long accountId,
                        @Param("passwordHash") String passwordHash);
}
