import { useState, useCallback, useEffect, useRef } from 'react';
import { useCUIContext } from '../context';
import type { Message, WebSocketMessage, ChatCompletionChunk } from '@aiplatform/cui-sdk';

interface UseChatOptions {
  onMessage?: (message: WebSocketMessage) => void;
  onProgress?: (progress: { progress: number; step: string; message?: string }) => void;
  onError?: (error: Error) => void;
}

interface UseChatReturn {
  messages: Message[];
  isLoading: boolean;
  isStreaming: boolean;
  streamingContent: string;
  error: string | null;
  sendMessage: (content: string) => Promise<void>;
  clearMessages: () => void;
  reconnect: () => void;
}

export function useChat(options: UseChatOptions = {}): UseChatReturn {
  const { client, isConnected, connect } = useCUIContext();
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingContent, setStreamingContent] = useState('');
  const [error, setError] = useState<string | null>(null);
  const streamingRef = useRef<string>('');

  useEffect(() => {
    if (!client || !isConnected) return;

    const unsubscribers: (() => void)[] = [];

    // Handle all messages
    unsubscribers.push(
      client.on('*', (msg: WebSocketMessage) => {
        options.onMessage?.(msg);
      })
    );

    // Handle progress updates
    unsubscribers.push(
      client.on('skill.progress', (msg: WebSocketMessage) => {
        const progressMsg = msg as any;
        options.onProgress?.(progressMsg.payload);
      })
    );

    // Handle streaming chunks
    unsubscribers.push(
      client.on('chat.completion.chunk', (msg: WebSocketMessage) => {
        const chunk = msg as ChatCompletionChunk;
        if (chunk.choices?.[0]?.delta?.content) {
          streamingRef.current += chunk.choices[0].delta.content;
          setStreamingContent(streamingRef.current);
        }
        if (chunk.choices?.[0]?.finishReason) {
          setIsStreaming(false);
          const streamedMessage: Message = {
            id: `msg-${Date.now()}-streamed`,
            role: 'assistant',
            content: streamingRef.current,
            createdAt: new Date(),
          };
          setMessages((prev) => [...prev, streamedMessage]);
          streamingRef.current = '';
          setStreamingContent('');
        }
      })
    );

    // Handle results
    unsubscribers.push(
      client.on('skill.result', (msg: WebSocketMessage) => {
        setIsLoading(false);
        const resultMsg = msg as any;
        const message: Message = {
          id: `msg-${Date.now()}-result`,
          role: 'assistant',
          content: typeof resultMsg.payload.result === 'string'
            ? resultMsg.payload.result
            : JSON.stringify(resultMsg.payload.result, null, 2),
          createdAt: new Date(),
        };
        setMessages((prev) => [...prev, message]);
      })
    );

    // Handle errors
    unsubscribers.push(
      client.on('skill.error', (msg: WebSocketMessage) => {
        setIsLoading(false);
        const errorMsg = msg as any;
        const errorMessage = errorMsg.payload.message || 'An error occurred';
        setError(errorMessage);
        options.onError?.(new Error(errorMessage));
      })
    );

    return () => {
      unsubscribers.forEach((unsub) => unsub());
    };
  }, [client, isConnected, options]);

  const sendMessage = useCallback(
    async (content: string) => {
      if (!content.trim() || !client || !isConnected) return;

      const userMessage: Message = {
        id: `msg-${Date.now()}`,
        role: 'user',
        content: content.trim(),
        createdAt: new Date(),
      };

      setMessages((prev) => [...prev, userMessage]);
      setIsLoading(true);
      setError(null);

      try {
        // Execute skill or send message
        await client.executeSkill({
          input: { message: content.trim() },
        });
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'An error occurred';
        setError(errorMessage);
        options.onError?.(new Error(errorMessage));
      } finally {
        setIsLoading(false);
      }
    },
    [client, isConnected, options]
  );

  const clearMessages = useCallback(() => {
    setMessages([]);
    setError(null);
  }, []);

  const reconnect = useCallback(async () => {
    await connect();
  }, [connect]);

  return {
    messages,
    isLoading,
    isStreaming,
    streamingContent,
    error,
    sendMessage,
    clearMessages,
    reconnect,
  };
}