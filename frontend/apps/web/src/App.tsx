import React from 'react';
import { Routes, Route, NavLink } from 'react-router-dom';
import { ChatContainer } from '@aiplatform/cui';
import { useChat } from '@aiplatform/cui-react';

function HomePage() {
  const { messages, isLoading, sendMessage } = useChat();

  return (
    <div className="h-screen flex flex-col">
      <header className="bg-blue-600 text-white p-4 shadow-md">
        <h1 className="text-2xl font-bold">AI Platform</h1>
      </header>
      <main className="flex-1 overflow-hidden">
        <ChatContainer
          onSendMessage={sendMessage}
          className="h-full"
        />
      </main>
    </div>
  );
}

function SkillsPage() {
  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold mb-4">Skills</h1>
      <p className="text-gray-600">Browse and manage available AI skills.</p>
    </div>
  );
}

function SettingsPage() {
  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold mb-4">Settings</h1>
      <p className="text-gray-600">Configure your AI Platform settings.</p>
    </div>
  );
}

function App() {
  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4">
          <div className="flex space-x-8 h-12">
            <NavLink
              to="/"
              className={({ isActive }) =>
                `inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium ${
                  isActive
                    ? 'border-blue-500 text-gray-900'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`
              }
            >
              Chat
            </NavLink>
            <NavLink
              to="/skills"
              className={({ isActive }) =>
                `inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium ${
                  isActive
                    ? 'border-blue-500 text-gray-900'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`
              }
            >
              Skills
            </NavLink>
            <NavLink
              to="/settings"
              className={({ isActive }) =>
                `inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium ${
                  isActive
                    ? 'border-blue-500 text-gray-900'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`
              }
            >
              Settings
            </NavLink>
          </div>
        </div>
      </nav>

      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/skills" element={<SkillsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Routes>
    </div>
  );
}

export default App;