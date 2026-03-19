import { uiText } from '../constants/uiText'

type LoadingStateProps = {
  title?: string
  message?: string
}

export function LoadingState({ title = uiText.states.loadingTitle, message = uiText.states.loadingMessage }: LoadingStateProps) {
  return (
    <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <p className="text-sm font-semibold text-[var(--color-text)]">{title}</p>
      <p className="mt-1 text-sm text-[var(--color-text-muted)]">{message}</p>
    </div>
  )
}
