import { useCallback, useState } from 'react'

/**
 * Light/dark theme handling. The actual color values live in `tokens.css`
 * under `:root[data-theme="dark"]`; this module only manages which theme is
 * active (the `data-theme` attribute on <html>) and persists the choice.
 *
 * The initial theme is applied by a tiny inline boot script in `index.html`
 * (before React mounts) to avoid a flash of the wrong theme. This module reads
 * that already-applied value and lets the UI toggle it.
 */
export type Theme = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'ae-theme'

export function readStoredTheme(): Theme | null {
  try {
    const value = localStorage.getItem(THEME_STORAGE_KEY)
    return value === 'dark' || value === 'light' ? value : null
  } catch {
    return null
  }
}

/** Resolve the theme to use when none is applied yet: stored choice, else OS preference. */
export function resolveInitialTheme(): Theme {
  const stored = readStoredTheme()
  if (stored) {
    return stored
  }
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
    return 'dark'
  }
  return 'light'
}

/** Apply a theme to the document and persist the choice. */
export function applyTheme(theme: Theme): void {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = theme
  }
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    /* persistence is best-effort (private mode etc.) */
  }
}

/** Read the theme currently applied to the DOM (set by the inline boot script). */
function currentTheme(): Theme {
  if (typeof document !== 'undefined' && document.documentElement.dataset.theme === 'dark') {
    return 'dark'
  }
  return 'light'
}

export function useTheme(): { theme: Theme; toggle: () => void } {
  const [theme, setTheme] = useState<Theme>(() => currentTheme())

  const toggle = useCallback(() => {
    setTheme((prev) => {
      const next: Theme = prev === 'dark' ? 'light' : 'dark'
      applyTheme(next)
      return next
    })
  }, [])

  return { theme, toggle }
}
