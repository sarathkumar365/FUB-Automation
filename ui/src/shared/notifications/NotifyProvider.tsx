import type { PropsWithChildren } from 'react'
import { useCallback, useMemo, useState } from 'react'
import { uiText } from '../constants/uiText'
import { Button } from '../ui/button'
import { notifyContext } from './notifyContext'
import type { NotifyApi, ToastItem, ToastOptions, ToastVariant } from './types'

const DEFAULT_DURATION_MS = 4500

const variantToStyles: Record<ToastVariant, string> = {
  success: 'border-[var(--color-status-ok-bg)] bg-[var(--color-surface)]',
  error: 'border-[var(--color-status-bad-bg)] bg-[var(--color-surface)]',
  warning: 'border-[var(--color-status-warn-bg)] bg-[var(--color-surface)]',
  info: 'border-[var(--color-brand-soft)] bg-[var(--color-surface)]',
}

const variantToTitle: Record<ToastVariant, string> = {
  success: uiText.notifications.successTitle,
  error: uiText.notifications.errorTitle,
  warning: uiText.notifications.warningTitle,
  info: uiText.notifications.infoTitle,
}

export function NotifyProvider({ children }: PropsWithChildren) {
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const removeToast = useCallback((id: string) => {
    setToasts((existing) => existing.filter((toast) => toast.id !== id))
  }, [])

  const pushToast = useCallback(
    (variant: ToastVariant, message: string, options?: ToastOptions) => {
      const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`
      const durationMs = options?.durationMs ?? DEFAULT_DURATION_MS

      const toast: ToastItem = {
        id,
        variant,
        message,
        title: options?.title ?? variantToTitle[variant],
        durationMs,
      }

      setToasts((existing) => [...existing, toast])
      window.setTimeout(() => {
        removeToast(id)
      }, durationMs)
    },
    [removeToast],
  )

  const api = useMemo<NotifyApi>(
    () => ({
      success: (message, options) => pushToast('success', message, options),
      error: (message, options) => pushToast('error', message, options),
      warning: (message, options) => pushToast('warning', message, options),
      info: (message, options) => pushToast('info', message, options),
    }),
    [pushToast],
  )

  return (
    <notifyContext.Provider value={api}>
      {children}
      <div
        data-testid="notify-stack"
        className="pointer-events-none fixed right-4 top-4 z-[70] flex w-[min(92vw,360px)] flex-col gap-2"
      >
        {toasts.map((toast) => (
          <section
            key={toast.id}
            className={`pointer-events-auto rounded-md border p-3 shadow-[var(--shadow-subtle)] ${variantToStyles[toast.variant]}`}
            role="status"
            aria-live="polite"
          >
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-sm font-semibold text-[var(--color-text)]">{toast.title}</p>
                <p className="mt-1 text-sm text-[var(--color-text-muted)]">{toast.message}</p>
              </div>
              <Button
                aria-label={uiText.notifications.close}
                className="h-7 w-7"
                size="icon"
                variant="ghost"
                onClick={() => removeToast(toast.id)}
              >
                ×
              </Button>
            </div>
          </section>
        ))}
      </div>
    </notifyContext.Provider>
  )
}
