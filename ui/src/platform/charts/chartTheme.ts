import { useEffect, useState } from 'react'

/**
 * Chart colors come from the design tokens, resolved at runtime (RD-015).
 * Option builders take a resolved token map as input and stay pure — no
 * component reads a CSS variable for a chart color anywhere else.
 */

function resolveTokens<const T extends readonly string[]>(names: T): Record<T[number], string> {
  const style =
    typeof window !== 'undefined' ? getComputedStyle(document.documentElement) : null
  const out = {} as Record<T[number], string>
  for (const name of names) {
    out[name as T[number]] = style?.getPropertyValue(name).trim() ?? ''
  }
  return out
}

/**
 * Resolve CSS custom properties (e.g. `--color-brand`) to concrete values and
 * re-resolve whenever the light/dark theme flips (the `data-theme` attribute
 * that `shared/theme/theme.ts` manages).
 */
export function useChartTheme<const T extends readonly string[]>(
  names: T,
): Record<T[number], string> {
  const [tokens, setTokens] = useState(() => resolveTokens(names))

  useEffect(() => {
    const observer = new MutationObserver(() => setTokens(resolveTokens(names)))
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => observer.disconnect()
    // names is a tuple literal at every call site; re-subscribing per render would churn.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return tokens
}
