package com.aiplatform.mapper;

import com.aiplatform.model.Credential;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper for Credential entities.
 */
@Mapper
public interface CredentialMapper {

    @Select("SELECT * FROM credentials WHERE id = #{id}")
    Optional<Credential> findById(Long id);

    @Select("SELECT * FROM credentials")
    List<Credential> findAll();

    @Select("SELECT * FROM credentials WHERE access_key = #{accessKey}")
    Optional<Credential> findByAccessKey(String accessKey);

    @Select("SELECT * FROM credentials WHERE user_id = #{userId}")
    List<Credential> findByUserId(String userId);

    @Select("SELECT * FROM credentials WHERE skill_id = #{skillId}")
    List<Credential> findBySkillId(Long skillId);

    @Select("SELECT * FROM credentials WHERE access_key = #{accessKey} AND is_active = true")
    Optional<Credential> findActiveByAccessKey(String accessKey);

    @Select("SELECT * FROM credentials WHERE access_key = #{accessKey} AND is_active = true AND (expires_at IS NULL OR expires_at > #{now})")
    Optional<Credential> findValidByAccessKey(@Param("accessKey") String accessKey, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) > 0 FROM credentials WHERE access_key = #{accessKey}")
    boolean existsByAccessKey(String accessKey);

    @Select("SELECT * FROM credentials WHERE expires_at IS NOT NULL AND expires_at < #{now} AND is_active = true")
    List<Credential> findExpiredCredentials(LocalDateTime now);

    @Select("SELECT COUNT(*) FROM credentials WHERE user_id = #{userId} AND is_active = true")
    long countActiveByUserId(String userId);

    @Insert("INSERT INTO credentials (access_key, secret_key, user_id, skill_id, permissions, rate_limit, is_active, expires_at, created_at, updated_at, last_used_at) " +
            "VALUES (#{accessKey}, #{secretKey}, #{userId}, #{skillId}, #{permissions}, #{rateLimit}, #{isActive}, #{expiresAt}, NOW(), NOW(), #{lastUsedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Credential credential);

    @Update("UPDATE credentials SET access_key = #{accessKey}, secret_key = #{secretKey}, user_id = #{userId}, " +
            "skill_id = #{skillId}, permissions = #{permissions}, rate_limit = #{rateLimit}, is_active = #{isActive}, " +
            "expires_at = #{expiresAt}, updated_at = NOW(), last_used_at = #{lastUsedAt} WHERE id = #{id}")
    int update(Credential credential);

    @Update("UPDATE credentials SET last_used_at = #{lastUsedAt} WHERE access_key = #{accessKey}")
    int updateLastUsedAt(@Param("accessKey") String accessKey, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    @Update("UPDATE credentials SET is_active = false, updated_at = #{now} WHERE id = #{id}")
    int deactivate(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Delete("DELETE FROM credentials WHERE id = #{id}")
    int deleteById(Long id);
}