-- =============================================
-- AI Open Platform - Initial Schema
-- Version: 1.0.0
-- Description: Core tables for skill management
-- =============================================

-- Skills table
CREATE TABLE skills (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version VARCHAR(50) DEFAULT '1.0.0',
    endpoint_url VARCHAR(500),
    auth_type ENUM('aksk', 'jwt', 'none') DEFAULT 'none',
    config JSON,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    INDEX idx_skills_name (name),
    INDEX idx_skills_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Skill Sessions table
CREATE TABLE skill_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) UNIQUE NOT NULL,
    skill_id BIGINT,
    user_id VARCHAR(100) NOT NULL,
    channel ENUM('cui', 'im', 'meeting', 'assistant_square') DEFAULT 'cui',
    status ENUM('pending', 'running', 'completed', 'failed', 'paused', 'cancelled') DEFAULT 'pending',
    input_data JSON,
    output_data JSON,
    error_message TEXT,
    metadata JSON,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_sessions_session_id (session_id),
    INDEX idx_sessions_user_id (user_id),
    INDEX idx_sessions_skill_id (skill_id),
    INDEX idx_sessions_status (status),
    INDEX idx_sessions_created_at (created_at),

    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Messages table
CREATE TABLE messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) NOT NULL,
    message_id VARCHAR(100) UNIQUE,
    role ENUM('user', 'assistant', 'system', 'tool') NOT NULL,
    content TEXT,
    tool_calls JSON,
    tool_call_id VARCHAR(100),
    name VARCHAR(255),
    metadata JSON,
    tokens_input INT DEFAULT 0,
    tokens_output INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_messages_session_id (session_id),
    INDEX idx_messages_role (role),
    INDEX idx_messages_created_at (created_at),

    FOREIGN KEY (session_id) REFERENCES skill_sessions(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Credentials table (AK/SK)
CREATE TABLE credentials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    access_key VARCHAR(100) UNIQUE NOT NULL,
    secret_key VARCHAR(255) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    skill_id BIGINT,
    permissions JSON,
    rate_limit INT DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,

    INDEX idx_credentials_access_key (access_key),
    INDEX idx_credentials_user_id (user_id),
    INDEX idx_credentials_skill_id (skill_id),
    INDEX idx_credentials_active (is_active),

    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- WebSocket Connections table (for tracking active connections)
CREATE TABLE websocket_connections (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    connection_id VARCHAR(100) UNIQUE NOT NULL,
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    client_ip VARCHAR(45),
    user_agent VARCHAR(500),
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    disconnected_at TIMESTAMP,
    status ENUM('connected', 'disconnected', 'error') DEFAULT 'connected',
    metadata JSON,

    INDEX idx_ws_connections_connection_id (connection_id),
    INDEX idx_ws_connections_session_id (session_id),
    INDEX idx_ws_connections_user_id (user_id),
    INDEX idx_ws_connections_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Skill Execution History table (for auditing and analytics)
CREATE TABLE skill_execution_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) NOT NULL,
    skill_id BIGINT,
    skill_name VARCHAR(255),
    user_id VARCHAR(100),
    channel VARCHAR(50),
    status VARCHAR(50),
    duration_ms BIGINT,
    input_tokens INT DEFAULT 0,
    output_tokens INT DEFAULT 0,
    error_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_history_session_id (session_id),
    INDEX idx_history_skill_id (skill_id),
    INDEX idx_history_user_id (user_id),
    INDEX idx_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;