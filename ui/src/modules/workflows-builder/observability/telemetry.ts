/**
 * Thin telemetry wrapper for the workflow builder.
 *
 * Phase 0 stub: logs to console in dev, no-ops in prod. The shape mirrors the
 * OpenTelemetry `startActiveSpan` contract so we can swap in a real provider
 * later without touching call sites. Keeping the API stable now is the whole
 * point of this file existing as a stub.
 *
 * Every span should carry the builder's `correlationId` as an attribute so
 * spans + actionLog + server logs all filter together.
 */

export interface SpanContext {
  setAttribute(key: string, value: string | number | boolean): void
  end(status?: 'ok' | 'error', errorMessage?: string): void
}

export interface TelemetryProvider {
  startSpan(name: string, attributes?: Record<string, string | number | boolean>): SpanContext
}

class ConsoleSpan implements SpanContext {
  private readonly name: string
  private readonly started: number
  private readonly attributes: Record<string, string | number | boolean> = {}
  private ended = false

  constructor(
    name: string,
    started: number,
    initialAttributes?: Record<string, string | number | boolean>,
  ) {
    this.name = name
    this.started = started
    if (initialAttributes) {
      Object.assign(this.attributes, initialAttributes)
    }
  }

  setAttribute(key: string, value: string | number | boolean): void {
    this.attributes[key] = value
  }

  end(status: 'ok' | 'error' = 'ok', errorMessage?: string): void {
    if (this.ended) return
    this.ended = true
    if (!import.meta.env.DEV) return
    const durationMs = Math.round(performance.now() - this.started)
    console.debug('[builder.span]', this.name, {
      status,
      durationMs,
      errorMessage,
      ...this.attributes,
    })
  }
}

class ConsoleTelemetryProvider implements TelemetryProvider {
  startSpan(name: string, attributes?: Record<string, string | number | boolean>): SpanContext {
    return new ConsoleSpan(name, performance.now(), attributes)
  }
}

let provider: TelemetryProvider = new ConsoleTelemetryProvider()

export function setTelemetryProvider(next: TelemetryProvider): void {
  provider = next
}

export function startSpan(
  name: string,
  attributes?: Record<string, string | number | boolean>,
): SpanContext {
  return provider.startSpan(name, attributes)
}
