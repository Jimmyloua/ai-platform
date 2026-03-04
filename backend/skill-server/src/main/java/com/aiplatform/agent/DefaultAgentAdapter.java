package com.aiplatform.agent;

import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.service.SkillExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Default agent adapter that wraps the existing SkillExecutionService.
 * Maintains backward compatibility with the skill.execute protocol.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAgentAdapter implements AgentAdapter {

    private final SkillExecutionService skillExecutionService;

    @Override
    public String getAgentType() {
        return "DEFAULT";
    }

    @Override
    public Flux<WebSocketMessage.WebSocketMessageBase> execute(AgentExecutionContext context) {
        log.info("Executing with DEFAULT adapter: sessionId={}, skillId={}",
                context.getSessionId(), context.getSkillId());

        return skillExecutionService.executeSkill(context.getSessionId(), context.getSkillRequest().getPayload())
                .map(progress -> (WebSocketMessage.WebSocketMessageBase) WebSocketMessage.SkillProgressResponse.builder()
                        .sessionId(context.getSessionId())
                        .payload(progress)
                        .build())
                .concatWithValues(createResultMessage(context.getSessionId()))
                .onErrorResume(error -> {
                    log.error("Skill execution failed: {}", error.getMessage());
                    return Flux.just(createErrorMessage(context.getSessionId(), error));
                });
    }

    @Override
    public boolean supports(String protocolType) {
        return protocolType != null && protocolType.startsWith("skill.");
    }

    @Override
    public void cancel(String sessionId) {
        skillExecutionService.cancelSkill(sessionId);
    }

    /**
     * Create result message (placeholder - actual result comes from service)
     */
    private WebSocketMessage.WebSocketMessageBase createResultMessage(String sessionId) {
        return WebSocketMessage.SkillResultResponse.builder()
                .sessionId(sessionId)
                .payload(WebSocketMessage.SkillResultResponse.Payload.builder()
                        .result(Map.of("status", "completed"))
                        .build())
                .build();
    }

    /**
     * Create error message
     */
    private WebSocketMessage.WebSocketMessageBase createErrorMessage(String sessionId, Throwable error) {
        return WebSocketMessage.SkillErrorResponse.builder()
                .sessionId(sessionId)
                .payload(WebSocketMessage.SkillErrorResponse.Payload.builder()
                        .code("EXECUTION_ERROR")
                        .message(error.getMessage())
                        .build())
                .build();
    }
}