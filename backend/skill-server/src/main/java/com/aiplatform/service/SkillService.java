package com.aiplatform.service;

import com.aiplatform.mapper.SkillMapper;
import com.aiplatform.model.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for skill management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillMapper skillMapper;

    /**
     * Get skill by ID
     */
    public Optional<Skill> getSkill(Long id) {
        return skillMapper.findById(id);
    }

    /**
     * Get skill by name
     */
    public Optional<Skill> getSkillByName(String name) {
        return skillMapper.findByName(name);
    }

    /**
     * Get skill by name and version
     */
    public Optional<Skill> getSkillByNameAndVersion(String name, String version) {
        return skillMapper.findByNameAndVersion(name, version);
    }

    /**
     * Get all skills
     */
    public List<Skill> getAllSkills() {
        return skillMapper.findAll();
    }

    /**
     * Get all active skills
     */
    public List<Skill> getActiveSkills() {
        return skillMapper.findByIsActiveTrue();
    }

    /**
     * Get active skill by ID
     */
    public Optional<Skill> getActiveSkill(Long id) {
        return skillMapper.findByIdAndIsActiveTrue(id);
    }

    /**
     * Get skills by auth type
     */
    public List<Skill> getSkillsByAuthType(Skill.AuthType authType) {
        return skillMapper.findByAuthType(authType.name());
    }

    /**
     * Create skill
     */
    @Transactional
    public Skill createSkill(Skill skill) {
        skillMapper.insert(skill);
        return skill;
    }

    /**
     * Update skill
     */
    @Transactional
    public Skill updateSkill(Skill skill) {
        skillMapper.update(skill);
        return skill;
    }

    /**
     * Delete skill
     */
    @Transactional
    public void deleteSkill(Long id) {
        skillMapper.deleteById(id);
    }

    /**
     * Check if skill exists by name
     */
    public boolean existsByName(String name) {
        return skillMapper.existsByName(name);
    }

    /**
     * Count active skills
     */
    public long countActiveSkills() {
        return skillMapper.countActiveSkills();
    }
}