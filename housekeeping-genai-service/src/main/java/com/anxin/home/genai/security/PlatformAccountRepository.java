package com.anxin.home.genai.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {
    Optional<PlatformAccount> findByUsername(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from PlatformAccount account where account.username = :username")
    Optional<PlatformAccount> findByUsernameForUpdate(@Param("username") String username);
    Optional<PlatformAccount> findByUserIdAndRole(String userId, String role);
    boolean existsByUsername(String username);
}
