/**
 * Scene card formatters — one per step type.
 *
 * Each formatter takes a node's `config` and produces:
 *   - `title`: human-friendly verb phrase (e.g. "Send Slack message")
 *   - `summary`: short detail line that makes the step's intent visible WITHOUT
 *     forcing the user to open the inspector. This is the main antidote to the
 *     "graph hides real logic" problem — the important config (channel, delay
 *     duration, field path) shows right on the card.
 *   - `accent`: optional brand tone key so each category (side-effect / wait /
 *     branch / pure-compute) gets a consistent color family.
 *
 * If the engine gains a new step type and no formatter is registered, the
 * default fallback renders the step type id verbatim — never crashes.
 */

export type FormatterAccent = 'side-effect' | 'wait' | 'branch' | 'compute' | 'trigger' | 'neutral'

export interface FormattedScene {
  title: string
  summary: string
  accent: FormatterAccent
}

type Config = Record<string, unknown>
type Formatter = (config: Config) => FormattedScene

const DEFAULT: Formatter = (config) => ({
  title: 'Unknown step',
  summary: JSON.stringify(config).slice(0, 72),
  accent: 'neutral',
})

const formatters: Record<string, Formatter> = {
  set_variable: (config) => {
    const name = asString(config.name) ?? 'variable'
    const value = config.value
    return {
      title: `Set ${name}`,
      summary: `= ${stringifyShort(value)}`,
      accent: 'compute',
    }
  },
  branch_on_field: (config) => {
    const field = asString(config.field) ?? '?'
    const cases = Array.isArray(config.cases) ? config.cases.length : 0
    return {
      title: `Branch on ${field}`,
      summary: `${cases} case${cases === 1 ? '' : 's'}`,
      accent: 'branch',
    }
  },
  delay: (config) => {
    const duration = asString(config.duration) ?? asString(config.durationIso) ?? '?'
    return {
      title: 'Delay',
      summary: `wait ${duration}`,
      accent: 'wait',
    }
  },
  wait_and_check_claim: (config) => ({
    title: 'Wait & check claim',
    summary: `after ${asString(config.duration) ?? '?'}`,
    accent: 'wait',
  }),
  wait_and_check_communication: (config) => ({
    title: 'Wait & check communication',
    summary: `after ${asString(config.duration) ?? '?'}`,
    accent: 'wait',
  }),
  http_request: (config) => {
    const method = asString(config.method)?.toUpperCase() ?? 'GET'
    const url = asString(config.url) ?? '?'
    return {
      title: 'HTTP request',
      summary: `${method} ${truncate(url, 48)}`,
      accent: 'side-effect',
    }
  },
  slack_notify: (config) => {
    const channel = asString(config.channel) ?? '?'
    return {
      title: 'Slack notify',
      summary: `→ ${channel}`,
      accent: 'side-effect',
    }
  },
  fub_add_tag: (config) => {
    const tag = asString(config.tag) ?? '?'
    return {
      title: 'FUB add tag',
      summary: `+ ${tag}`,
      accent: 'side-effect',
    }
  },
  fub_reassign: (config) => {
    const userId = asString(config.userId) ?? asString(config.targetUserId) ?? '?'
    return {
      title: 'FUB reassign',
      summary: `→ user ${userId}`,
      accent: 'side-effect',
    }
  },
  fub_move_to_pond: (config) => {
    const pondId = asString(config.pondId) ?? '?'
    return {
      title: 'FUB move to pond',
      summary: `→ pond ${pondId}`,
      accent: 'side-effect',
    }
  },
}

export function formatScene(stepType: string, config: Config): FormattedScene {
  if (stepType === '__trigger__') {
    return {
      title: 'Trigger',
      summary: 'workflow start',
      accent: 'trigger',
    }
  }
  const formatter = formatters[stepType] ?? DEFAULT
  return formatter(config ?? {})
}

export function registeredStepTypes(): string[] {
  return Object.keys(formatters).sort()
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

function truncate(value: string, max: number): string {
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`
}

function stringifyShort(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return truncate(value, 48)
  try {
    return truncate(JSON.stringify(value), 48)
  } catch {
    return '…'
  }
}
