import React from 'react';
import { MessageList } from './MessageList';
import { InputArea } from './InputArea';
import { SkillProgress } from './SkillProgress';
import { useChat } from '../hooks/useChat';
import { clsx } from 'clsx';

export interface ChatContainerProps {
  onSendMessage?: (message: string) => void;
  className?: string;
  placeholder?: string;
  showProgress?: boolean;
}

export function ChatContainer({
  onSendMessage,
  className,
  placeholder = 'Type a message...',
  showProgress = true,
}: ChatContainerProps) {
  const {
    messages,
    isLoading,
    isStreaming,
    streamingContent,
    progress,
    sendMessage,
    clearMessages,
    executeSkill,
    pauseSkill,
    resumeSkill,
    cancelSkill,
  } = useChat();

  const handleSend = async (message: string) => {
    if (onSendMessage) {
      onSendMessage(message);
    } else {
      await sendMessage(message);
    }
  };

  return (
    <div className={clsx('flex flex-col h-full bg-white', className)}>
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b">
        <h2 className="text-lg font-semibold text-gray-900">Chat</h2>
        <button
          onClick={clearMessages}
          className="text-sm text-gray-500 hover:text-gray-700"
        >
          Clear
        </button>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-hidden">
        <MessageList
          messages={messages}
          isLoading={isLoading}
          isStreaming={isStreaming}
          streamingContent={streamingContent}
        />
      </div>

      {/* Skill progress */}
      {showProgress && progress && (
        <SkillProgress
          progress={progress.progress}
          step={progress.step}
          message={progress.message}
          onPause={pauseSkill}
          onResume={resumeSkill}
          onCancel={cancelSkill}
          isPaused={false}
        />
      )}

      {/* Input */}
      <div className="p-4 border-t">
        <InputArea
          onSend={handleSend}
          disabled={isLoading && !isStreaming}
          placeholder={placeholder}
        />
      </div>
    </div>
  );
}