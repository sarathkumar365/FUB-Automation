import { useCallback, useState } from 'react'

/**
 * Light/dark theme handling. The color values live in `tokens.css` under
 * `:root[data-theme="dark"]`; this module only manages which theme is active
 * (the `data-theme` attribute on <html>) and persists the choice.
 *
 * Initial-theme resolution (stored choice → OS preference) is owned by the
 * inline boot script in `index.html` so it runs before first paint (no flash);
 * it cannot import this module. This module reads the already-applied value and
 * lets the UI toggle it — keep the storage key in sync with that script.
 */
export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'ae-theme'

export function applyTheme(theme: Theme): void {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = theme
  }
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    /* persistence is best-effort (private mode etc.) */
  }
}

/** Read the theme currently applied to the DOM (set by the inline boot script). */
function currentTheme(): Theme {
  return typeof document !== 'undefined' && document.documentElement.dataset.theme === 'dark'
    ? 'dark'
    : 'light'
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
