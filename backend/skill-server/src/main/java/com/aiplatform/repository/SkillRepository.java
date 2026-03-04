package com.aiplatform.repository;

import com.aiplatform.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Skill entities.
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    /**
     * Find skill by name
     */
    Optional<Skill> findByName(String name);

    /**
     * Find skill by name and version
     */
    Optional<Skill> findByNameAndVersion(String name, String version);

    /**
     * Find all active skills
     */
    List<Skill> findByIsActiveTrue();

    /**
     * Find active skill by ID
     */
    Optional<Skill> findByIdAndIsActiveTrue(Long id);

    /**
     * Find skills by auth type
     */
    List<Skill> findByAuthType(Skill.AuthType authType);

    /**
     * Check if skill exists by name
     */
    boolean existsByName(String name);

    /**
     * Count active skills
     */
    @Query("SELECT COUNT(s) FROM Skill s WHERE s.isActive = true")
    long countActiveSkills();
}