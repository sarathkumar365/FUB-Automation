import { uiText } from '../constants/uiText'
import { useTheme } from '../theme/theme'
import { MoonIcon, SunIcon } from './icons'

/**
 * Light/dark theme toggle for the rail. Shows a moon while in light mode (click
 * → dark) and a sun while in dark mode (click → light). Colors come from the
 * `data-theme` token layer in `tokens.css`; this only flips the attribute.
 */
export function ThemeToggle({ className = '' }: { className?: string }) {
  const { theme, toggle } = useTheme()
  const isDark = theme === 'dark'
  const label = isDark ? uiText.app.shell.themeToggleToLight : uiText.app.shell.themeToggleToDark

  return (
    <button
      type="button"
      onClick={toggle}
      title={label}
      aria-label={label}
      aria-pressed={isDark}
      className={[
        'flex h-9 w-9 items-center justify-center rounded-md transition-colors',
        'bg-[var(--color-surface-alt)] text-[var(--color-text-muted)] hover:text-[var(--color-text)]',
        className,
      ].join(' ')}
    >
      {isDark ? <SunIcon className="h-[18px] w-[18px]" /> : <MoonIcon className="h-[18px] w-[18px]" />}
    </button>
  )
}
