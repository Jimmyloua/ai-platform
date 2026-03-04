import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { AIClient, ClientConfig } from '@aiplatform/cui-sdk';
import { CUIContext, CUIProviderProps } from './context';

export function CUIProvider({ config, children, autoConnect = true }: CUIProviderProps) {
  const [isConnected, setIsConnected] = useState(false);
  const client = useMemo(() => new AIClient(config), [config]);

  const connect = useCallback(async () => {
    await client.connect();
    setIsConnected(true);
  }, [client]);

  const disconnect = useCallback(() => {
    client.disconnect();
    setIsConnected(false);
  }, [client]);

  useEffect(() => {
    if (autoConnect) {
      connect().catch(console.error);
    }

    return () => {
      disconnect();
    };
  }, [autoConnect, connect, disconnect]);

  const value = useMemo(
    () => ({
      client,
      isConnected,
      connect,
      disconnect,
    }),
    [client, isConnected, connect, disconnect]
  );

  return <CUIContext.Provider value={value}>{children}</CUIContext.Provider>;
}