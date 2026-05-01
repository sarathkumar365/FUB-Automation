import { useMemo, useState } from 'react'
import { Button } from './button'
import { uiText } from '../constants/uiText'

type JsonViewerProps = {
  value: unknown
  className?: string
  maxHeightClassName?: string
}

export function JsonViewer({ value, className, maxHeightClassName }: JsonViewerProps) {
  const [copied, setCopied] = useState(false)

  const content = useMemo(() => {
    if (value === null || value === undefined) {
      return ''
    }

    if (typeof value === 'string') {
      return value
    }

    try {
      return JSON.stringify(value, null, 2)
    } catch {
      return String(value)
    }
  }, [value])

  const handleCopy = async () => {
    if (!content || !navigator?.clipboard?.writeText) {
      return
    }

    await navigator.clipboard.writeText(content)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className={`rounded-md border border-[var(--color-border)] bg-[var(--color-surface-alt)] ${className ?? ''}`}>
      <div className="flex items-center justify-end border-b border-[var(--color-border)] px-2 py-1.5">
        <Button type="button" size="sm" variant="ghost" onClick={handleCopy} disabled={!content}>
          {copied ? uiText.common.jsonCopied : uiText.common.jsonCopy}
        </Button>
      </div>
      <pre
        className={`overflow-auto p-3 text-xs text-[var(--color-text)] ${maxHeightClassName ?? 'max-h-72'}`}
      >
        {content || '{}'}
      </pre>
    </div>
  )
}

export type { JsonViewerProps }
