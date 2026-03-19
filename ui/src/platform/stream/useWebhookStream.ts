import { useEffect, useMemo, useState } from 'react'
import { z } from 'zod'
import { useAppPorts } from '../../app/useAppPorts'
import type { WebhookEventStatus, WebhookSource, WebhookStreamEvent, WebhookStreamFilters } from '../../shared/types/webhook'

export type StreamConnectionState = 'connecting' | 'open' | 'error'

type StreamHookResult = {
  events: WebhookStreamEvent[]
  state: StreamConnectionState
  lastHeartbeatAt: string | null
}

type StreamEventState = {
  key: string
  state: StreamConnectionState
}

type HeartbeatState = {
  key: string
  at: string
}

type StreamCacheState = {
  key: string
  events: WebhookStreamEvent[]
  seenIds: Set<string>
}

const MAX_STREAM_EVENTS = 100

const webhookStreamPayloadSchema = z.object({
  id: z.union([z.string(), z.number()]),
  receivedAt: z.string(),
  source: z.custom<WebhookSource>((value) => value === 'FUB'),
  eventType: z.string(),
  status: z.custom<WebhookEventStatus>((value) => value === 'RECEIVED'),
})

function toFiltersKey(filters: WebhookStreamFilters) {
  return [filters.source ?? '', filters.status ?? '', filters.eventType ?? ''].join('|')
}

function normalizeWebhookStreamEvent(payload: unknown): WebhookStreamEvent | null {
  const parsed = webhookStreamPayloadSchema.safeParse(payload)

  if (!parsed.success) {
    return null
  }

  const value = parsed.data
  return {
    id: String(value.id),
    receivedAt: value.receivedAt,
    source: value.source,
    eventType: value.eventType,
    status: value.status,
    payload,
  }
}

export function useWebhookStream(filters: WebhookStreamFilters = {}): StreamHookResult {
  const { webhookStreamPort } = useAppPorts()
  const source = filters.source
  const status = filters.status
  const eventType = filters.eventType
  const normalizedFilters = useMemo<WebhookStreamFilters>(
    () => ({
      source,
      status,
      eventType,
    }),
    [eventType, source, status],
  )

  const filtersKey = toFiltersKey(normalizedFilters)
  const [streamEventState, setStreamEventState] = useState<StreamEventState | null>(null)
  const [streamCacheState, setStreamCacheState] = useState<StreamCacheState | null>(null)
  const [heartbeatState, setHeartbeatState] = useState<HeartbeatState | null>(null)

  useEffect(() => {
    const close = webhookStreamPort.openWebhookStream(normalizedFilters, {
      onEvent: (eventName, payload) => {
        setStreamEventState({
          key: filtersKey,
          state: 'open',
        })

        if (eventName === 'webhook.received') {
          const normalized = normalizeWebhookStreamEvent(payload)
          if (!normalized) {
            return
          }

          setStreamCacheState((existing) => {
            if (!existing || existing.key !== filtersKey) {
              return {
                key: filtersKey,
                events: [normalized],
                seenIds: new Set([normalized.id]),
              }
            }

            if (existing.seenIds.has(normalized.id)) {
              return existing
            }

            const nextEvents = [normalized, ...existing.events].slice(0, MAX_STREAM_EVENTS)
            const nextSeenIds = new Set(nextEvents.map((event) => event.id))
            return {
              key: filtersKey,
              events: nextEvents,
              seenIds: nextSeenIds,
            }
          })
          return
        }

        if (eventName === 'heartbeat') {
          setHeartbeatState({
            key: filtersKey,
            at: new Date().toISOString(),
          })
        }
      },
      onError: () => {
        setStreamEventState({
          key: filtersKey,
          state: 'error',
        })
      },
    })

    return () => {
      close()
    }
  }, [filtersKey, normalizedFilters, webhookStreamPort])

  const state = streamEventState && streamEventState.key === filtersKey ? streamEventState.state : 'connecting'
  const events = streamCacheState && streamCacheState.key === filtersKey ? streamCacheState.events : []
  const lastHeartbeatAt = heartbeatState && heartbeatState.key === filtersKey ? heartbeatState.at : null

  return { events, state, lastHeartbeatAt }
}
