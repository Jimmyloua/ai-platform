import React from 'react';
import { clsx } from 'clsx';

export interface SkillProgressProps {
  progress: number;
  step: string;
  message?: string;
  isPaused?: boolean;
  onPause?: () => void;
  onResume?: () => void;
  onCancel?: () => void;
  className?: string;
}

export function SkillProgress({
  progress,
  step,
  message,
  isPaused = false,
  onPause,
  onResume,
  onCancel,
  className,
}: SkillProgressProps) {
  return (
    <div className={clsx('px-4 py-3 bg-gray-50 border-t', className)}>
      {/* Progress bar */}
      <div className="relative w-full h-2 bg-gray-200 rounded-full overflow-hidden mb-2">
        <div
          className="absolute inset-y-0 left-0 bg-blue-500 transition-all duration-300"
          style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
        />
      </div>

      {/* Info row */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-700">{step}</span>
          {message && (
            <span className="text-sm text-gray-500">- {message}</span>
          )}
          {isPaused && (
            <span className="text-xs px-2 py-0.5 bg-yellow-100 text-yellow-800 rounded">
              Paused
            </span>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-500">{progress}%</span>

          {onPause && !isPaused && (
            <button
              onClick={onPause}
              className="p-1 text-gray-400 hover:text-gray-600"
              title="Pause"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 9v6m4-6v6" />
              </svg>
            </button>
          )}

          {onResume && isPaused && (
            <button
              onClick={onResume}
              className="p-1 text-gray-400 hover:text-gray-600"
              title="Resume"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </button>
          )}

          {onCancel && (
            <button
              onClick={onCancel}
              className="p-1 text-gray-400 hover:text-red-600"
              title="Cancel"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>
      </div>
    </div>
  );
}