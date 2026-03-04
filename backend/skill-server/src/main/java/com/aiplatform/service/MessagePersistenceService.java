package com.aiplatform.service;

import com.aiplatform.mapper.MessageMapper;
import com.aiplatform.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final MessageMapper messageMapper;
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

        messageMapper.insert(message);
        return message;
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

            messageMapper.insert(message);
            return message;
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

        messageMapper.insert(message);
        return message;
    }

    /**
     * Get messages for a session
     */
    public List<Message> getMessages(String sessionId) {
        return messageMapper.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Get last message in session
     */
    public Optional<Message> getLastMessage(String sessionId) {
        return messageMapper.findLastMessage(sessionId);
    }

    /**
     * Get message count for session
     */
    public long getMessageCount(String sessionId) {
        return messageMapper.countBySessionId(sessionId);
    }

    /**
     * Delete all messages for a session
     */
    @Transactional
    public void deleteMessages(String sessionId) {
        messageMapper.deleteBySessionId(sessionId);
    }

    /**
     * Update token usage for a message
     */
    @Transactional
    public void updateTokenUsage(Long messageId, int inputTokens, int outputTokens) {
        messageMapper.findById(messageId).ifPresent(message -> {
            message.setTokensInput(inputTokens);
            message.setTokensOutput(outputTokens);
            messageMapper.update(message);
        });
    }

    /**
     * Get total token usage for a session
     */
    public TokenUsage getTokenUsage(String sessionId) {
        int inputTokens = messageMapper.sumInputTokensBySessionId(sessionId);
        int outputTokens = messageMapper.sumOutputTokensBySessionId(sessionId);
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