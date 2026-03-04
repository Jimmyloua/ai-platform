package com.aiplatform.service;

import com.aiplatform.model.Message;
import com.aiplatform.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for message persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePersistenceService {

    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    /**
     * Save a message
     */
    @Transactional
    public Message saveMessage(String sessionId, Message.Role role, String content) {
        Message message = Message.builder()
                .sessionId(sessionId)
                .messageId(generateMessageId())
                .role(role)
                .content(content)
                .build();

        return messageRepository.save(message);
    }

    /**
     * Save a message with tool calls
     */
    @Transactional
    public Message saveMessageWithToolCalls(String sessionId, Message.Role role,
                                             String content, Object toolCalls) {
        try {
            String toolCallsJson = toolCalls != null ? objectMapper.writeValueAsString(toolCalls) : null;

            Message message = Message.builder()
                    .sessionId(sessionId)
                    .messageId(generateMessageId())
                    .role(role)
                    .content(content)
                    .toolCalls(toolCallsJson)
                    .build();

            return messageRepository.save(message);
        } catch (JsonProcessingException e) {
            log.error("Error serializing tool calls: {}", e.getMessage());
            return saveMessage(sessionId, role, content);
        }
    }

    /**
     * Save a tool response
     */
    @Transactional
    public Message saveToolResponse(String sessionId, String toolCallId, String name, String content) {
        Message message = Message.builder()
                .sessionId(sessionId)
                .messageId(generateMessageId())
                .role(Message.Role.TOOL)
                .content(content)
                .toolCallId(toolCallId)
                .name(name)
                .build();

        return messageRepository.save(message);
    }

    /**
     * Get messages for a session
     */
    public List<Message> getMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Get messages with pagination
     */
    public Page<Message> getMessages(String sessionId, int page, int size) {
        return messageRepository.findBySessionId(
                sessionId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"))
        );
    }

    /**
     * Get last message in session
     */
    public Optional<Message> getLastMessage(String sessionId) {
        return messageRepository.findLastMessage(sessionId);
    }

    /**
     * Get message count for session
     */
    public long getMessageCount(String sessionId) {
        return messageRepository.countBySessionId(sessionId);
    }

    /**
     * Delete all messages for a session
     */
    @Transactional
    public void deleteMessages(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
    }

    /**
     * Update token usage for a message
     */
    @Transactional
    public void updateTokenUsage(Long messageId, int inputTokens, int outputTokens) {
        messageRepository.findById(messageId).ifPresent(message -> {
            message.setTokensInput(inputTokens);
            message.setTokensOutput(outputTokens);
            messageRepository.save(message);
        });
    }

    /**
     * Get total token usage for a session
     */
    public TokenUsage getTokenUsage(String sessionId) {
        int inputTokens = messageRepository.sumInputTokensBySessionId(sessionId);
        int outputTokens = messageRepository.sumOutputTokensBySessionId(sessionId);
        return new TokenUsage(inputTokens, outputTokens);
    }

    /**
     * Generate unique message ID
     */
    private String generateMessageId() {
        return "msg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Token usage record
     */
    public record TokenUsage(int inputTokens, int outputTokens) {
        public int total() {
            return inputTokens + outputTokens;
        }
    }
}