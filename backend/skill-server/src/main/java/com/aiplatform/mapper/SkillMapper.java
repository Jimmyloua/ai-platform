package com.aiplatform.mapper;

import com.aiplatform.model.Skill;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper for Skill entities.
 */
@Mapper
public interface SkillMapper {

    /**
     * Find skill by ID
     */
    @Select("SELECT * FROM skills WHERE id = #{id}")
    Optional<Skill> findById(Long id);

    /**
     * Find all skills
     */
    @Select("SELECT * FROM skills")
    List<Skill> findAll();

    /**
     * Find skill by name
     */
    @Select("SELECT * FROM skills WHERE name = #{name}")
    Optional<Skill> findByName(String name);

    /**
     * Find skill by name and version
     */
    @Select("SELECT * FROM skills WHERE name = #{name} AND version = #{version}")
    Optional<Skill> findByNameAndVersion(@Param("name") String name, @Param("version") String version);

    /**
     * Find all active skills
     */
    @Select("SELECT * FROM skills WHERE is_active = true")
    List<Skill> findByIsActiveTrue();

    /**
     * Find active skill by ID
     */
    @Select("SELECT * FROM skills WHERE id = #{id} AND is_active = true")
    Optional<Skill> findByIdAndIsActiveTrue(Long id);

    /**
     * Find skills by auth type
     */
    @Select("SELECT * FROM skills WHERE auth_type = #{authType}")
    List<Skill> findByAuthType(String authType);

    /**
     * Check if skill exists by name
     */
    @Select("SELECT COUNT(*) > 0 FROM skills WHERE name = #{name}")
    boolean existsByName(String name);

    /**
     * Count active skills
     */
    @Select("SELECT COUNT(*) FROM skills WHERE is_active = true")
    long countActiveSkills();

    /**
     * Insert skill
     */
    @Insert("INSERT INTO skills (name, description, version, endpoint_url, auth_type, config, is_active, created_at, updated_at, created_by, updated_by) " +
            "VALUES (#{name}, #{description}, #{version}, #{endpointUrl}, #{authType}, #{config}, #{isActive}, NOW(), NOW(), #{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Skill skill);

    /**
     * Update skill
     */
    @Update("UPDATE skills SET name = #{name}, description = #{description}, version = #{version}, " +
            "endpoint_url = #{endpointUrl}, auth_type = #{authType}, config = #{config}, " +
            "is_active = #{isActive}, updated_at = NOW(), updated_by = #{updatedBy} WHERE id = #{id}")
    int update(Skill skill);

    /**
     * Delete skill
     */
    @Delete("DELETE FROM skills WHERE id = #{id}")
    int deleteById(Long id);
}