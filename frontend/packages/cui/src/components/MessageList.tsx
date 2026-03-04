import React, { useRef, useEffect } from 'react';
import { Message } from '../types';
import { MessageItem } from './MessageItem';
import { ThinkingBlock } from './ThinkingBlock';
import { clsx } from 'clsx';

export interface MessageListProps {
  messages: Message[];
  isLoading?: boolean;
  isStreaming?: boolean;
  streamingContent?: string;
  className?: string;
  autoScroll?: boolean;
}

export function MessageList({
  messages,
  isLoading = false,
  isStreaming = false,
  streamingContent = '',
  className,
  autoScroll = true,
}: MessageListProps) {
  const listRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (autoScroll && bottomRef.current) {
      bottomRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, streamingContent, autoScroll]);

  return (
    <div
      ref={listRef}
      className={clsx(
        'flex flex-col gap-4 p-4 overflow-y-auto',
        className
      )}
    >
      {messages.map((message) => (
        <MessageItem key={message.id} message={message} />
      ))}

      {/* Streaming content */}
      {isStreaming && streamingContent && (
        <MessageItem
          message={{
            id: 'streaming',
            role: 'assistant',
            content: streamingContent,
            createdAt: new Date(),
          }}
          isStreaming
        />
      )}

      {/* Loading indicator */}
      {isLoading && !isStreaming && <ThinkingBlock />}

      {/* Scroll anchor */}
      <div ref={bottomRef} />
    </div>
  );
}