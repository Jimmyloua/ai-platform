package com.aiplatform.mapper;

import com.aiplatform.model.Message;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper for Message entities.
 */
@Mapper
public interface MessageMapper {

    @Select("SELECT * FROM messages WHERE id = #{id}")
    Optional<Message> findById(Long id);

    @Select("SELECT * FROM messages WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Select("SELECT * FROM messages WHERE message_id = #{messageId}")
    Optional<Message> findByMessageId(String messageId);

    @Select("SELECT * FROM messages WHERE session_id = #{sessionId} AND role = #{role}")
    List<Message> findBySessionIdAndRole(@Param("sessionId") String sessionId, @Param("role") String role);

    @Select("SELECT * FROM messages WHERE session_id = #{sessionId} ORDER BY created_at DESC LIMIT 1")
    Optional<Message> findLastMessage(String sessionId);

    @Select("SELECT COUNT(*) FROM messages WHERE session_id = #{sessionId}")
    long countBySessionId(String sessionId);

    @Select("SELECT COUNT(*) FROM messages WHERE session_id = #{sessionId} AND role = #{role}")
    long countBySessionIdAndRole(@Param("sessionId") String sessionId, @Param("role") String role);

    @Select("SELECT COALESCE(SUM(tokens_input), 0) FROM messages WHERE session_id = #{sessionId}")
    int sumInputTokensBySessionId(String sessionId);

    @Select("SELECT COALESCE(SUM(tokens_output), 0) FROM messages WHERE session_id = #{sessionId}")
    int sumOutputTokensBySessionId(String sessionId);

    @Select("SELECT * FROM messages WHERE session_id = #{sessionId} AND tool_calls IS NOT NULL")
    List<Message> findMessagesWithToolCalls(String sessionId);

    @Insert("INSERT INTO messages (session_id, message_id, role, content, tool_calls, tool_call_id, name, metadata, tokens_input, tokens_output, created_at) " +
            "VALUES (#{sessionId}, #{messageId}, #{role}, #{content}, #{toolCalls}, #{toolCallId}, #{name}, #{metadata}, #{tokensInput}, #{tokensOutput}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    @Update("UPDATE messages SET session_id = #{sessionId}, message_id = #{messageId}, role = #{role}, " +
            "content = #{content}, tool_calls = #{toolCalls}, tool_call_id = #{toolCallId}, " +
            "name = #{name}, metadata = #{metadata}, tokens_input = #{tokensInput}, tokens_output = #{tokensOutput} WHERE id = #{id}")
    int update(Message message);

    @Delete("DELETE FROM messages WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM messages WHERE session_id = #{sessionId}")
    int deleteBySessionId(String sessionId);
}