/* Shared dashboard data, series, and small building blocks used across the
   four directions. Numbers are coherent with the kit's RUNS / PROCESSED_CALLS
   mock data so every direction tells the same story. */

/* ---- 24h series (one point per hour) ------------------------------------ */
const THRU_24 = [96, 104, 88, 79, 70, 66, 72, 91, 118, 134, 142, 151, 148, 139, 144, 158, 166, 159, 150, 143, 137, 146, 152, 148];
const RUNS_24 = [61, 66, 58, 49, 44, 40, 47, 60, 78, 88, 92, 96, 97, 90, 93, 101, 104, 99, 94, 90, 86, 92, 96, 97];
const SUCCESS_24 = [98.1, 97.4, 99.0, 98.6, 100, 99.2, 97.8, 96.4, 95.1, 96.0, 97.2, 96.6, 97.0, 98.0, 97.5, 96.1, 95.4, 96.8, 97.7, 98.2, 97.9, 96.9, 97.3, 97.0];

/* ---- KPI headline numbers ----------------------------------------------- */
const KPI = {
  ingested: 148, events: 142, runs: 97, failedOpen: 3,
  runs24: 312, succeeded24: 289, failed24: 9, running24: 4, canceled24: 10,
  successRate: 97.0, p50ms: 412, replayedToday: 6,
};

/* ---- pipeline stages (the spine, reused) -------------------------------- */
const STAGES = [
  { key: 'ingested', label: 'Ingested', value: 148, sub: 'events / hr', spark: [30, 50, 45, 65, 55, 75, 60, 90], tone: 'brand' },
  { key: 'events', label: 'Domain Events', value: 142, sub: 'normalized', spark: [40, 55, 50, 60, 58, 70, 66, 82], tone: 'brand' },
  { key: 'runs', label: 'Workflow Runs', value: 97, sub: 'started', spark: [35, 45, 40, 55, 52, 60, 58, 72], tone: 'brand' },
  { key: 'failed', label: 'Failed', value: 3, sub: 'needs replay', spark: [90, 100, 70, 75, 55, 45, 35, 25], tone: 'bad' },
];

/* ---- outcomes (last 24h) ------------------------------------------------ */
const OUTCOMES = [
  { label: 'Succeeded', value: 289, color: 'var(--color-status-ok)' },
  { label: 'Canceled', value: 10, color: 'var(--color-accent-neutral-dot)' },
  { label: 'Failed', value: 9, color: 'var(--color-status-bad)' },
  { label: 'Running', value: 4, color: 'var(--color-brand)' },
];

/* ---- per-workflow throughput (last 24h) --------------------------------- */
const WF_PERF = [
  { label: 'lead_follow_up_v1', value: 186, suffix: '', rate: 98.4 },
  { label: 'missed_call_recover', value: 74, suffix: '', rate: 91.9 },
  { label: 'new_lead_intake', value: 38, suffix: '', rate: 100 },
  { label: 'stale_lead_nudge', value: 14, suffix: '', rate: 92.8 },
];

/* ---- the triage worklist (3 open failures) ------------------------------ */
const ATTENTION = [
  { kind: 'Run', ref: '90411', wf: 'missed_call_recover', reason: 'SIDE_EFFECT_ERROR', retries: 1, ago: '10m', nav: 'workflows' },
  { kind: 'Call', ref: '40229', wf: 'missed_call_v2', reason: 'FUB 429 rate limit', retries: 2, ago: '9m', nav: 'processed-calls' },
  { kind: 'Call', ref: '40226', wf: 'missed_call_v2', reason: 'Lead not found', retries: 1, ago: '25m', nav: 'processed-calls' },
];

/* ---- live activity feed (newest first) ---------------------------------- */
const FEED = [
  { kind: 'run', tone: 'ok', label: 'Run SUCCEEDED', detail: 'lead_follow_up_v1', ref: '90412', ago: '0:04' },
  { kind: 'webhook', tone: 'brand', label: 'Webhook callsCreated', detail: 'FUB', ref: 'evt_efd5a12', ago: '0:09' },
  { kind: 'call', tone: 'ok', label: 'Call TASK_CREATED', detail: 'missed_call_v2', ref: '40231', ago: '0:21' },
  { kind: 'run', tone: 'info', label: 'Run RUNNING', detail: 'lead_follow_up_v1', ref: '90410', ago: '0:40' },
  { kind: 'run', tone: 'bad', label: 'Run FAILED', detail: 'missed_call_recover', ref: '90411', ago: '1:02' },
  { kind: 'webhook', tone: 'brand', label: 'Webhook peopleUpdated', detail: 'FUB', ref: 'evt_efc9b03', ago: '1:18' },
  { kind: 'call', tone: 'warn', label: 'Call SKIPPED', detail: 'task_creation_disabled', ref: '40230', ago: '2:03' },
  { kind: 'call', tone: 'bad', label: 'Call FAILED', detail: 'FUB 429 rate limit', ref: '40229', ago: '2:41' },
];

const TONE_FG = { ok: 'var(--color-status-ok)', bad: 'var(--color-status-bad)', warn: 'var(--color-status-warn)', brand: 'var(--color-brand)', info: 'var(--color-brand)' };
const TONE_BG = { ok: 'var(--color-status-ok-bg)', bad: 'var(--color-status-bad-bg)', warn: 'var(--color-status-warn-bg)', brand: 'var(--color-brand-soft)', info: 'var(--color-brand-soft)' };

/* ======================================================================== */
/* Shared atoms                                                             */
/* ======================================================================== */

function LivePill({ label = 'Pipeline live' }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, borderRadius: 'var(--radius-pill)',
      border: '1px solid color-mix(in srgb, var(--color-status-ok) 30%, transparent)', background: 'var(--color-status-ok-bg)',
      color: 'var(--color-status-ok)', padding: '5px 12px', fontSize: 12, fontWeight: 600 }}>
      <span className="ae-livedot" style={{ width: 8, height: 8, borderRadius: 999, background: 'var(--color-live)' }} />{label}
    </span>
  );
}

/* Kicker / overline label */
function Kicker({ children, style }) {
  return <div style={{ fontSize: 11, fontWeight: 800, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--color-text-muted)', ...style }}>{children}</div>;
}

/* A status chip used in feeds (colored square + mono kind glyph) */
function FeedDot({ tone }) {
  return <span style={{ width: 26, height: 26, borderRadius: 7, flexShrink: 0, background: TONE_BG[tone], display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
    <span style={{ width: 8, height: 8, borderRadius: 999, background: TONE_FG[tone] }} />
  </span>;
}

/* compact attention row used by A & C */
function AttentionRow({ item, onNav, dense }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: dense ? '9px 12px' : '11px 12px',
      borderRadius: 'var(--radius-sm)', background: 'var(--color-status-bad-bg)' }}>
      <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: '.06em', textTransform: 'uppercase', color: 'var(--color-status-bad)',
        background: 'color-mix(in srgb, var(--color-status-bad) 12%, transparent)', borderRadius: 5, padding: '3px 6px', flexShrink: 0 }}>{item.kind}</span>
      <div style={{ minWidth: 0, flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
          <span className="mono" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--color-status-bad)' }}>{item.ref}</span>
          <span className="mono" style={{ fontSize: 11, color: 'var(--color-text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.reason}</span>
        </div>
        <div className="mono" style={{ fontSize: 10.5, color: 'var(--color-text-muted)', marginTop: 1 }}>{item.wf} · {item.retries} retr{item.retries === 1 ? 'y' : 'ies'} · {item.ago} ago</div>
      </div>
      <Button size="sm" variant="outline" onClick={() => onNav(item.nav)}>Replay</Button>
    </div>
  );
}

Object.assign(window, {
  THRU_24, RUNS_24, SUCCESS_24, KPI, STAGES, OUTCOMES, WF_PERF, ATTENTION, FEED, TONE_FG, TONE_BG,
  LivePill, Kicker, FeedDot, AttentionRow,
});
