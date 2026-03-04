package com.aiplatform.repository;

import com.aiplatform.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Message entities.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Find messages by session ID, ordered by creation time
     */
    List<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Find messages by session ID with pagination
     */
    Page<Message> findBySessionId(String sessionId, Pageable pageable);

    /**
     * Find message by message ID
     */
    Optional<Message> findByMessageId(String messageId);

    /**
     * Find messages by session ID and role
     */
    List<Message> findBySessionIdAndRole(String sessionId, Message.Role role);

    /**
     * Find last message in session
     */
    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<Message> findLastMessage(@Param("sessionId") String sessionId);

    /**
     * Count messages in session
     */
    long countBySessionId(String sessionId);

    /**
     * Count messages by session and role
     */
    long countBySessionIdAndRole(String sessionId, Message.Role role);

    /**
     * Delete all messages for a session
     */
    void deleteBySessionId(String sessionId);

    /**
     * Sum input tokens for a session
     */
    @Query("SELECT COALESCE(SUM(m.tokensInput), 0) FROM Message m WHERE m.sessionId = :sessionId")
    int sumInputTokensBySessionId(@Param("sessionId") String sessionId);

    /**
     * Sum output tokens for a session
     */
    @Query("SELECT COALESCE(SUM(m.tokensOutput), 0) FROM Message m WHERE m.sessionId = :sessionId")
    int sumOutputTokensBySessionId(@Param("sessionId") String sessionId);

    /**
     * Find messages with tool calls
     */
    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId AND m.toolCalls IS NOT NULL")
    List<Message> findMessagesWithToolCalls(@Param("sessionId") String sessionId);
}