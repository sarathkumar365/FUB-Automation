export type ToastVariant = 'success' | 'error' | 'warning' | 'info'

export type ToastOptions = {
  title?: string
  durationMs?: number
}

export type ToastItem = {
  id: string
  variant: ToastVariant
  message: string
  title?: string
  durationMs: number
}

export type NotifyApi = {
  success: (message: string, options?: ToastOptions) => void
  error: (message: string, options?: ToastOptions) => void
  warning: (message: string, options?: ToastOptions) => void
  info: (message: string, options?: ToastOptions) => void
}
