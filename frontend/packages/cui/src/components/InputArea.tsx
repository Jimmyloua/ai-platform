import React, { useState, useRef, KeyboardEvent } from 'react';
import { clsx } from 'clsx';

export interface InputAreaProps {
  onSend: (message: string) => void;
  onPaste?: (event: React.ClipboardEvent) => void;
  disabled?: boolean;
  placeholder?: string;
  maxLength?: number;
  className?: string;
  showSendButton?: boolean;
  sendOnEnter?: boolean;
}

export function InputArea({
  onSend,
  onPaste,
  disabled = false,
  placeholder = 'Type a message...',
  maxLength = 4000,
  className,
  showSendButton = true,
  sendOnEnter = true,
}: InputAreaProps) {
  const [message, setMessage] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = () => {
    const trimmed = message.trim();
    if (trimmed && !disabled) {
      onSend(trimmed);
      setMessage('');
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
      }
    }
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && sendOnEnter) {
      event.preventDefault();
      handleSend();
    }
  };

  const handleChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    const value = event.target.value;
    if (value.length <= maxLength) {
      setMessage(value);
    }

    // Auto-resize
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${textareaRef.current.scrollHeight}px`;
    }
  };

  return (
    <div
      className={clsx(
        'flex items-end gap-2 p-3 border rounded-lg bg-white transition-colors',
        isFocused && 'border-blue-500 ring-2 ring-blue-100',
        !isFocused && 'border-gray-200',
        disabled && 'opacity-50 cursor-not-allowed',
        className
      )}
    >
      {/* Text input */}
      <textarea
        ref={textareaRef}
        value={message}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        onFocus={() => setIsFocused(true)}
        onBlur={() => setIsFocused(false)}
        onPaste={onPaste}
        placeholder={placeholder}
        disabled={disabled}
        rows={1}
        className={clsx(
          'flex-1 resize-none border-0 outline-none text-gray-900 placeholder-gray-400',
          'max-h-40 overflow-y-auto',
          disabled && 'cursor-not-allowed'
        )}
        style={{ minHeight: '24px' }}
      />

      {/* Character count */}
      {message.length > maxLength * 0.8 && (
        <span className="text-xs text-gray-400">
          {message.length}/{maxLength}
        </span>
      )}

      {/* Send button */}
      {showSendButton && (
        <button
          onClick={handleSend}
          disabled={disabled || !message.trim()}
          className={clsx(
            'flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center transition-colors',
            message.trim() && !disabled
              ? 'bg-blue-500 text-white hover:bg-blue-600'
              : 'bg-gray-100 text-gray-400 cursor-not-allowed'
          )}
        >
          <svg
            className="w-5 h-5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
            />
          </svg>
        </button>
      )}
    </div>
  );
}