package com.aiplatform.protocol;

import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.model.WeLinkMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenCode protocol adapter.
 * Transforms between WeLink messages and OpenCode agent protocol.
 */
@Slf4j
@Component
public class OpenCodeProtocolAdapter implements ProtocolAdapter {

    @Override
    public String getProtocolType() {
        return "opencode";
    }

    @Override
    public WebSocketMessage.WebSocketMessageBase adaptToGateway(WeLinkMessage welinkMessage, boolean stream) {
        log.debug("Adapting WeLink message to OpenCode: msgId={}", welinkMessage.getMsgId());

        // Build OpenCode agent execute request
        OpenCodeMessage.Message userMessage = OpenCodeMessage.Message.builder()
                .role("user")
                .content(welinkMessage.getContent())
                .build();

        List<OpenCodeMessage.Message> messages = new ArrayList<>();
        messages.add(userMessage);

        // Build input map from WeLink message context
        Map<String, Object> input = Map.of(
                "message", welinkMessage.getContent(),
                "chatType", welinkMessage.getChatType() != null ? welinkMessage.getChatType() : "single",
                "groupId", welinkMessage.getGroupId() != null ? welinkMessage.getGroupId() : "",
                "msgId", welinkMessage.getMsgId(),
                "fromUserId", welinkMessage.getFromUserId() != null ? welinkMessage.getFromUserId() : ""
        );

        OpenCodeMessage.AgentExecuteRequest request = OpenCodeMessage.AgentExecuteRequest.builder()
                .sessionId(UUID.randomUUID().toString())
                .payload(OpenCodeMessage.AgentExecuteRequest.Payload.builder()
                        .messages(messages)
                        .stream(stream)
                        .channel("im") // WeLink is IM channel
                        .options(Map.of(
                                "platform", "welink",
                                "tenantId", welinkMessage.getTenantId() != null ? welinkMessage.getTenantId() : ""
                        ))
                        .build())
                .build();

        return request;
    }

    @Override
    public WeLinkMessage adaptFromGateway(WebSocketMessage.WebSocketMessageBase gatewayMessage) {
        log.debug("Adapting gateway message to WeLink: type={}", gatewayMessage.getType());

        WeLinkMessage.WeLinkMessageBuilder builder = WeLinkMessage.builder();

        if (gatewayMessage instanceof OpenCodeMessage.AgentResponseChunk) {
            // Streaming chunk
            OpenCodeMessage.AgentResponseChunk chunk = (OpenCodeMessage.AgentResponseChunk) gatewayMessage;
            builder.content(extractChunkContent(chunk));
        } else if (gatewayMessage instanceof OpenCodeMessage.AgentResultResponse) {
            // Final result
            OpenCodeMessage.AgentResultResponse result = (OpenCodeMessage.AgentResultResponse) gatewayMessage;
            builder.content(extractResultContent(result));
        } else if (gatewayMessage instanceof OpenCodeMessage.AgentErrorResponse) {
            // Error
            OpenCodeMessage.AgentErrorResponse error = (OpenCodeMessage.AgentErrorResponse) gatewayMessage;
            builder.content("Error: " + error.getPayload().getMessage());
        } else if (gatewayMessage instanceof WebSocketMessage.SkillProgressResponse) {
            // Legacy skill progress
            WebSocketMessage.SkillProgressResponse progress = (WebSocketMessage.SkillProgressResponse) gatewayMessage;
            builder.content(progress.getPayload().getMessage());
        } else if (gatewayMessage instanceof WebSocketMessage.SkillResultResponse) {
            // Legacy skill result
            WebSocketMessage.SkillResultResponse result = (WebSocketMessage.SkillResultResponse) gatewayMessage;
            builder.content(extractSkillResultContent(result));
        } else if (gatewayMessage instanceof WebSocketMessage.SkillErrorResponse) {
            // Legacy skill error
            WebSocketMessage.SkillErrorResponse error = (WebSocketMessage.SkillErrorResponse) gatewayMessage;
            builder.content("Error: " + error.getPayload().getMessage());
        } else {
            // Unknown type - try to extract content
            builder.content(gatewayMessage.toString());
        }

        return builder.build();
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean isComplete(WebSocketMessage.WebSocketMessageBase gatewayMessage) {
        if (gatewayMessage instanceof OpenCodeMessage.AgentResponseChunk) {
            OpenCodeMessage.AgentResponseChunk chunk = (OpenCodeMessage.AgentResponseChunk) gatewayMessage;
            return chunk.getPayload().isDone();
        }
        if (gatewayMessage instanceof OpenCodeMessage.AgentResultResponse) {
            return true;
        }
        if (gatewayMessage instanceof OpenCodeMessage.AgentErrorResponse) {
            return true;
        }
        if (gatewayMessage instanceof WebSocketMessage.SkillResultResponse) {
            return true;
        }
        if (gatewayMessage instanceof WebSocketMessage.SkillErrorResponse) {
            return true;
        }
        return false;
    }

    @Override
    public String getExecuteMessageType() {
        return OpenCodeMessage.AgentExecuteRequest.TYPE;
    }

    /**
     * Extract content from streaming chunk
     */
    private String extractChunkContent(OpenCodeMessage.AgentResponseChunk chunk) {
        if (chunk.getPayload() != null && chunk.getPayload().getDelta() != null) {
            return chunk.getPayload().getDelta().getContent();
        }
        return "";
    }

    /**
     * Extract content from result response
     */
    private String extractResultContent(OpenCodeMessage.AgentResultResponse result) {
        if (result.getPayload() != null && result.getPayload().getMessage() != null) {
            return result.getPayload().getMessage().getContent();
        }
        return "";
    }

    /**
     * Extract content from skill result
     */
    private String extractSkillResultContent(WebSocketMessage.SkillResultResponse result) {
        if (result.getPayload() != null && result.getPayload().getResult() != null) {
            Object res = result.getPayload().getResult();
            if (res instanceof String) {
                return (String) res;
            }
            return res.toString();
        }
        return "";
    }
}