import { useState, useCallback, useEffect } from 'react';
import { useCUIContext } from '../context';
import type {
  Skill,
  SkillSession,
  SkillExecutionOptions,
  ProgressUpdate,
  WebSocketMessage,
} from '@aiplatform/cui-sdk';

interface UseSkillOptions {
  skillId?: string;
  autoFetch?: boolean;
}

interface UseSkillReturn {
  // Skill data
  skills: Skill[];
  activeSkill: Skill | null;
  skillSession: SkillSession | null;
  progress: ProgressUpdate | null;

  // Status
  isLoading: boolean;
  isExecuting: boolean;
  error: string | null;
  status: 'idle' | 'running' | 'completed' | 'failed' | 'paused';

  // Actions
  fetchSkills: () => Promise<void>;
  setActiveSkill: (skill: Skill | null) => void;
  execute: (options?: Partial<SkillExecutionOptions>) => Promise<string>;
  pause: () => void;
  resume: () => void;
  cancel: () => void;
  reset: () => void;
}

export function useSkill(options: UseSkillOptions = {}): UseSkillReturn {
  const { skillId, autoFetch = true } = options;
  const { client, isConnected } = useCUIContext();

  const [skills, setSkills] = useState<Skill[]>([]);
  const [activeSkill, setActiveSkill] = useState<Skill | null>(null);
  const [skillSession, setSkillSession] = useState<SkillSession | null>(null);
  const [progress, setProgress] = useState<ProgressUpdate | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<'idle' | 'running' | 'completed' | 'failed' | 'paused'>('idle');
  const [sessionId, setSessionId] = useState<string | null>(null);

  // Fetch skills on mount
  const fetchSkills = useCallback(async () => {
    if (!client) return;

    setIsLoading(true);
    try {
      const response = await client.getSkills();
      if (response.success && response.data) {
        setSkills(response.data.items);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch skills');
    } finally {
      setIsLoading(false);
    }
  }, [client]);

  useEffect(() => {
    if (autoFetch && client) {
      fetchSkills();
    }
  }, [autoFetch, client, fetchSkills]);

  // Set active skill from skillId prop
  useEffect(() => {
    if (skillId && skills.length > 0) {
      const skill = skills.find((s) => s.id === skillId);
      if (skill) {
        setActiveSkill(skill);
      }
    }
  }, [skillId, skills]);

  // Handle WebSocket messages for skill execution
  useEffect(() => {
    if (!client || !isConnected) return;

    const unsubscribers: (() => void)[] = [];

    unsubscribers.push(
      client.on('skill.progress', (msg: WebSocketMessage) => {
        const progressMsg = msg as any;
        setProgress(progressMsg.payload);
      })
    );

    unsubscribers.push(
      client.on('skill.result', (msg: WebSocketMessage) => {
        setIsExecuting(false);
        setStatus('completed');
        setProgress(null);
      })
    );

    unsubscribers.push(
      client.on('skill.error', (msg: WebSocketMessage) => {
        setIsExecuting(false);
        setStatus('failed');
        setProgress(null);
        const errorMsg = msg as any;
        setError(errorMsg.payload.message);
      })
    );

    return () => {
      unsubscribers.forEach((unsub) => unsub());
    };
  }, [client, isConnected]);

  const execute = useCallback(
    async (executionOptions?: Partial<SkillExecutionOptions>) => {
      if (!client || !isConnected) {
        throw new Error('Client not connected');
      }

      const targetSkillId = executionOptions?.skillId || activeSkill?.id || skillId;
      if (!targetSkillId) {
        throw new Error('No skill specified for execution');
      }

      setIsExecuting(true);
      setStatus('running');
      setError(null);
      setProgress(null);

      const options: SkillExecutionOptions = {
        skillId: targetSkillId,
        ...executionOptions,
      };

      const newSessionId = await client.executeSkill(options);
      setSessionId(newSessionId);

      return newSessionId;
    },
    [client, isConnected, activeSkill, skillId]
  );

  const pause = useCallback(() => {
    if (!client || !sessionId) return;
    client.pauseSkill(sessionId);
    setStatus('paused');
  }, [client, sessionId]);

  const resume = useCallback(() => {
    if (!client || !sessionId) return;
    client.resumeSkill(sessionId);
    setStatus('running');
  }, [client, sessionId]);

  const cancel = useCallback(() => {
    if (!client || !sessionId) return;
    client.cancelSkill(sessionId);
    setIsExecuting(false);
    setStatus('idle');
    setProgress(null);
  }, [client, sessionId]);

  const reset = useCallback(() => {
    setSkillSession(null);
    setProgress(null);
    setError(null);
    setStatus('idle');
    setSessionId(null);
  }, []);

  return {
    skills,
    activeSkill,
    skillSession,
    progress,
    isLoading,
    isExecuting,
    error,
    status,
    fetchSkills,
    setActiveSkill,
    execute,
    pause,
    resume,
    cancel,
    reset,
  };
}