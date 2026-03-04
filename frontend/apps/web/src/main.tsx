import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { CUIProvider } from '@aiplatform/cui-react';
import App from './App';
import './index.css';

const config = {
  baseUrl: import.meta.env.VITE_API_URL || '/api',
  wsUrl: import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws',
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <CUIProvider config={config}>
        <App />
      </CUIProvider>
    </BrowserRouter>
  </React.StrictMode>
);