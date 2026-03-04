export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system' | 'tool';
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

export interface ChatState {
  messages: Message[];
  isLoading: boolean;
  error: string | null;
  sessionId: string | null;
}

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

export type WebSocketMessage =
  | SkillExecuteMessage
  | SkillProgressMessage
  | SkillResultMessage
  | SkillErrorMessage
  | ChatCompletionChunk
  | WebSocketMessageBase;