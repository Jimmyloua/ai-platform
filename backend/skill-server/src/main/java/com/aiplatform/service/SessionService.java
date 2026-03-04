package com.aiplatform.service;

import com.aiplatform.model.Message;
import com.aiplatform.model.SkillSession;
import com.aiplatform.repository.MessageRepository;
import com.aiplatform.repository.SessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
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

        return sessionRepository.save(session);
    }

    /**
     * Get session by ID
     */
    public Optional<SkillSession> getSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }

    /**
     * Get sessions for user
     */
    public List<SkillSession> getSessionsByUser(String userId) {
        return sessionRepository.findByUserId(userId);
    }

    /**
     * Get sessions for user with pagination
     */
    public Page<SkillSession> getSessionsByUser(String userId, int page, int size) {
        return sessionRepository.findByUserId(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    /**
     * Update session status
     */
    @Transactional
    public void updateStatus(String sessionId, SkillSession.Status status) {
        sessionRepository.updateStatus(sessionId, status, LocalDateTime.now());
    }

    /**
     * Complete session
     */
    @Transactional
    public void completeSession(String sessionId, Object result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            sessionRepository.completeSession(sessionId, resultJson, LocalDateTime.now());
        } catch (JsonProcessingException e) {
            log.error("Error serializing result: {}", e.getMessage());
            sessionRepository.completeSession(sessionId, "{}", LocalDateTime.now());
        }
    }

    /**
     * Fail session
     */
    @Transactional
    public void failSession(String sessionId, String errorMessage) {
        sessionRepository.failSession(sessionId, errorMessage, LocalDateTime.now());
    }

    /**
     * Deactivate session (for WebSocket cleanup)
     */
    @Transactional
    public void deactivateSession(String sessionId) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            if (session.getStatus() == SkillSession.Status.RUNNING ||
                session.getStatus() == SkillSession.Status.PENDING) {
                sessionRepository.updateStatus(sessionId, SkillSession.Status.CANCELLED, LocalDateTime.now());
            }
        });
    }

    /**
     * Delete session and its messages
     */
    @Transactional
    public void deleteSession(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.findBySessionId(sessionId).ifPresent(sessionRepository::delete);
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
        return sessionRepository.countByStatus(SkillSession.Status.RUNNING) +
               sessionRepository.countByStatus(SkillSession.Status.PENDING);
    }
}