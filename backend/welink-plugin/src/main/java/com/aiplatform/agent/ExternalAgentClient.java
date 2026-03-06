package com.aiplatform.agent;

import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for connecting to external OpenCode/Agent services.
 * This is the final hop in the call chain: CUI -> SkillServer -> SkillGateway -> WeLinkPlugin -> ExternalAgent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalAgentClient {

    private final ObjectMapper objectMapper;

    @Value("${agent.external.url:http://localhost:8082}")
    private String externalAgentUrl;

    @Value("${agent.external.timeout:60000}")
    private long timeout;

    @Value("${agent.external.enabled:true}")
    private boolean enabled;

    private WebClient webClient;

    /**
     * Execute a request against the external agent
     */
    public Flux<WebSocketMessage.WebSocketMessageBase> execute(
            String sessionId,
            OpenCodeMessage.AgentExecuteRequest request) {

        if (!enabled) {
            log.warn("External agent disabled, returning mock response");
            return createMockResponse(sessionId, request);
        }

        log.info("Calling external agent: sessionId={}, url={}", sessionId, externalAgentUrl);

        if (webClient == null) {
            webClient = WebClient.builder()
                    .baseUrl(externalAgentUrl)
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();
        }

        if (request.getPayload().isStream()) {
            return executeStreaming(sessionId, request);
        } else {
            return executeNonStreaming(sessionId, request);
        }
    }

    /**
     * Execute streaming request
     */
    private Flux<WebSocketMessage.WebSocketMessageBase> executeStreaming(
            String sessionId,
            OpenCodeMessage.AgentExecuteRequest request) {

        return Flux.create(sink -> {
            try {
                StringBuilder contentBuilder = new StringBuilder();
                int[] chunkId = {0};

                webClient.post()
                        .uri("/v1/agent/execute")
                        .bodyValue(buildRequestBody(request))
                        .retrieve()
                        .bodyToFlux(String.class)
                        .timeout(Duration.ofMillis(timeout))
                        .subscribe(
                                chunk -> {
                                    try {
                                        // Parse SSE or JSON lines
                                        String content = parseStreamChunk(chunk);
                                        if (content != null && !content.isEmpty()) {
                                            contentBuilder.append(content);

                                            OpenCodeMessage.AgentResponseChunk responseChunk =
                                                    OpenCodeMessage.AgentResponseChunk.builder()
                                                            .sessionId(sessionId)
                                                            .payload(OpenCodeMessage.AgentResponseChunk.Payload.builder()
                                                                    .chunkId(chunkId[0]++)
                                                                    .delta(OpenCodeMessage.MessageDelta.builder()
                                                                            .content(content)
                                                                            .build())
                                                                    .done(false)
                                                                    .build())
                                                            .build();
                                            sink.next(responseChunk);
                                        }
                                    } catch (Exception e) {
                                        log.error("Error parsing chunk: {}", e.getMessage());
                                    }
                                },
                                error -> {
                                    log.error("External agent error: {}", error.getMessage());
                                    sink.next(createErrorResponse(sessionId, "EXTERNAL_ERROR", error.getMessage()));
                                    sink.complete();
                                },
                                () -> {
                                    // Send final chunk
                                    OpenCodeMessage.AgentResponseChunk finalChunk =
                                            OpenCodeMessage.AgentResponseChunk.builder()
                                                    .sessionId(sessionId)
                                                    .payload(OpenCodeMessage.AgentResponseChunk.Payload.builder()
                                                            .chunkId(chunkId[0])
                                                            .delta(OpenCodeMessage.MessageDelta.builder()
                                                                    .content("")
                                                                    .build())
                                                            .done(true)
                                                            .finishReason("stop")
                                                            .build())
                                                    .build();
                                    sink.next(finalChunk);

                                    // Send result
                                    sink.next(createResultResponse(sessionId, contentBuilder.toString()));
                                    sink.complete();
                                }
                        );

            } catch (Exception e) {
                log.error("Error calling external agent: {}", e.getMessage());
                sink.next(createErrorResponse(sessionId, "CALL_ERROR", e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * Execute non-streaming request
     */
    private Flux<WebSocketMessage.WebSocketMessageBase> executeNonStreaming(
            String sessionId,
            OpenCodeMessage.AgentExecuteRequest request) {

        try {
            log.debug("Making non-streaming call to external agent");

            Map<String, Object> response = webClient.post()
                    .uri("/v1/agent/execute")
                    .bodyValue(buildRequestBody(request))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            String content = extractContent(response);
            return Flux.just(createResultResponse(sessionId, content));

        } catch (Exception e) {
            log.error("External agent error: {}", e.getMessage());
            return Flux.just(createErrorResponse(sessionId, "EXTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * Build request body for external agent
     */
    private Map<String, Object> buildRequestBody(OpenCodeMessage.AgentExecuteRequest request) {
        return Map.of(
                "messages", request.getPayload().getMessages() != null
                        ? request.getPayload().getMessages() : List.of(),
                "tools", request.getPayload().getTools() != null
                        ? request.getPayload().getTools() : List.of(),
                "stream", request.getPayload().isStream(),
                "model", request.getPayload().getModel() != null
                        ? request.getPayload().getModel() : "default",
                "skillId", request.getPayload().getSkillId() != null
                        ? request.getPayload().getSkillId() : "",
                "skillName", request.getPayload().getSkillName() != null
                        ? request.getPayload().getSkillName() : ""
        );
    }

    /**
     * Parse streaming chunk
     */
    private String parseStreamChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }

        // Handle SSE format
        if (chunk.startsWith("data: ")) {
            String data = chunk.substring(6);
            if ("[DONE]".equals(data)) {
                return null;
            }
            try {
                Map<String, Object> parsed = objectMapper.readValue(data, Map.class);
                // Extract content from OpenAI-style response
                List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                    if (delta != null) {
                        return (String) delta.get("content");
                    }
                }
            } catch (Exception e) {
                log.debug("Could not parse chunk as JSON: {}", chunk);
                return chunk;
            }
        }

        return chunk;
    }

    /**
     * Extract content from response
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return "No response from agent";
        }

        Object content = response.get("content");
        if (content != null) {
            return content.toString();
        }

        Object message = response.get("message");
        if (message instanceof Map) {
            Object msgContent = ((Map<String, Object>) message).get("content");
            if (msgContent != null) {
                return msgContent.toString();
            }
        }

        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return response.toString();
        }
    }

    /**
     * Create result response
     */
    private OpenCodeMessage.AgentResultResponse createResultResponse(String sessionId, String content) {
        return OpenCodeMessage.AgentResultResponse.builder()
                .sessionId(sessionId)
                .payload(OpenCodeMessage.AgentResultResponse.Payload.builder()
                        .message(OpenCodeMessage.Message.assistant(content))
                        .usage(OpenCodeMessage.AgentResultResponse.Payload.Usage.builder()
                                .inputTokens(0)
                                .outputTokens(content.length() / 4)
                                .durationMs(0)
                                .build())
                        .build())
                .build();
    }

    /**
     * Create error response
     */
    private OpenCodeMessage.AgentErrorResponse createErrorResponse(String sessionId, String code, String message) {
        return OpenCodeMessage.AgentErrorResponse.builder()
                .sessionId(sessionId)
                .payload(OpenCodeMessage.AgentErrorResponse.Payload.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * Create mock response for testing
     */
    private Flux<WebSocketMessage.WebSocketMessageBase> createMockResponse(
            String sessionId,
            OpenCodeMessage.AgentExecuteRequest request) {

        String content = "Mock response from external agent";

        if (request.getPayload().getMessages() != null && !request.getPayload().getMessages().isEmpty()) {
            OpenCodeMessage.Message lastMessage = request.getPayload().getMessages()
                    .get(request.getPayload().getMessages().size() - 1);
            if (lastMessage.getContent() != null) {
                content = "Response to: " + lastMessage.getContent();
            }
        }

        if (request.getPayload().isStream()) {
            return Flux.just(
                    OpenCodeMessage.AgentResponseChunk.builder()
                            .sessionId(sessionId)
                            .payload(OpenCodeMessage.AgentResponseChunk.Payload.builder()
                                    .chunkId(0)
                                    .delta(OpenCodeMessage.MessageDelta.builder()
                                            .content(content)
                                            .build())
                                    .done(false)
                                    .build())
                            .build(),
                    OpenCodeMessage.AgentResponseChunk.builder()
                            .sessionId(sessionId)
                            .payload(OpenCodeMessage.AgentResponseChunk.Payload.builder()
                                    .chunkId(1)
                                    .delta(OpenCodeMessage.MessageDelta.builder()
                                            .content("")
                                            .build())
                                    .done(true)
                                    .finishReason("stop")
                                    .build())
                            .build(),
                    createResultResponse(sessionId, content)
            );
        } else {
            return Flux.just(createResultResponse(sessionId, content));
        }
    }

    /**
     * Cancel a session (if external agent supports it)
     */
    public void cancel(String sessionId) {
        log.info("Cancelling external agent session: {}", sessionId);
        // Could implement cancellation via HTTP endpoint if supported
    }
}