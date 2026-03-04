package com.aiplatform.mapper;

import com.aiplatform.model.SkillSession;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper for SkillSession entities.
 */
@Mapper
public interface SessionMapper {

    /**
     * Find session by ID
     */
    @Select("SELECT * FROM skill_sessions WHERE id = #{id}")
    Optional<SkillSession> findById(Long id);

    /**
     * Find session by session ID
     */
    @Select("SELECT * FROM skill_sessions WHERE session_id = #{sessionId}")
    Optional<SkillSession> findBySessionId(String sessionId);

    /**
     * Find sessions by user ID
     */
    @Select("SELECT * FROM skill_sessions WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<SkillSession> findByUserId(String userId);

    /**
     * Find sessions by skill ID
     */
    @Select("SELECT * FROM skill_sessions WHERE skill_id = #{skillId}")
    List<SkillSession> findBySkillId(Long skillId);

    /**
     * Find sessions by status
     */
    @Select("SELECT * FROM skill_sessions WHERE status = #{status}")
    List<SkillSession> findByStatus(String status);

    /**
     * Find sessions by channel
     */
    @Select("SELECT * FROM skill_sessions WHERE channel = #{channel}")
    List<SkillSession> findByChannel(String channel);

    /**
     * Find active sessions (running or pending)
     */
    @Select("SELECT * FROM skill_sessions WHERE status IN ('RUNNING', 'PENDING')")
    List<SkillSession> findActiveSessions();

    /**
     * Find sessions by user and status
     */
    @Select("SELECT * FROM skill_sessions WHERE user_id = #{userId} AND status = #{status}")
    List<SkillSession> findByUserIdAndStatus(@Param("userId") String userId, @Param("status") String status);

    /**
     * Find sessions created after a timestamp
     */
    @Select("SELECT * FROM skill_sessions WHERE created_at > #{createdAt}")
    List<SkillSession> findByCreatedAtAfter(LocalDateTime createdAt);

    /**
     * Find sessions by user and channel
     */
    @Select("SELECT * FROM skill_sessions WHERE user_id = #{userId} AND channel = #{channel}")
    List<SkillSession> findByUserIdAndChannel(@Param("userId") String userId, @Param("channel") String channel);

    /**
     * Count sessions by status
     */
    @Select("SELECT COUNT(*) FROM skill_sessions WHERE status = #{status}")
    long countByStatus(String status);

    /**
     * Count sessions by user
     */
    @Select("SELECT COUNT(*) FROM skill_sessions WHERE user_id = #{userId}")
    long countByUserId(String userId);

    /**
     * Insert session
     */
    @Insert("INSERT INTO skill_sessions (session_id, skill_id, user_id, channel, status, input_data, output_data, " +
            "error_message, metadata, started_at, completed_at, created_at, updated_at) " +
            "VALUES (#{sessionId}, #{skillId}, #{userId}, #{channel}, #{status}, #{inputData}, #{outputData}, " +
            "#{errorMessage}, #{metadata}, #{startedAt}, #{completedAt}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SkillSession session);

    /**
     * Update session
     */
    @Update("UPDATE skill_sessions SET skill_id = #{skillId}, user_id = #{userId}, channel = #{channel}, " +
            "status = #{status}, input_data = #{inputData}, output_data = #{outputData}, " +
            "error_message = #{errorMessage}, metadata = #{metadata}, started_at = #{startedAt}, " +
            "completed_at = #{completedAt}, updated_at = NOW() WHERE id = #{id}")
    int update(SkillSession session);

    /**
     * Update session status
     */
    @Update("UPDATE skill_sessions SET status = #{status}, updated_at = #{now} WHERE session_id = #{sessionId}")
    int updateStatus(@Param("sessionId") String sessionId, @Param("status") String status, @Param("now") LocalDateTime now);

    /**
     * Complete session
     */
    @Update("UPDATE skill_sessions SET status = 'COMPLETED', output_data = #{outputData}, " +
            "completed_at = #{completedAt}, updated_at = #{completedAt} WHERE session_id = #{sessionId}")
    int completeSession(@Param("sessionId") String sessionId, @Param("outputData") String outputData, @Param("completedAt") LocalDateTime completedAt);

    /**
     * Fail session
     */
    @Update("UPDATE skill_sessions SET status = 'FAILED', error_message = #{errorMessage}, " +
            "completed_at = #{completedAt}, updated_at = #{completedAt} WHERE session_id = #{sessionId}")
    int failSession(@Param("sessionId") String sessionId, @Param("errorMessage") String errorMessage, @Param("completedAt") LocalDateTime completedAt);

    /**
     * Delete session
     */
    @Delete("DELETE FROM skill_sessions WHERE id = #{id}")
    int deleteById(Long id);

    /**
     * Delete session by session ID
     */
    @Delete("DELETE FROM skill_sessions WHERE session_id = #{sessionId}")
    int deleteBySessionId(String sessionId);
}