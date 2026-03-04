import React, { createContext, useContext } from 'react';
import { AIClient, ClientConfig } from '@aiplatform/cui-sdk';

interface CUIContextValue {
  client: AIClient | null;
  isConnected: boolean;
  connect: () => Promise<void>;
  disconnect: () => void;
}

const CUIContext = createContext<CUIContextValue | null>(null);

export function useCUIContext(): CUIContextValue {
  const context = useContext(CUIContext);
  if (!context) {
    throw new Error('useCUIContext must be used within a CUIProvider');
  }
  return context;
}

export interface CUIProviderProps {
  config: ClientConfig;
  children: React.ReactNode;
  autoConnect?: boolean;
}

export { CUIContext };