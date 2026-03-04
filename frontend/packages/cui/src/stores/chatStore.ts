import { create } from 'zustand';
import { Message, SkillSession, ProgressUpdate } from '../types';

interface ChatStore {
  // Messages
  messages: Message[];
  addMessage: (message: Message) => void;
  updateMessage: (id: string, updates: Partial<Message>) => void;
  clearMessages: () => void;

  // Session
  sessionId: string | null;
  setSessionId: (sessionId: string | null) => void;

  // Loading state
  isLoading: boolean;
  setIsLoading: (isLoading: boolean) => void;

  // Error
  error: string | null;
  setError: (error: string | null) => void;

  // Streaming
  isStreaming: boolean;
  setIsStreaming: (isStreaming: boolean) => void;
  streamingContent: string;
  appendStreamingContent: (content: string) => void;
  clearStreamingContent: () => void;

  // Skill execution
  skillSession: SkillSession | null;
  setSkillSession: (session: SkillSession | null) => void;
  progress: ProgressUpdate | null;
  setProgress: (progress: ProgressUpdate | null) => void;
}

export const useChatStore = create<ChatStore>((set) => ({
  // Messages
  messages: [],
  addMessage: (message) =>
    set((state) => ({
      messages: [...state.messages, message],
    })),
  updateMessage: (id, updates) =>
    set((state) => ({
      messages: state.messages.map((msg) =>
        msg.id === id ? { ...msg, ...updates } : msg
      ),
    })),
  clearMessages: () => set({ messages: [] }),

  // Session
  sessionId: null,
  setSessionId: (sessionId) => set({ sessionId }),

  // Loading
  isLoading: false,
  setIsLoading: (isLoading) => set({ isLoading }),

  // Error
  error: null,
  setError: (error) => set({ error }),

  // Streaming
  isStreaming: false,
  setIsStreaming: (isStreaming) => set({ isStreaming }),
  streamingContent: '',
  appendStreamingContent: (content) =>
    set((state) => ({
      streamingContent: state.streamingContent + content,
    })),
  clearStreamingContent: () => set({ streamingContent: '' }),

  // Skill execution
  skillSession: null,
  setSkillSession: (session) => set({ skillSession: session }),
  progress: null,
  setProgress: (progress) => set({ progress }),
}));

// Skill store for skill management
interface SkillStore {
  skills: Array<{
    id: string;
    name: string;
    description: string;
    isActive: boolean;
  }>;
  setSkills: (skills: SkillStore['skills']) => void;
  activeSkillId: string | null;
  setActiveSkillId: (id: string | null) => void;
}

export const useSkillStore = create<SkillStore>((set) => ({
  skills: [],
  setSkills: (skills) => set({ skills }),
  activeSkillId: null,
  setActiveSkillId: (id) => set({ activeSkillId: id }),
}));