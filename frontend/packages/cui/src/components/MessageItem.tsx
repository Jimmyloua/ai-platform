import React from 'react';
import { Message, ToolCall } from '../types';
import { MarkdownRenderer } from './MarkdownRenderer';
import { ToolExecution } from './ToolExecution';
import { clsx } from 'clsx';

export interface MessageItemProps {
  message: Message;
  isStreaming?: boolean;
  className?: string;
}

export function MessageItem({
  message,
  isStreaming = false,
  className,
}: MessageItemProps) {
  const isUser = message.role === 'user';
  const isAssistant = message.role === 'assistant';
  const isSystem = message.role === 'system';
  const isTool = message.role === 'tool';

  return (
    <div
      className={clsx(
        'flex gap-3',
        isUser && 'justify-end',
        className
      )}
    >
      {/* Avatar for non-user messages */}
      {!isUser && (
        <div
          className={clsx(
            'flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-medium',
            isAssistant && 'bg-blue-500',
            isSystem && 'bg-gray-500',
            isTool && 'bg-green-500'
          )}
        >
          {isAssistant && 'AI'}
          {isSystem && 'S'}
          {isTool && 'T'}
        </div>
      )}

      <div
        className={clsx(
          'flex-1 max-w-[80%]',
          isUser && 'flex justify-end'
        )}
      >
        {/* Message content */}
        <div
          className={clsx(
            'rounded-lg px-4 py-2',
            isUser && 'bg-blue-500 text-white',
            isAssistant && 'bg-gray-100 text-gray-900',
            isSystem && 'bg-yellow-100 text-yellow-900',
            isTool && 'bg-gray-50 text-gray-700 border border-gray-200'
          )}
        >
          {/* Name/Role */}
          {message.name && (
            <div className="text-xs text-gray-500 mb-1">{message.name}</div>
          )}

          {/* Content */}
          {message.content && (
            <div className={isUser ? 'text-white' : ''}>
              <MarkdownRenderer content={message.content} />
            </div>
          )}

          {/* Streaming cursor */}
          {isStreaming && (
            <span className="inline-block w-2 h-4 bg-blue-500 animate-pulse ml-0.5" />
          )}
        </div>

        {/* Tool calls */}
        {message.toolCalls && message.toolCalls.length > 0 && (
          <div className="mt-2 space-y-2">
            {message.toolCalls.map((toolCall) => (
              <ToolExecution
                key={toolCall.id}
                toolCall={toolCall}
                status="pending"
              />
            ))}
          </div>
        )}

        {/* Timestamp */}
        <div
          className={clsx(
            'text-xs text-gray-400 mt-1',
            isUser && 'text-right'
          )}
        >
          {formatTime(message.createdAt)}
        </div>
      </div>

      {/* Avatar for user messages */}
      {isUser && (
        <div className="flex-shrink-0 w-8 h-8 rounded-full bg-green-500 flex items-center justify-center text-white text-sm font-medium">
          U
        </div>
      )}
    </div>
  );
}

function formatTime(date: Date): string {
  return new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: 'numeric',
  }).format(date);
}