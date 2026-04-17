/**
 * Ring buffer for dispatched builder actions.
 *
 * The debug overlay (Cmd+Shift+D) reads this to show a "Before / After" trace
 * for every mutation the builder makes. We keep the last 200 entries to bound
 * memory while still giving enough window to reproduce any user-reported bug.
 *
 * Each entry captures:
 *  - a monotonically increasing seq (stable across renders, easy to reference)
 *  - wall-clock timestamp
 *  - the action name + payload snapshot
 *  - content hashes of the graph before/after (see contentHash.ts)
 *  - optional correlationId propagated through telemetry.ts
 */

export interface ActionLogEntry {
  seq: number
  timestamp: number
  action: string
  payload: unknown
  hashBefore: string
  hashAfter: string
  correlationId?: string
  /** Ms between dispatch start and commit. Useful for spotting slow reducers. */
  durationMs: number
}

const MAX_ENTRIES = 200

let seqCounter = 0
const buffer: ActionLogEntry[] = []
const listeners = new Set<() => void>()

export function pushActionLogEntry(
  entry: Omit<ActionLogEntry, 'seq' | 'timestamp'>,
): ActionLogEntry {
  seqCounter += 1
  const full: ActionLogEntry = {
    seq: seqCounter,
    timestamp: Date.now(),
    ...entry,
  }
  buffer.push(full)
  if (buffer.length > MAX_ENTRIES) {
    buffer.splice(0, buffer.length - MAX_ENTRIES)
  }
  for (const listener of listeners) {
    listener()
  }
  return full
}

/** Returns a snapshot copy of the ring buffer, newest-last. */
export function getActionLog(): readonly ActionLogEntry[] {
  return buffer.slice()
}

/** Subscribe to buffer changes. Returns an unsubscribe function. */
export function subscribeToActionLog(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Test/debug only — wipes the buffer so Vitest runs start clean. */
export function resetActionLog(): void {
  seqCounter = 0
  buffer.length = 0
  for (const listener of listeners) {
    listener()
  }
}
