package com.aiplatform.service;

import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.model.Skill;
import com.aiplatform.model.SkillSession;
import com.aiplatform.repository.SessionRepository;
import com.aiplatform.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for skill execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExecutionService {

    private final SkillRepository skillRepository;
    private final SessionRepository sessionRepository;
    private final MessagePersistenceService messagePersistenceService;

    // Active executions: sessionId -> sink for progress updates
    private final Map<String, Sinks.Many<WebSocketMessage.SkillProgressResponse.Payload>> activeExecutions = new ConcurrentHashMap<>();

    // Paused sessions
    private final Map<String, Boolean> pausedSessions = new ConcurrentHashMap<>();

    // Cancelled sessions
    private final Map<String, Boolean> cancelledSessions = new ConcurrentHashMap<>();

    /**
     * Execute a skill
     */
    public Flux<WebSocketMessage.SkillProgressResponse.Payload> executeSkill(
            String sessionId,
            WebSocketMessage.SkillExecuteRequest.Payload payload) {

        return Flux.create(sink -> {
            try {
                // Find skill
                Skill skill = findSkill(payload);
                if (skill == null) {
                    sink.error(new RuntimeException("Skill not found"));
                    return;
                }

                // Create session
                SkillSession session = createSession(sessionId, skill, payload);
                sessionRepository.save(session);

                // Store active execution
                Sinks.Many<WebSocketMessage.SkillProgressResponse.Payload> progressSink =
                        Sinks.many().unicast().onBackpressureBuffer();
                activeExecutions.put(sessionId, progressSink);

                // Execute skill asynchronously
                executeSkillInternal(sessionId, skill, payload, sink);

            } catch (Exception e) {
                log.error("Error starting skill execution: {}", e.getMessage(), e);
                sink.error(e);
            }
        });
    }

    /**
     * Internal skill execution
     */
    @Async("skillExecutor")
    protected void executeSkillInternal(
            String sessionId,
            Skill skill,
            WebSocketMessage.SkillExecuteRequest.Payload payload,
            reactor.core.publisher.FluxSink<WebSocketMessage.SkillProgressResponse.Payload> sink) {

        long startTime = System.currentTimeMillis();
        int progress = 0;

        try {
            // Update session status
            updateSessionStatus(sessionId, SkillSession.Status.RUNNING);

            // Simulate skill execution with progress updates
            // In real implementation, this would call the actual skill endpoint
            for (int i = 0; i <= 100; i += 10) {
                // Check if cancelled
                if (cancelledSessions.containsKey(sessionId)) {
                    updateSessionStatus(sessionId, SkillSession.Status.CANCELLED);
                    sink.complete();
                    cleanup(sessionId);
                    return;
                }

                // Check if paused
                while (pausedSessions.containsKey(sessionId)) {
                    Thread.sleep(500);
                }

                progress = i;
                int finalProgress = progress;

                sink.next(WebSocketMessage.SkillProgressResponse.Payload.builder()
                        .progress(finalProgress)
                        .step("Processing")
                        .message("Executing skill: " + skill.getName())
                        .build());

                // Simulate work
                Thread.sleep(500);
            }

            // Complete session
            long duration = System.currentTimeMillis() - startTime;
            String result = "{\"status\":\"success\",\"duration\":" + duration + "}";

            sessionRepository.completeSession(sessionId, result, LocalDateTime.now());

            sink.next(WebSocketMessage.SkillProgressResponse.Payload.builder()
                    .progress(100)
                    .step("Complete")
                    .message("Skill execution completed")
                    .data(Map.of("result", result, "durationMs", duration))
                    .build());

            sink.complete();

        } catch (Exception e) {
            log.error("Skill execution error: {}", e.getMessage(), e);
            sessionRepository.failSession(sessionId, e.getMessage(), LocalDateTime.now());
            sink.error(e);
        } finally {
            cleanup(sessionId);
        }
    }

    /**
     * Pause skill execution
     */
    public void pauseSkill(String sessionId) {
        pausedSessions.put(sessionId, true);
        updateSessionStatus(sessionId, SkillSession.Status.PAUSED);
    }

    /**
     * Resume skill execution
     */
    public void resumeSkill(String sessionId) {
        pausedSessions.remove(sessionId);
        updateSessionStatus(sessionId, SkillSession.Status.RUNNING);
    }

    /**
     * Cancel skill execution
     */
    public void cancelSkill(String sessionId) {
        cancelledSessions.put(sessionId, true);
        updateSessionStatus(sessionId, SkillSession.Status.CANCELLED);
    }

    /**
     * Find skill by ID or name
     */
    private Skill findSkill(WebSocketMessage.SkillExecuteRequest.Payload payload) {
        if (payload.getSkillId() != null && !payload.getSkillId().isEmpty()) {
            return skillRepository.findByIdAndIsActiveTrue(Long.parseLong(payload.getSkillId()))
                    .orElse(null);
        }
        if (payload.getSkillName() != null && !payload.getSkillName().isEmpty()) {
            return skillRepository.findByName(payload.getSkillName())
                    .orElse(null);
        }
        return null;
    }

    /**
     * Create a new session
     */
    private SkillSession createSession(String sessionId, Skill skill,
                                        WebSocketMessage.SkillExecuteRequest.Payload payload) {
        return SkillSession.builder()
                .sessionId(sessionId)
                .skillId(skill.getId())
                .userId("user-" + UUID.randomUUID().toString().substring(0, 8)) // TODO: Get from auth
                .channel(SkillSession.Channel.CUI) // TODO: Get from payload
                .status(SkillSession.Status.PENDING)
                .startedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Update session status
     */
    private void updateSessionStatus(String sessionId, SkillSession.Status status) {
        sessionRepository.updateStatus(sessionId, status, LocalDateTime.now());
    }

    /**
     * Cleanup execution resources
     */
    private void cleanup(String sessionId) {
        activeExecutions.remove(sessionId);
        pausedSessions.remove(sessionId);
        cancelledSessions.remove(sessionId);
    }
}