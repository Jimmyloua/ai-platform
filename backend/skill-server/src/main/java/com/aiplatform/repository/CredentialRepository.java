package com.aiplatform.repository;

import com.aiplatform.model.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Credential entities.
 */
@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {

    /**
     * Find credential by access key
     */
    Optional<Credential> findByAccessKey(String accessKey);

    /**
     * Find credentials by user ID
     */
    List<Credential> findByUserId(String userId);

    /**
     * Find credentials by skill ID
     */
    List<Credential> findBySkillId(Long skillId);

    /**
     * Find active credentials by access key
     */
    @Query("SELECT c FROM Credential c WHERE c.accessKey = :accessKey AND c.isActive = true")
    Optional<Credential> findActiveByAccessKey(@Param("accessKey") String accessKey);

    /**
     * Find valid (active and not expired) credential by access key
     */
    @Query("SELECT c FROM Credential c WHERE c.accessKey = :accessKey AND c.isActive = true AND (c.expiresAt IS NULL OR c.expiresAt > :now)")
    Optional<Credential> findValidByAccessKey(@Param("accessKey") String accessKey, @Param("now") LocalDateTime now);

    /**
     * Check if credential exists by access key
     */
    boolean existsByAccessKey(String accessKey);

    /**
     * Update last used timestamp
     */
    @Modifying
    @Query("UPDATE Credential c SET c.lastUsedAt = :lastUsedAt WHERE c.accessKey = :accessKey")
    int updateLastUsedAt(@Param("accessKey") String accessKey, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    /**
     * Deactivate credential
     */
    @Modifying
    @Query("UPDATE Credential c SET c.isActive = false, c.updatedAt = :now WHERE c.id = :id")
    int deactivate(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Find expired credentials
     */
    @Query("SELECT c FROM Credential c WHERE c.expiresAt IS NOT NULL AND c.expiresAt < :now AND c.isActive = true")
    List<Credential> findExpiredCredentials(@Param("now") LocalDateTime now);

    /**
     * Count active credentials for user
     */
    @Query("SELECT COUNT(c) FROM Credential c WHERE c.userId = :userId AND c.isActive = true")
    long countActiveByUserId(@Param("userId") String userId);
}