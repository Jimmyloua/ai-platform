package com.aiplatform.repository;

import com.aiplatform.model.SkillSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for SkillSession entities.
 */
@Repository
public interface SessionRepository extends JpaRepository<SkillSession, Long> {

    /**
     * Find session by session ID
     */
    Optional<SkillSession> findBySessionId(String sessionId);

    /**
     * Find sessions by user ID
     */
    List<SkillSession> findByUserId(String userId);

    /**
     * Find sessions by user ID with pagination
     */
    Page<SkillSession> findByUserId(String userId, Pageable pageable);

    /**
     * Find sessions by skill ID
     */
    List<SkillSession> findBySkillId(Long skillId);

    /**
     * Find sessions by status
     */
    List<SkillSession> findByStatus(SkillSession.Status status);

    /**
     * Find sessions by channel
     */
    List<SkillSession> findByChannel(SkillSession.Channel channel);

    /**
     * Find active sessions (running or pending)
     */
    @Query("SELECT s FROM SkillSession s WHERE s.status IN ('RUNNING', 'PENDING')")
    List<SkillSession> findActiveSessions();

    /**
     * Find sessions by user and status
     */
    List<SkillSession> findByUserIdAndStatus(String userId, SkillSession.Status status);

    /**
     * Find sessions created after a timestamp
     */
    List<SkillSession> findByCreatedAtAfter(LocalDateTime createdAt);

    /**
     * Find sessions by user and channel
     */
    List<SkillSession> findByUserIdAndChannel(String userId, SkillSession.Channel channel);

    /**
     * Update session status
     */
    @Modifying
    @Query("UPDATE SkillSession s SET s.status = :status, s.updatedAt = :now WHERE s.sessionId = :sessionId")
    int updateStatus(@Param("sessionId") String sessionId, @Param("status") SkillSession.Status status, @Param("now") LocalDateTime now);

    /**
     * Complete session
     */
    @Modifying
    @Query("UPDATE SkillSession s SET s.status = 'COMPLETED', s.outputData = :outputData, s.completedAt = :completedAt, s.updatedAt = :completedAt WHERE s.sessionId = :sessionId")
    int completeSession(@Param("sessionId") String sessionId, @Param("outputData") String outputData, @Param("completedAt") LocalDateTime completedAt);

    /**
     * Fail session
     */
    @Modifying
    @Query("UPDATE SkillSession s SET s.status = 'FAILED', s.errorMessage = :errorMessage, s.completedAt = :completedAt, s.updatedAt = :completedAt WHERE s.sessionId = :sessionId")
    int failSession(@Param("sessionId") String sessionId, @Param("errorMessage") String errorMessage, @Param("completedAt") LocalDateTime completedAt);

    /**
     * Count sessions by status
     */
    long countByStatus(SkillSession.Status status);

    /**
     * Count sessions by user
     */
    long countByUserId(String userId);
}