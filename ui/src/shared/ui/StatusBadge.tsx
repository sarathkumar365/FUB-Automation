import { Badge } from './badge'

export type StatusTone = 'success' | 'warning' | 'error' | 'info'

type StatusBadgeProps = {
  label: string
  tone: StatusTone
}

const toneToVariant = {
  success: 'success',
  warning: 'warning',
  error: 'error',
  info: 'default',
} as const

export function StatusBadge({ label, tone }: StatusBadgeProps) {
  return <Badge variant={toneToVariant[tone]}>{label}</Badge>
}
