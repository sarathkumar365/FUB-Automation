import { uiText } from '../constants/uiText'
import { Button } from './button'

type ErrorStateProps = {
  title?: string
  message?: string
  onRetry?: () => void
}

export function ErrorState({
  title = uiText.states.errorTitle,
  message = uiText.states.errorMessage,
  onRetry,
}: ErrorStateProps) {
  return (
    <div className="rounded-lg border border-[var(--color-status-bad-bg)] bg-[var(--color-status-bad-bg)]/30 p-4">
      <p className="text-sm font-semibold text-[var(--color-status-bad)]">{title}</p>
      <p className="mt-1 text-sm text-[var(--color-text)]">{message}</p>
      {onRetry ? (
        <Button className="mt-3" variant="outline" onClick={onRetry}>
          {uiText.states.retry}
        </Button>
      ) : null}
    </div>
  )
}
