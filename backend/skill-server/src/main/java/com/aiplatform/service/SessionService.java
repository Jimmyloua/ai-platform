package com.aiplatform.service;

import com.aiplatform.mapper.MessageMapper;
import com.aiplatform.mapper.SessionMapper;
import com.aiplatform.model.Message;
import com.aiplatform.model.SkillSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for session management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /**
     * Create a new session
     */
    @Transactional
    public SkillSession createSession(Long skillId, String userId, SkillSession.Channel channel) {
        String sessionId = generateSessionId();

        SkillSession session = SkillSession.builder()
                .sessionId(sessionId)
                .skillId(skillId)
                .userId(userId)
                .channel(channel)
                .status(SkillSession.Status.PENDING)
                .build();

        sessionMapper.insert(session);
        return session;
    }

    /**
     * Get session by ID
     */
    public Optional<SkillSession> getSession(String sessionId) {
        return sessionMapper.findBySessionId(sessionId);
    }

    /**
     * Get sessions for user
     */
    public List<SkillSession> getSessionsByUser(String userId) {
        return sessionMapper.findByUserId(userId);
    }

    /**
     * Update session status
     */
    @Transactional
    public void updateStatus(String sessionId, SkillSession.Status status) {
        sessionMapper.updateStatus(sessionId, status.name(), LocalDateTime.now());
    }

    /**
     * Complete session
     */
    @Transactional
    public void completeSession(String sessionId, Object result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            sessionMapper.completeSession(sessionId, resultJson, LocalDateTime.now());
        } catch (JsonProcessingException e) {
            log.error("Error serializing result: {}", e.getMessage());
            sessionMapper.completeSession(sessionId, "{}", LocalDateTime.now());
        }
    }

    /**
     * Fail session
     */
    @Transactional
    public void failSession(String sessionId, String errorMessage) {
        sessionMapper.failSession(sessionId, errorMessage, LocalDateTime.now());
    }

    /**
     * Deactivate session (for WebSocket cleanup)
     */
    @Transactional
    public void deactivateSession(String sessionId) {
        sessionMapper.findBySessionId(sessionId).ifPresent(session -> {
            if (session.getStatus() == SkillSession.Status.RUNNING ||
                session.getStatus() == SkillSession.Status.PENDING) {
                sessionMapper.updateStatus(sessionId, SkillSession.Status.CANCELLED.name(), LocalDateTime.now());
            }
        });
    }

    /**
     * Delete session and its messages
     */
    @Transactional
    public void deleteSession(String sessionId) {
        messageMapper.deleteBySessionId(sessionId);
        sessionMapper.deleteBySessionId(sessionId);
    }

    /**
     * Generate unique session ID
     */
    private String generateSessionId() {
        return "sess-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Get active sessions count
     */
    public long getActiveSessionsCount() {
        return sessionMapper.countByStatus(SkillSession.Status.RUNNING.name()) +
               sessionMapper.countByStatus(SkillSession.Status.PENDING.name());
    }
}