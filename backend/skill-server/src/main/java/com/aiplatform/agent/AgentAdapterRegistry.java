package com.aiplatform.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for agent adapters.
 * Manages adapter instances and routes requests to appropriate adapters.
 */
@Slf4j
@Component
public class AgentAdapterRegistry {

    private final List<AgentAdapter> adapters;
    private final Map<String, AgentAdapter> adapterMap = new ConcurrentHashMap<>();

    public AgentAdapterRegistry(List<AgentAdapter> adapters) {
        this.adapters = adapters;
    }

    @PostConstruct
    public void init() {
        // Register all adapters
        for (AgentAdapter adapter : adapters) {
            registerAdapter(adapter);
        }
        log.info("Registered {} agent adapters: {}", adapterMap.size(), adapterMap.keySet());
    }

    /**
     * Register an adapter
     */
    public void registerAdapter(AgentAdapter adapter) {
        String type = adapter.getAgentType();
        if (adapterMap.containsKey(type)) {
            log.warn("Overwriting existing adapter for type: {}", type);
        }
        adapterMap.put(type, adapter);
        log.debug("Registered agent adapter: {}", type);
    }

    /**
     * Get adapter for the given protocol type
     * @param protocolType protocol type (e.g., "skill.execute", "agent.execute")
     * @return appropriate adapter or default adapter
     */
    public AgentAdapter getAdapter(String protocolType) {
        // Direct mapping for specific protocol types
        if (protocolType != null) {
            // Agent execute goes to OpenCode adapter
            if (protocolType.startsWith("agent.")) {
                AgentAdapter adapter = adapterMap.get("OPENCODE");
                if (adapter != null) {
                    return adapter;
                }
            }
            // Skill execute goes to Default adapter
            if (protocolType.startsWith("skill.")) {
                AgentAdapter adapter = adapterMap.get("DEFAULT");
                if (adapter != null) {
                    return adapter;
                }
            }
        }

        // Fall back to default adapter
        AgentAdapter defaultAdapter = adapterMap.get("DEFAULT");
        if (defaultAdapter == null) {
            throw new IllegalStateException("No DEFAULT agent adapter registered");
        }
        return defaultAdapter;
    }

    /**
     * Get adapter by agent type
     * @param agentType agent type identifier
     * @return adapter or null if not found
     */
    public AgentAdapter getAdapterByType(String agentType) {
        return adapterMap.get(agentType);
    }

    /**
     * Check if an adapter exists for the given type
     */
    public boolean hasAdapter(String agentType) {
        return adapterMap.containsKey(agentType);
    }

    /**
     * Get all registered adapter types
     */
    public java.util.Set<String> getRegisteredTypes() {
        return java.util.Collections.unmodifiableSet(adapterMap.keySet());
    }
}