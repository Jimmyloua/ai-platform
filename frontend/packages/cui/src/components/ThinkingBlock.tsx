import React from 'react';
import { clsx } from 'clsx';

export interface ThinkingBlockProps {
  status?: 'thinking' | 'complete' | 'error';
  duration?: number;
  content?: string;
  className?: string;
}

export function ThinkingBlock({
  status = 'thinking',
  duration,
  content,
  className,
}: ThinkingBlockProps) {
  return (
    <div
      className={clsx(
        'rounded-lg border px-4 py-3',
        status === 'thinking' && 'bg-blue-50 border-blue-200',
        status === 'complete' && 'bg-green-50 border-green-200',
        status === 'error' && 'bg-red-50 border-red-200',
        className
      )}
    >
      <div className="flex items-center gap-2">
        {/* Thinking animation */}
        {status === 'thinking' && (
          <div className="flex gap-1">
            <span className="w-2 h-2 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
            <span className="w-2 h-2 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
            <span className="w-2 h-2 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
          </div>
        )}

        {/* Complete checkmark */}
        {status === 'complete' && (
          <svg className="w-5 h-5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        )}

        {/* Error icon */}
        {status === 'error' && (
          <svg className="w-5 h-5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        )}

        {/* Status text */}
        <span
          className={clsx(
            'text-sm font-medium',
            status === 'thinking' && 'text-blue-700',
            status === 'complete' && 'text-green-700',
            status === 'error' && 'text-red-700'
          )}
        >
          {status === 'thinking' && 'Thinking...'}
          {status === 'complete' && 'Complete'}
          {status === 'error' && 'Error'}
        </span>

        {/* Duration */}
        {duration && (
          <span className="text-xs text-gray-500">{duration}ms</span>
        )}
      </div>

      {/* Content */}
      {content && (
        <div className="mt-2 text-sm text-gray-600">{content}</div>
      )}
    </div>
  );
}