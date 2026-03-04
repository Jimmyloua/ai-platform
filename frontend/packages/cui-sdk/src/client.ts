import { v4 as uuidv4 } from 'uuid';
import type {
  ClientConfig,
  WebSocketMessage,
  SkillExecutionOptions,
  ChatCompletionRequest,
  ChatCompletionResponse,
  ApiResponse,
  Skill,
  SkillSession,
  PaginatedResponse,
} from './protocol';
import { generateSignature, signRequest } from './auth';

export class AIClient {
  private config: ClientConfig;
  private ws: WebSocket | null = null;
  private messageHandlers: Map<string, Set<(msg: WebSocketMessage) => void>> = new Map();
  private connectionPromise: Promise<void> | null = null;

  constructor(config: ClientConfig) {
    this.config = {
      timeout: 30000,
      retries: 3,
      ...config,
    };
  }

  // WebSocket connection management
  async connect(): Promise<void> {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    this.connectionPromise = new Promise((resolve, reject) => {
      const ws = new WebSocket(this.config.wsUrl);

      ws.onopen = () => {
        this.ws = ws;
        resolve();
      };

      ws.onerror = (error) => {
        reject(error);
      };

      ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data) as WebSocketMessage;
          this.handleMessage(message);
        } catch (e) {
          console.error('Failed to parse WebSocket message:', e);
        }
      };

      ws.onclose = () => {
        this.ws = null;
      };
    });

    return this.connectionPromise;
  }

  disconnect(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  // Message handling
  private handleMessage(message: WebSocketMessage): void {
    const handlers = this.messageHandlers.get(message.type);
    if (handlers) {
      handlers.forEach((handler) => handler(message));
    }
    // Also call 'any' handlers
    const anyHandlers = this.messageHandlers.get('*');
    if (anyHandlers) {
      anyHandlers.forEach((handler) => handler(message));
    }
  }

  on(type: string, handler: (msg: WebSocketMessage) => void): () => void {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, new Set());
    }
    this.messageHandlers.get(type)!.add(handler);

    return () => {
      this.messageHandlers.get(type)?.delete(handler);
    };
  }

  off(type: string, handler: (msg: WebSocketMessage) => void): void {
    this.messageHandlers.get(type)?.delete(handler);
  }

  // Send messages
  send(message: Partial<WebSocketMessage>): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not connected');
    }

    const fullMessage: WebSocketMessage = {
      ...message,
      timestamp: Date.now(),
    } as WebSocketMessage;

    this.ws.send(JSON.stringify(fullMessage));
  }

  // Skill execution
  async executeSkill(options: SkillExecutionOptions): Promise<string> {
    const sessionId = uuidv4();

    this.send({
      type: 'skill.execute',
      sessionId,
      payload: options,
    });

    return sessionId;
  }

  pauseSkill(sessionId: string): void {
    this.send({
      type: 'skill.pause',
      sessionId,
    });
  }

  resumeSkill(sessionId: string): void {
    this.send({
      type: 'skill.resume',
      sessionId,
    });
  }

  cancelSkill(sessionId: string): void {
    this.send({
      type: 'skill.cancel',
      sessionId,
    });
  }

  // HTTP API methods
  private async request<T>(
    method: string,
    path: string,
    body?: unknown
  ): Promise<T> {
    const url = `${this.config.baseUrl}${path}`;
    const timestamp = Date.now().toString();
    const bodyStr = body ? JSON.stringify(body) : '';

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Timestamp': timestamp,
    };

    // Add authentication
    if (this.config.auth?.type === 'aksk') {
      const signature = signRequest(
        this.config.auth.accessKey!,
        this.config.auth.secretKey!,
        method,
        path,
        timestamp,
        bodyStr
      );
      headers['X-Access-Key'] = this.config.auth.accessKey!;
      headers['X-Signature'] = signature;
    } else if (this.config.auth?.type === 'jwt' && this.config.auth.token) {
      headers['Authorization'] = `Bearer ${this.config.auth.token}`;
    }

    const response = await fetch(url, {
      method,
      headers,
      body: bodyStr || undefined,
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }));
      throw new Error(error.message || `HTTP ${response.status}`);
    }

    return response.json();
  }

  // Skill management
  async getSkills(): Promise<ApiResponse<PaginatedResponse<Skill>>> {
    return this.request('GET', '/api/v1/skills');
  }

  async getSkill(skillId: string): Promise<ApiResponse<Skill>> {
    return this.request('GET', `/api/v1/skills/${skillId}`);
  }

  async getSession(sessionId: string): Promise<ApiResponse<SkillSession>> {
    return this.request('GET', `/api/v1/sessions/${sessionId}`);
  }

  async getSessions(params?: {
    skillId?: string;
    status?: string;
    page?: number;
    pageSize?: number;
  }): Promise<ApiResponse<PaginatedResponse<SkillSession>>> {
    const query = new URLSearchParams();
    if (params?.skillId) query.set('skillId', params.skillId);
    if (params?.status) query.set('status', params.status);
    if (params?.page) query.set('page', params.page.toString());
    if (params?.pageSize) query.set('pageSize', params.pageSize.toString());

    const queryStr = query.toString();
    return this.request('GET', `/api/v1/sessions${queryStr ? `?${queryStr}` : ''}`);
  }

  // Chat completion (OpenAI-compatible)
  async createChatCompletion(
    request: ChatCompletionRequest
  ): Promise<ChatCompletionResponse> {
    return this.request('POST', '/v1/chat/completions', request);
  }

  // Streaming chat completion
  async *streamChatCompletion(
    request: ChatCompletionRequest
  ): AsyncGenerator<string> {
    const url = `${this.config.baseUrl}/v1/chat/completions`;
    const timestamp = Date.now().toString();

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Timestamp': timestamp,
    };

    if (this.config.auth?.type === 'aksk') {
      const body = JSON.stringify({ ...request, stream: true });
      const signature = signRequest(
        this.config.auth.accessKey!,
        this.config.auth.secretKey!,
        'POST',
        '/v1/chat/completions',
        timestamp,
        body
      );
      headers['X-Access-Key'] = this.config.auth.accessKey!;
      headers['X-Signature'] = signature;
    } else if (this.config.auth?.type === 'jwt' && this.config.auth.token) {
      headers['Authorization'] = `Bearer ${this.config.auth.token}`;
    }

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({ ...request, stream: true }),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const reader = response.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.slice(6);
          if (data === '[DONE]') return;
          yield data;
        }
      }
    }
  }
}

// Factory function
export function createClient(config: ClientConfig): AIClient {
  return new AIClient(config);
}