import React, { useState } from 'react';
import { ToolCall } from '../types';
import { clsx } from 'clsx';

export interface ToolExecutionProps {
  toolCall: ToolCall;
  result?: unknown;
  status: 'pending' | 'running' | 'complete' | 'error';
  error?: string;
  onApprove?: () => void;
  onReject?: () => void;
  requiresApproval?: boolean;
  className?: string;
}

export function ToolExecution({
  toolCall,
  result,
  status,
  error,
  onApprove,
  onReject,
  requiresApproval = false,
  className,
}: ToolExecutionProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const parsedArgs = (() => {
    try {
      return JSON.parse(toolCall.function.arguments);
    } catch {
      return toolCall.function.arguments;
    }
  })();

  return (
    <div
      className={clsx(
        'rounded-lg border overflow-hidden',
        status === 'pending' && 'bg-gray-50 border-gray-200',
        status === 'running' && 'bg-blue-50 border-blue-200',
        status === 'complete' && 'bg-green-50 border-green-200',
        status === 'error' && 'bg-red-50 border-red-200',
        className
      )}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-4 py-2 cursor-pointer hover:bg-opacity-80"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div className="flex items-center gap-2">
          {/* Status icon */}
          {status === 'pending' && (
            <span className="w-2 h-2 bg-gray-400 rounded-full" />
          )}
          {status === 'running' && (
            <span className="w-2 h-2 bg-blue-500 rounded-full animate-pulse" />
          )}
          {status === 'complete' && (
            <svg className="w-4 h-4 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          )}
          {status === 'error' && (
            <svg className="w-4 h-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          )}

          {/* Tool name */}
          <span className="font-medium text-sm">{toolCall.function.name}</span>
        </div>

        {/* Expand/collapse */}
        <svg
          className={clsx('w-4 h-4 text-gray-400 transition-transform', isExpanded && 'rotate-180')}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </div>

      {/* Expanded content */}
      {isExpanded && (
        <div className="border-t border-gray-200 px-4 py-2 space-y-2">
          {/* Arguments */}
          <div>
            <div className="text-xs font-medium text-gray-500 mb-1">Arguments:</div>
            <pre className="text-xs bg-white rounded p-2 overflow-x-auto border">
              {typeof parsedArgs === 'object'
                ? JSON.stringify(parsedArgs, null, 2)
                : parsedArgs}
            </pre>
          </div>

          {/* Result */}
          {result !== undefined && (
            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Result:</div>
              <pre className="text-xs bg-white rounded p-2 overflow-x-auto border">
                {typeof result === 'object'
                  ? JSON.stringify(result, null, 2)
                  : String(result)}
              </pre>
            </div>
          )}

          {/* Error */}
          {error && (
            <div>
              <div className="text-xs font-medium text-red-500 mb-1">Error:</div>
              <pre className="text-xs bg-red-100 text-red-800 rounded p-2 overflow-x-auto border border-red-200">
                {error}
              </pre>
            </div>
          )}

          {/* Approval buttons */}
          {requiresApproval && status === 'pending' && (
            <div className="flex gap-2 pt-2">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onApprove?.();
                }}
                className="px-3 py-1 text-sm bg-green-500 text-white rounded hover:bg-green-600 transition-colors"
              >
                Approve
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onReject?.();
                }}
                className="px-3 py-1 text-sm bg-red-500 text-white rounded hover:bg-red-600 transition-colors"
              >
                Reject
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}