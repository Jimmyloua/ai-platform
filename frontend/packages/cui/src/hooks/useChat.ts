import { useCallback } from 'react';
import { useChatStore } from '../stores/chatStore';
import { Message, WebSocketMessage, SkillExecutionOptions } from '../types';

export function useChat() {
  const {
    messages,
    addMessage,
    updateMessage,
    clearMessages,
    sessionId,
    setSessionId,
    isLoading,
    setIsLoading,
    error,
    setError,
    isStreaming,
    setIsStreaming,
    streamingContent,
    appendStreamingContent,
    clearStreamingContent,
    skillSession,
    setSkillSession,
    progress,
    setProgress,
  } = useChatStore();

  const sendMessage = useCallback(
    async (content: string, options?: { useStreaming?: boolean }) => {
      if (!content.trim()) return;

      // Add user message
      const userMessage: Message = {
        id: `msg-${Date.now()}`,
        role: 'user',
        content: content.trim(),
        createdAt: new Date(),
      };
      addMessage(userMessage);

      // Set loading state
      setIsLoading(true);
      setError(null);

      try {
        // In a real implementation, this would send to WebSocket or API
        // For now, we'll simulate a response
        if (options?.useStreaming) {
          setIsStreaming(true);
          // Streaming would be handled by WebSocket
        } else {
          // Simulate API call
          await new Promise((resolve) => setTimeout(resolve, 1000));

          const assistantMessage: Message = {
            id: `msg-${Date.now()}-response`,
            role: 'assistant',
            content: `Response to: ${content}`,
            createdAt: new Date(),
          };
          addMessage(assistantMessage);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An error occurred');
      } finally {
        setIsLoading(false);
        setIsStreaming(false);
      }
    },
    [addMessage, setIsLoading, setError, setIsStreaming]
  );

  const handleWebSocketMessage = useCallback(
    (message: WebSocketMessage) => {
      switch (message.type) {
        case 'skill.progress':
          setProgress((message as any).payload);
          break;

        case 'skill.result':
          setProgress(null);
          setIsLoading(false);
          const resultMsg: Message = {
            id: `msg-${Date.now()}-result`,
            role: 'assistant',
            content: JSON.stringify((message as any).payload.result, null, 2),
            createdAt: new Date(),
          };
          addMessage(resultMsg);
          break;

        case 'skill.error':
          setProgress(null);
          setIsLoading(false);
          setError((message as any).payload.message);
          break;

        case 'chat.completion.chunk':
          const chunk = message as any;
          if (chunk.choices?.[0]?.delta?.content) {
            appendStreamingContent(chunk.choices[0].delta.content);
          }
          if (chunk.choices?.[0]?.finishReason) {
            setIsStreaming(false);
            const streamedMessage: Message = {
              id: `msg-${Date.now()}-streamed`,
              role: 'assistant',
              content: streamingContent,
              createdAt: new Date(),
            };
            addMessage(streamedMessage);
            clearStreamingContent();
          }
          break;
      }
    },
    [setProgress, setIsLoading, addMessage, setError, appendStreamingContent, streamingContent, clearStreamingContent, setIsStreaming]
  );

  const executeSkill = useCallback(
    (options: SkillExecutionOptions) => {
      const message: WebSocketMessage = {
        type: 'skill.execute',
        sessionId: sessionId || undefined,
        payload: options,
      };
      // This would be sent via WebSocket
      console.log('Execute skill:', message);
      setIsLoading(true);
    },
    [sessionId, setIsLoading]
  );

  const pauseSkill = useCallback(() => {
    if (!sessionId) return;
    const message: WebSocketMessage = {
      type: 'skill.pause',
      sessionId,
    };
    console.log('Pause skill:', message);
  }, [sessionId]);

  const resumeSkill = useCallback(() => {
    if (!sessionId) return;
    const message: WebSocketMessage = {
      type: 'skill.resume',
      sessionId,
    };
    console.log('Resume skill:', message);
  }, [sessionId]);

  const cancelSkill = useCallback(() => {
    if (!sessionId) return;
    const message: WebSocketMessage = {
      type: 'skill.cancel',
      sessionId,
    };
    console.log('Cancel skill:', message);
    setIsLoading(false);
    setProgress(null);
  }, [sessionId, setIsLoading, setProgress]);

  return {
    // State
    messages,
    isLoading,
    error,
    isStreaming,
    streamingContent,
    sessionId,
    skillSession,
    progress,

    // Actions
    sendMessage,
    clearMessages,
    executeSkill,
    pauseSkill,
    resumeSkill,
    cancelSkill,
    setSessionId,
    handleWebSocketMessage,
  };
}