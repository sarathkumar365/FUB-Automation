import { useState } from 'react'
import { uiText } from '../../shared/constants/uiText'
import { ChevronDownIcon } from '../../shared/ui'

function formatError(error: unknown): string {
  if (error instanceof Error) return [error.message, error.stack].filter(Boolean).join('\n')
  if (error == null) return uiText.appError.details.empty
  return String(error)
}

/**
 * Dev-only collapsible error stack. Render only when `import.meta.env.DEV` —
 * never expose a stack trace in production.
 */
export function ErrorDetails({ error }: { error?: unknown }) {
  const [open, setOpen] = useState(false)
  const t = uiText.appError.details

  return (
    <div className="mt-6 w-full max-w-[440px] text-left">
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        className="inline-flex items-center gap-2 text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-text)]"
      >
        <ChevronDownIcon className={`h-[15px] w-[15px] transition-transform ${open ? '' : '-rotate-90'}`} />
        <span className="text-[11px] font-extrabold uppercase tracking-[0.1em]">{t.label}</span>
        <span className="rounded border border-[var(--color-border)] bg-[var(--color-surface-alt)] px-1.5 py-0.5 font-mono text-[10.5px]">
          {t.devOnly}
        </span>
      </button>
      {open ? (
        <pre className="mt-3 max-h-[168px] overflow-auto whitespace-pre-wrap rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface-alt)] px-[15px] py-[13px] font-mono text-[11.5px] leading-[1.65] text-[var(--color-text-muted)]">
          {formatError(error)}
        </pre>
      ) : null}
    </div>
  )
}
