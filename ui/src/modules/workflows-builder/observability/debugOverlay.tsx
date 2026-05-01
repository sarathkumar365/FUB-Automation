/**
 * Workflow Builder debug overlay.
 *
 * Toggle: Cmd+Shift+D (Ctrl+Shift+D on Windows/Linux).
 *
 * Shows, for the current session:
 *   - Current graph content hash + revision counter
 *   - Current surface + selected node
 *   - Ring buffer of the last dispatched actions with Before → After hashes
 *     and correlation IDs
 *
 * Mount it once at the top of `WorkflowBuilderPage`. It's tree-shaken from
 * production builds because it only renders when `import.meta.env.DEV` is
 * true; set `?builderDebug=1` in the URL to force-enable it in non-dev
 * environments.
 */
import { useEffect, useSyncExternalStore, useState } from 'react'
import { getActionLog, subscribeToActionLog, type ActionLogEntry } from '../state/actionLog'
import { useBuilderStore } from '../state/builderStore'

function isOverlayForcedOn(): boolean {
  if (typeof window === 'undefined') return false
  try {
    return new URLSearchParams(window.location.search).has('builderDebug')
  } catch {
    return false
  }
}

function useActionLogSnapshot(): readonly ActionLogEntry[] {
  return useSyncExternalStore(subscribeToActionLog, getActionLog, getActionLog)
}

export function BuilderDebugOverlay() {
  const enabled = import.meta.env.DEV || isOverlayForcedOn()
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (!enabled) return
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.shiftKey && event.key.toLowerCase() === 'd') {
        event.preventDefault()
        setOpen((prev) => !prev)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [enabled])

  const log = useActionLogSnapshot()
  const graphHash = useBuilderStore((state) => state.graphHash)
  const revision = useBuilderStore((state) => state.revision)
  const surface = useBuilderStore((state) => state.layout.surface)
  const selectedNodeId = useBuilderStore((state) => state.layout.selectedNodeId)

  if (!enabled || !open) return null

  return (
    <div
      role="dialog"
      aria-label="Workflow Builder debug overlay"
      data-builder-region="debug-overlay"
      style={{
        position: 'fixed',
        right: 16,
        bottom: 16,
        width: 420,
        maxHeight: '60vh',
        zIndex: 9999,
        background: 'rgba(15, 23, 32, 0.96)',
        color: '#e7f2f4',
        border: '1px solid rgba(15, 159, 184, 0.4)',
        borderRadius: 12,
        boxShadow: '0 24px 64px rgba(0,0,0,0.45)',
        fontFamily: 'JetBrains Mono, monospace',
        fontSize: 12,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <div style={{ padding: '10px 14px', borderBottom: '1px solid rgba(255,255,255,0.08)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <strong>builder/debug</strong>
        <button
          type="button"
          onClick={() => setOpen(false)}
          style={{ background: 'transparent', color: 'inherit', border: 'none', cursor: 'pointer' }}
        >
          close
        </button>
      </div>
      <div style={{ padding: '10px 14px', display: 'grid', gap: 4, borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
        <div>hash: {graphHash}</div>
        <div>revision: {revision}</div>
        <div>surface: {surface}</div>
        <div>selected: {selectedNodeId ?? '-'}</div>
      </div>
      <div style={{ overflowY: 'auto', padding: '6px 10px' }}>
        {log.length === 0 ? (
          <div style={{ opacity: 0.6, padding: '6px 4px' }}>no actions yet</div>
        ) : (
          log
            .slice()
            .reverse()
            .map((entry) => (
              <div
                key={entry.seq}
                style={{ padding: '6px 4px', borderBottom: '1px dashed rgba(255,255,255,0.08)' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>#{entry.seq} {entry.action}</span>
                  <span style={{ opacity: 0.6 }}>{Math.round(entry.durationMs)}ms</span>
                </div>
                <div style={{ opacity: 0.75 }}>
                  {entry.hashBefore} → {entry.hashAfter}
                </div>
                {entry.correlationId ? (
                  <div style={{ opacity: 0.55 }}>{entry.correlationId}</div>
                ) : null}
              </div>
            ))
        )}
      </div>
    </div>
  )
}
