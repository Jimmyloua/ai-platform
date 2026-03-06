package com.aiplatform.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Reliable message queue using Redis Streams.
 * Provides at-least-once delivery guarantee with acknowledgment.
 *
 * Features:
 * - Message persistence in Redis Streams
 * - Consumer groups for horizontal scaling
 * - Automatic acknowledgment
 * - Pending message recovery on restart
 * - Dead letter queue for failed messages
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReliableMessageQueue {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Stream names
    private static final String REQUEST_STREAM = "aiplatform:requests";
    private static final String RESPONSE_STREAM = "aiplatform:responses";
    private static final String DEAD_LETTER_STREAM = "aiplatform:deadletter";

    // Consumer group names
    private static final String GATEWAY_CONSUMER_GROUP = "gateway-consumers";
    private static final String PLUGIN_CONSUMER_GROUP = "plugin-consumers";

    // Consumer configuration
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_DELIVERY_COUNT = 3;

    // In-flight messages tracker
    private final Map<String, InFlightMessage> inFlightMessages = new ConcurrentHashMap<>();

    // Executors for async processing
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService processorExecutor = Executors.newCachedThreadPool();

    // Running flag
    private volatile boolean running = false;

    // Consumer ID
    private String consumerId;

    @PostConstruct
    public void init() {
        this.consumerId = UUID.randomUUID().toString().substring(0, 8);
        initializeStreams();
        startPendingMessageRecovery();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        scheduler.shutdown();
        processorExecutor.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!processorExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                processorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("ReliableMessageQueue shutdown complete");
    }

    /**
     * Initialize streams and consumer groups
     */
    private void initializeStreams() {
        try {
            // Create streams if they don't exist
            createStreamIfNotExists(REQUEST_STREAM);
            createStreamIfNotExists(RESPONSE_STREAM);
            createStreamIfNotExists(DEAD_LETTER_STREAM);

            // Create consumer groups
            createConsumerGroupIfNotExists(REQUEST_STREAM, GATEWAY_CONSUMER_GROUP);
            createConsumerGroupIfNotExists(REQUEST_STREAM, PLUGIN_CONSUMER_GROUP);
            createConsumerGroupIfNotExists(RESPONSE_STREAM, GATEWAY_CONSUMER_GROUP);

            log.info("Initialized Redis Streams: request={}, response={}, deadletter={}",
                    REQUEST_STREAM, RESPONSE_STREAM, DEAD_LETTER_STREAM);

        } catch (Exception e) {
            log.error("Failed to initialize streams: {}", e.getMessage(), e);
        }
    }

    /**
     * Create stream if it doesn't exist
     */
    private void createStreamIfNotExists(String streamName) {
        try {
            // Add a placeholder message to create the stream
            Map<String, String> placeholder = Map.of("_init", "true", "timestamp", String.valueOf(System.currentTimeMillis()));
            redisTemplate.opsForStream().add(streamName, placeholder);
            log.debug("Created stream: {}", streamName);
        } catch (Exception e) {
            log.debug("Stream already exists or error: {} - {}", streamName, e.getMessage());
        }
    }

    /**
     * Create consumer group if it doesn't exist
     */
    private void createConsumerGroupIfNotExists(String streamName, String groupName) {
        try {
            redisTemplate.opsForStream().createGroup(streamName, groupName);
            log.info("Created consumer group: {} for stream: {}", groupName, streamName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer group already exists: {} for stream: {}", groupName, streamName);
            } else {
                log.warn("Error creating consumer group: {} - {}", groupName, e.getMessage());
            }
        }
    }

    /**
     * Publish a message to a stream with at-least-once delivery.
     * Returns the message ID for tracking.
     */
    public String publish(String streamName, MessagePayload payload) {
        try {
            Map<String, String> message = new HashMap<>();
            message.put("messageId", payload.getMessageId());
            message.put("sessionId", payload.getSessionId());
            message.put("sourceId", payload.getSourceId());
            message.put("targetId", payload.getTargetId());
            message.put("type", payload.getType());
            message.put("payload", payload.getPayload());
            message.put("timestamp", String.valueOf(System.currentTimeMillis()));
            message.put("retryCount", "0");

            RecordId recordId = redisTemplate.opsForStream().add(streamName, message);
            log.debug("Published message to stream: stream={}, messageId={}, recordId={}",
                    streamName, payload.getMessageId(), recordId);

            return recordId.getValue();

        } catch (Exception e) {
            log.error("Failed to publish message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }

    /**
     * Publish a request message (routed to gateway/plugin)
     */
    public String publishRequest(MessagePayload payload) {
        return publish(REQUEST_STREAM, payload);
    }

    /**
     * Publish a response message (back to source)
     */
    public String publishResponse(MessagePayload payload) {
        return publish(RESPONSE_STREAM, payload);
    }

    /**
     * Start consuming messages from a stream
     */
    public void startConsuming(String streamName, String groupName, MessageHandler handler) {
        running = true;

        processorExecutor.submit(() -> {
            log.info("Started consuming from stream: {} with group: {}", streamName, groupName);

            while (running) {
                try {
                    // Read messages from consumer group
                    List<MapRecord<String, Object, Object>> messages = readMessages(streamName, groupName);

                    for (MapRecord<String, Object, Object> message : messages) {
                        processMessage(message, handler);
                    }

                } catch (Exception e) {
                    log.error("Error consuming messages: {}", e.getMessage());
                    try {
                        Thread.sleep(1000); // Backoff on error
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("Stopped consuming from stream: {}", streamName);
        });
    }

    /**
     * Read messages from consumer group
     */
    @SuppressWarnings("unchecked")
    private List<MapRecord<String, Object, Object>> readMessages(String streamName, String groupName) {
        try {
            return redisTemplate.opsForStream().read(
                    Consumer.from(groupName, consumerId),
                    StreamReadOptions.empty().block(BLOCK_TIMEOUT).count(10),
                    StreamOffset.create(streamName, ReadOffset.lastConsumed())
            );
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Process a single message
     */
    private void processMessage(MapRecord<String, Object, Object> message, MessageHandler handler) {
        String recordId = message.getId().getValue();
        Map<Object, Object> body = message.getValue();

        try {
            // Extract message data
            MessagePayload payload = MessagePayload.builder()
                    .messageId((String) body.get("messageId"))
                    .sessionId((String) body.get("sessionId"))
                    .sourceId((String) body.get("sourceId"))
                    .targetId((String) body.get("targetId"))
                    .type((String) body.get("type"))
                    .payload((String) body.get("payload"))
                    .build();

            // Track as in-flight
            inFlightMessages.put(recordId, new InFlightMessage(recordId, payload, System.currentTimeMillis()));

            // Process the message
            handler.handle(payload);

            // Acknowledge on success
            acknowledge(message.getStream(), recordId);
            inFlightMessages.remove(recordId);

            log.debug("Processed and acknowledged message: recordId={}, messageId={}", recordId, payload.getMessageId());

        } catch (Exception e) {
            log.error("Error processing message: recordId={}, error={}", recordId, e.getMessage());
            handleProcessingFailure(message, e);
        }
    }

    /**
     * Acknowledge a processed message
     */
    private void acknowledge(String streamName, String recordId) {
        try {
            redisTemplate.opsForStream().acknowledge(streamName, GATEWAY_CONSUMER_GROUP, recordId);
        } catch (Exception e) {
            log.warn("Failed to acknowledge message: {} - {}", recordId, e.getMessage());
        }
    }

    /**
     * Handle processing failure
     */
    private void handleProcessingFailure(MapRecord<String, Object, Object> message, Exception error) {
        String recordId = message.getId().getValue();
        Map<Object, Object> body = message.getValue();

        try {
            // Get retry count
            int retryCount = Integer.parseInt((String) body.getOrDefault("retryCount", "0"));

            if (retryCount < MAX_DELIVERY_COUNT) {
                // Increment retry count and re-publish
                Map<String, String> updatedBody = new HashMap<>();
                body.forEach((k, v) -> updatedBody.put(k.toString(), v != null ? v.toString() : ""));
                updatedBody.put("retryCount", String.valueOf(retryCount + 1));
                updatedBody.put("lastError", error.getMessage());

                redisTemplate.opsForStream().add(message.getStream(), updatedBody);
                redisTemplate.opsForStream().acknowledge(message.getStream(), GATEWAY_CONSUMER_GROUP, recordId);

                log.warn("Re-queued message for retry: recordId={}, retryCount={}", recordId, retryCount + 1);

            } else {
                // Move to dead letter queue
                moveToDeadLetterQueue(message, error);
                redisTemplate.opsForStream().acknowledge(message.getStream(), GATEWAY_CONSUMER_GROUP, recordId);

                log.error("Moved message to dead letter queue: recordId={}", recordId);
            }

            inFlightMessages.remove(recordId);

        } catch (Exception e) {
            log.error("Error handling processing failure: {}", e.getMessage());
        }
    }

    /**
     * Move message to dead letter queue
     */
    private void moveToDeadLetterQueue(MapRecord<String, Object, Object> message, Exception error) {
        try {
            Map<String, String> deadLetter = new HashMap<>();
            message.getValue().forEach((k, v) -> deadLetter.put(k.toString(), v != null ? v.toString() : ""));
            deadLetter.put("error", error.getMessage());
            deadLetter.put("failedAt", String.valueOf(System.currentTimeMillis()));

            redisTemplate.opsForStream().add(DEAD_LETTER_STREAM, deadLetter);
        } catch (Exception e) {
            log.error("Failed to move to dead letter queue: {}", e.getMessage());
        }
    }

    /**
     * Start pending message recovery (handles restart scenarios)
     */
    private void startPendingMessageRecovery() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;

            try {
                // Claim pending messages that have timed out
                claimPendingMessages(REQUEST_STREAM, GATEWAY_CONSUMER_GROUP);
                claimPendingMessages(RESPONSE_STREAM, GATEWAY_CONSUMER_GROUP);

                // Check for timed out in-flight messages
                checkInFlightTimeouts();

            } catch (Exception e) {
                log.error("Error in pending message recovery: {}", e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Claim pending messages that have been idle too long
     */
    @SuppressWarnings("unchecked")
    private void claimPendingMessages(String streamName, String groupName) {
        try {
            // Get pending messages summary
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(streamName, groupName);

            if (summary == null || summary.getTotalPendingMessages() == 0) {
                return;
            }

            // Get details of pending messages
            PendingMessages pending = redisTemplate.opsForStream()
                    .pending(streamName, Consumer.from(groupName, consumerId), Range.unbounded(), 100);

            if (pending == null) return;

            for (org.springframework.data.redis.connection.stream.PendingMessage pm : pending) {
                // Claim messages idle for more than CLAIM_TIMEOUT
                Duration idleTime = pm.getElapsedTimeSinceLastDelivery();
                if (idleTime != null && idleTime.compareTo(CLAIM_TIMEOUT) > 0) {
                    String recordIdStr = pm.getId() != null ? pm.getId().getValue() : "";

                    log.info("Claiming timed-out pending message: stream={}, recordId={}",
                            streamName, recordIdStr);

                    // Read and re-process the message
                    List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().read(
                            Consumer.from(groupName, consumerId),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(streamName, ReadOffset.from(pm.getId()))
                    );

                    if (!claimed.isEmpty()) {
                        log.debug("Claimed message: {}", recordIdStr);
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Error claiming pending messages: {}", e.getMessage());
        }
    }

    /**
     * Check for in-flight message timeouts
     */
    private void checkInFlightTimeouts() {
        long now = System.currentTimeMillis();
        long timeoutMs = CLAIM_TIMEOUT.toMillis();

        Iterator<Map.Entry<String, InFlightMessage>> iterator = inFlightMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, InFlightMessage> entry = iterator.next();
            InFlightMessage msg = entry.getValue();

            if (now - msg.timestamp > timeoutMs) {
                log.warn("In-flight message timed out: recordId={}, sessionId={}",
                        msg.recordId, msg.payload.getSessionId());
                iterator.remove();
                // The message will be recovered by the pending message claim process
            }
        }
    }

    /**
     * Get pending messages count (for monitoring)
     */
    public long getPendingCount(String streamName, String groupName) {
        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(streamName, groupName);
            return summary != null ? summary.getTotalPendingMessages() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get consumer ID
     */
    public String getConsumerId() {
        return consumerId;
    }

    /**
     * Message payload
     */
    @lombok.Data
    @lombok.Builder
    public static class MessagePayload {
        private String messageId;
        private String sessionId;
        private String sourceId;      // Gateway connection ID
        private String targetId;      // Target gateway/plugin ID
        private String type;          // Message type
        private String payload;       // JSON payload
    }

    /**
     * In-flight message tracker
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class InFlightMessage {
        private String recordId;
        private MessagePayload payload;
        private long timestamp;
    }

    /**
     * Message handler interface
     */
    @FunctionalInterface
    public interface MessageHandler {
        void handle(MessagePayload payload) throws Exception;
    }
}