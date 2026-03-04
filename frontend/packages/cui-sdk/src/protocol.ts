/**
 * Protocol types matching the backend WebSocket messages
 */

// Base message types
export type MessageRole = 'user' | 'assistant' | 'system' | 'tool';

export interface Message {
  id: string;
  role: MessageRole;
  content: string;
  name?: string;
  toolCalls?: ToolCall[];
  toolCallId?: string;
  createdAt: Date;
  metadata?: Record<string, unknown>;
}

export interface ToolCall {
  id: string;
  type: 'function';
  function: {
    name: string;
    arguments: string;
  };
}

export interface ToolResult {
  toolCallId: string;
  name: string;
  content: string;
  status: 'success' | 'error';
}

// Skill types
export interface Skill {
  id: string;
  name: string;
  description: string;
  version: string;
  endpointUrl: string;
  authType: 'aksk' | 'jwt' | 'none';
  config?: Record<string, unknown>;
  isActive: boolean;
}

export interface SkillSession {
  sessionId: string;
  skillId: string;
  userId: string;
  channel: 'cui' | 'im' | 'meeting' | 'assistant_square';
  status: 'pending' | 'running' | 'completed' | 'failed' | 'paused' | 'cancelled';
  inputData?: Record<string, unknown>;
  outputData?: Record<string, unknown>;
  errorMessage?: string;
  startedAt?: Date;
  completedAt?: Date;
  createdAt: Date;
}

export interface SkillExecutionOptions {
  skillId?: string;
  skillName?: string;
  input?: Record<string, unknown>;
  options?: Record<string, unknown>;
  channel?: string;
}

export interface ProgressUpdate {
  progress: number;
  step: string;
  message?: string;
  data?: Record<string, unknown>;
}

export interface Usage {
  inputTokens: number;
  outputTokens: number;
  durationMs: number;
}

// WebSocket message types
export type WebSocketMessageType =
  | 'skill.execute'
  | 'skill.progress'
  | 'skill.result'
  | 'skill.error'
  | 'skill.pause'
  | 'skill.resume'
  | 'skill.cancel'
  | 'chat.completion.chunk'
  | 'heartbeat';

export interface WebSocketMessageBase {
  type: WebSocketMessageType;
  sessionId?: string;
  timestamp?: number;
  metadata?: Record<string, unknown>;
}

export interface SkillExecuteMessage extends WebSocketMessageBase {
  type: 'skill.execute';
  payload: SkillExecutionOptions;
}

export interface SkillProgressMessage extends WebSocketMessageBase {
  type: 'skill.progress';
  payload: ProgressUpdate;
}

export interface SkillResultMessage extends WebSocketMessageBase {
  type: 'skill.result';
  payload: {
    result: unknown;
    metadata?: Record<string, unknown>;
    usage?: Usage;
  };
}

export interface SkillErrorMessage extends WebSocketMessageBase {
  type: 'skill.error';
  payload: {
    code: string;
    message: string;
    details?: string;
  };
}

export interface SkillPauseMessage extends WebSocketMessageBase {
  type: 'skill.pause';
}

export interface SkillResumeMessage extends WebSocketMessageBase {
  type: 'skill.resume';
}

export interface SkillCancelMessage extends WebSocketMessageBase {
  type: 'skill.cancel';
}

export interface ChatCompletionChunk extends WebSocketMessageBase {
  type: 'chat.completion.chunk';
  id: string;
  object: string;
  created: number;
  model: string;
  choices: Array<{
    index: number;
    delta: {
      role?: string;
      content?: string;
      toolCalls?: ToolCall[];
    };
    finishReason: string | null;
  }>;
}

export interface HeartbeatMessage extends WebSocketMessageBase {
  type: 'heartbeat';
}

export type WebSocketMessage =
  | SkillExecuteMessage
  | SkillProgressMessage
  | SkillResultMessage
  | SkillErrorMessage
  | SkillPauseMessage
  | SkillResumeMessage
  | SkillCancelMessage
  | ChatCompletionChunk
  | HeartbeatMessage
  | WebSocketMessageBase;

// API Response types
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
}

// Configuration types
export interface ClientConfig {
  baseUrl: string;
  wsUrl: string;
  timeout?: number;
  retries?: number;
  auth?: {
    type: 'aksk' | 'jwt' | 'none';
    accessKey?: string;
    secretKey?: string;
    token?: string;
  };
}

// Chat completion types (OpenAI-compatible)
export interface ChatCompletionRequest {
  model: string;
  messages: Array<{
    role: MessageRole;
    content: string;
    name?: string;
  }>;
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  stream?: boolean;
  tools?: Array<{
    type: 'function';
    function: {
      name: string;
      description?: string;
      parameters?: Record<string, unknown>;
    };
  }>;
}

export interface ChatCompletionResponse {
  id: string;
  object: string;
  created: number;
  model: string;
  choices: Array<{
    index: number;
    message: {
      role: string;
      content: string;
      toolCalls?: ToolCall[];
    };
    finishReason: string;
  }>;
  usage: Usage;
}