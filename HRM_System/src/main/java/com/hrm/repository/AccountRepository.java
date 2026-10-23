package com.hrm.repository;

import com.hrm.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Account a
               set a.tokenVersion = a.tokenVersion + 1,
                   a.updatedAt = CURRENT_TIMESTAMP
             where a.accountId = :accountId
            """)
    int incrementTokenVersion(@Param("accountId") Long accountId);

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
