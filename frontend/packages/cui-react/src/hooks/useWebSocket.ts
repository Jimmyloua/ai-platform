import { useState, useCallback, useEffect, useRef } from 'react';
import { useCUIContext } from '../context';
import type { WebSocketMessage } from '@aiplatform/cui-sdk';

interface UseWebSocketOptions {
  onOpen?: () => void;
  onClose?: () => void;
  onError?: (error: Event) => void;
  onMessage?: (message: WebSocketMessage) => void;
  autoReconnect?: boolean;
  reconnectInterval?: number;
  maxReconnectAttempts?: number;
}

interface UseWebSocketReturn {
  isConnected: boolean;
  isReconnecting: boolean;
  error: Event | null;
  reconnectAttempts: number;
  send: (message: WebSocketMessage) => void;
  connect: () => Promise<void>;
  disconnect: () => void;
}

export function useWebSocket(options: UseWebSocketOptions = {}): UseWebSocketReturn {
  const {
    onOpen,
    onClose,
    onError,
    onMessage,
    autoReconnect = true,
    reconnectInterval = 3000,
    maxReconnectAttempts = 5,
  } = options;

  const { client, isConnected: contextIsConnected, connect: contextConnect, disconnect: contextDisconnect } = useCUIContext();
  const [isReconnecting, setIsReconnecting] = useState(false);
  const [error, setError] = useState<Event | null>(null);
  const [reconnectAttempts, setReconnectAttempts] = useState(0);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  const connect = useCallback(async () => {
    setIsReconnecting(true);
    try {
      await contextConnect();
      setReconnectAttempts(0);
      setIsReconnecting(false);
      onOpen?.();
    } catch (err) {
      setIsReconnecting(false);
      const event = new Event('error') as Event;
      setError(event);
      onError?.(event);
    }
  }, [contextConnect, onOpen, onError]);

  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }
    contextDisconnect();
    setIsReconnecting(false);
    setReconnectAttempts(0);
    onClose?.();
  }, [contextDisconnect, onClose]);

  const send = useCallback(
    (message: WebSocketMessage) => {
      if (!client) {
        console.error('Client not initialized');
        return;
      }
      client.send(message);
    },
    [client]
  );

  // Handle auto-reconnect
  useEffect(() => {
    if (!contextIsConnected && autoReconnect && reconnectAttempts < maxReconnectAttempts) {
      reconnectTimeoutRef.current = setTimeout(() => {
        setReconnectAttempts((prev) => prev + 1);
        connect();
      }, reconnectInterval);
    }

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [contextIsConnected, autoReconnect, reconnectAttempts, maxReconnectAttempts, reconnectInterval, connect]);

  // Subscribe to messages
  useEffect(() => {
    if (!client || !contextIsConnected) return;

    const unsubscribe = client.on('*', (msg: WebSocketMessage) => {
      onMessage?.(msg);
    });

    return unsubscribe;
  }, [client, contextIsConnected, onMessage]);

  return {
    isConnected: contextIsConnected,
    isReconnecting,
    error,
    reconnectAttempts,
    send,
    connect,
    disconnect,
  };
}