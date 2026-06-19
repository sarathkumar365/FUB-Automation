/* Mock data + formatters for the Flux kit.
   Shapes mirror ui/src/shared/types/* and the *Display helpers. */

const EVENT_TYPES = ['callsCreated', 'peopleCreated', 'peopleUpdated'];
function formatEventType(t) {
  return String(t).replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^\w/, (c) => c.toUpperCase());
}
function pad(n) { return String(n).padStart(2, '0'); }
function fmtTime(d) { return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`; }
function fmtDateTime(d) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${fmtTime(d)}`; }

let _seq = 982340;
function nextEventId() { _seq += 1; return 'evt_' + _seq.toString(16) + 'a' + Math.floor(Math.random() * 90 + 10); }
function rand(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

function makeWebhook(secondsAgo) {
  const et = rand(EVENT_TYPES);
  const d = new Date(Date.now() - secondsAgo * 1000);
  return {
    id: _seq + Math.floor(Math.random() * 1000),
    eventId: nextEventId(),
    source: 'FUB',
    eventType: et,
    status: 'RECEIVED',
    receivedAt: d,
    payloadHash: Math.random().toString(16).slice(2, 6) + '…' + Math.random().toString(16).slice(2, 6),
    payload: { event: et, resourceIds: [40000 + Math.floor(Math.random() * 999)], source: 'FUB' },
  };
}
const SEED_WEBHOOKS = Array.from({ length: 14 }, (_, i) => makeWebhook(i * 23 + 5));

const PROCESSED_CALLS = [
  { callId: 40231, status: 'TASK_CREATED', ruleApplied: 'missed_call_v2', taskId: 88120, failureReason: null, retryCount: 0, updatedAt: new Date(Date.now() - 120000) },
  { callId: 40230, status: 'SKIPPED', ruleApplied: 'task_creation_disabled', taskId: null, failureReason: null, retryCount: 0, updatedAt: new Date(Date.now() - 360000) },
  { callId: 40229, status: 'FAILED', ruleApplied: 'missed_call_v2', taskId: null, failureReason: 'FUB 429 rate limit', retryCount: 2, updatedAt: new Date(Date.now() - 540000) },
  { callId: 40228, status: 'TASK_CREATED', ruleApplied: 'answered_followup', taskId: 88119, failureReason: null, retryCount: 0, updatedAt: new Date(Date.now() - 900000) },
  { callId: 40227, status: 'PROCESSING', ruleApplied: null, taskId: null, failureReason: null, retryCount: 0, updatedAt: new Date(Date.now() - 60000) },
  { callId: 40226, status: 'FAILED', ruleApplied: 'missed_call_v2', taskId: null, failureReason: 'Lead not found', retryCount: 1, updatedAt: new Date(Date.now() - 1500000) },
  { callId: 40225, status: 'RECEIVED', ruleApplied: null, taskId: null, failureReason: null, retryCount: 0, updatedAt: new Date(Date.now() - 30000) },
  { callId: 40224, status: 'TASK_CREATED', ruleApplied: 'answered_followup', taskId: 88117, failureReason: null, retryCount: 0, updatedAt: new Date(Date.now() - 2400000) },
];
const PC_STATUS_TONE = { RECEIVED: 'info', PROCESSING: 'warning', SKIPPED: 'warning', TASK_CREATED: 'success', FAILED: 'error' };
function pcStatusLabel(s) { return s.replace('_', ' '); }

const WORKFLOWS = [
  { key: 'lead_follow_up_v1', name: 'Lead follow-up', status: 'ACTIVE', version: 4, createdAt: new Date('2026-03-02'), trigger: 'callsCreated' },
  { key: 'missed_call_recover', name: 'Missed-call recovery', status: 'ACTIVE', version: 2, createdAt: new Date('2026-04-11'), trigger: 'callsCreated' },
  { key: 'new_lead_intake', name: 'New lead intake', status: 'DRAFT', version: 1, createdAt: new Date('2026-05-20'), trigger: 'peopleCreated' },
  { key: 'stale_lead_nudge', name: 'Stale-lead nudge', status: 'INACTIVE', version: 3, createdAt: new Date('2026-02-18'), trigger: 'peopleUpdated' },
];
const WF_STATUS_TONE = { ACTIVE: 'success', DRAFT: 'info', INACTIVE: 'muted', ARCHIVED: 'muted' };

const RUNS = [
  { id: 90412, workflowKey: 'lead_follow_up_v1', status: 'SUCCEEDED', reasonCode: null, startedAt: new Date(Date.now() - 300000), completedAt: new Date(Date.now() - 298000) },
  { id: 90411, workflowKey: 'missed_call_recover', status: 'FAILED', reasonCode: 'SIDE_EFFECT_ERROR', startedAt: new Date(Date.now() - 600000), completedAt: new Date(Date.now() - 599000) },
  { id: 90410, workflowKey: 'lead_follow_up_v1', status: 'RUNNING', reasonCode: null, startedAt: new Date(Date.now() - 40000), completedAt: null },
  { id: 90409, workflowKey: 'lead_follow_up_v1', status: 'SUCCEEDED', reasonCode: null, startedAt: new Date(Date.now() - 1200000), completedAt: new Date(Date.now() - 1198000) },
  { id: 90408, workflowKey: 'stale_lead_nudge', status: 'CANCELED', reasonCode: 'MANUAL_CANCEL', startedAt: new Date(Date.now() - 2000000), completedAt: new Date(Date.now() - 1999000) },
];
const RUN_STATUS_TONE = { SUCCEEDED: 'success', FAILED: 'error', RUNNING: 'info', CANCELED: 'muted', BLOCKED: 'warning' };

/* Persons (formerly Leads) — list rows mirror GET /admin/persons.
   getPersonSummary(id) mirrors GET /admin/persons/{id}/summary. */
const PERSONS = [
  { id: 7012, sourceSystem: 'FUB', sourcePersonId: '1042', status: 'Active', name: 'Marcus Bell',
    createdAt: new Date('2026-05-28'), updatedAt: new Date(Date.now() - 200000), lastSyncedAt: new Date(Date.now() - 60000),
    snapshot: { id: 1042, firstName: 'Marcus', lastName: 'Bell', stage: 'Active Client', source: 'Zillow', emails: [{ value: 'marcus.bell@example.com' }], phones: [{ value: '+1 415 555 0142' }] } },
  { id: 7011, sourceSystem: 'FUB', sourcePersonId: '1041', status: 'Nurture', name: 'Dana Whitfield',
    createdAt: new Date('2026-05-21'), updatedAt: new Date(Date.now() - 800000), lastSyncedAt: new Date(Date.now() - 120000),
    snapshot: { id: 1041, firstName: 'Dana', lastName: 'Whitfield', stage: 'Lead', source: 'Realtor.com', emails: [{ value: 'dana.w@example.com' }], phones: [{ value: '+1 415 555 0141' }] } },
  { id: 7010, sourceSystem: 'FUB', sourcePersonId: '1040', status: 'New', name: '(unnamed)',
    createdAt: new Date(Date.now() - 90000), updatedAt: new Date(Date.now() - 90000), lastSyncedAt: new Date(Date.now() - 90000),
    snapshot: { id: 1040, stage: 'Lead', source: 'Website', emails: [], phones: [{ value: '+1 415 555 0140' }] } },
  { id: 7009, sourceSystem: 'FUB', sourcePersonId: '1039', status: 'Active', name: 'Priya Nair',
    createdAt: new Date('2026-04-02'), updatedAt: new Date(Date.now() - 3000000), lastSyncedAt: new Date(Date.now() - 300000),
    snapshot: { id: 1039, firstName: 'Priya', lastName: 'Nair', stage: 'Active Client', source: 'Referral', emails: [{ value: 'priya.nair@example.com' }], phones: [{ value: '+1 415 555 0139' }] } },
];
const PERSON_STATUS_TONE = { Active: 'success', Nurture: 'info', New: 'muted', Closed: 'muted' };

// unified activity timeline + per-stream recents, mirroring the summary endpoint
function getPersonSummary(sourcePersonId, includeLive = true) {
  const person = PERSONS.find((p) => p.sourcePersonId === sourcePersonId);
  if (!person) return null;
  const base = Date.now();
  const recentCalls = [
    { id: 40231, outcome: 'Answered', durationSec: 142, at: new Date(base - 200000) },
    { id: 40190, outcome: 'No answer', durationSec: 0, at: new Date(base - 5400000) },
  ];
  const recentWorkflowRuns = [
    { id: 90412, workflowKey: 'lead_follow_up_v1', status: 'SUCCEEDED', at: new Date(base - 198000) },
    { id: 90377, workflowKey: 'missed_call_recover', status: 'FAILED', at: new Date(base - 5390000) },
  ];
  const recentWebhookEvents = [
    { id: 'evt_efd5a12', eventType: 'callsCreated', at: new Date(base - 201000) },
    { id: 'evt_efc9b03', eventType: 'peopleUpdated', at: new Date(base - 260000) },
  ];
  const activity = [
    { kind: 'call', label: 'Call · Answered (2:22)', ref: '40231', at: new Date(base - 200000) },
    { kind: 'run', label: 'Workflow run SUCCEEDED · lead_follow_up_v1', ref: '90412', at: new Date(base - 198000) },
    { kind: 'webhook', label: 'Webhook callsCreated', ref: 'evt_efd5a12', at: new Date(base - 201000) },
    { kind: 'webhook', label: 'Webhook peopleUpdated', ref: 'evt_efc9b03', at: new Date(base - 260000) },
    { kind: 'run', label: 'Workflow run FAILED · missed_call_recover', ref: '90377', at: new Date(base - 5390000) },
    { kind: 'call', label: 'Call · No answer', ref: '40190', at: new Date(base - 5400000) },
  ];
  return {
    person,
    liveStatus: includeLive ? 'ok' : 'skipped',
    liveMessage: includeLive ? 'synced with FUB' : 'local only',
    livePerson: includeLive ? { ...person.snapshot, _fetchedAt: new Date().toISOString() } : null,
    activity, recentCalls, recentWorkflowRuns, recentWebhookEvents,
  };
}
const PERSON_LIVE_TONE = { ok: 'success', failed: 'error', skipped: 'muted' };

// storyboard graph for the workflow detail preview
const STORYBOARD = {
  nodes: [
    { id: 'trigger', kind: 'trigger', label: 'Call created', x: 40, y: 70 },
    { id: 'wait', kind: 'wait', label: 'Wait 15m', x: 220, y: 70 },
    { id: 'branch', kind: 'branch', label: 'Answered?', x: 400, y: 70 },
    { id: 'task', kind: 'side-effect', label: 'Create FUB task', x: 580, y: 24 },
    { id: 'tag', kind: 'compute', label: 'Tag lead', x: 580, y: 120 },
  ],
  edges: [['trigger', 'wait'], ['wait', 'branch'], ['branch', 'task'], ['branch', 'tag']],
};
const ACCENT = {
  trigger: { fg: 'var(--color-accent-trigger-fg)', bg: 'var(--color-accent-trigger-bg)', dot: 'var(--color-accent-trigger-dot)' },
  wait: { fg: 'var(--color-accent-wait-fg)', bg: 'var(--color-accent-wait-bg)', dot: 'var(--color-accent-wait-dot)' },
  branch: { fg: 'var(--color-accent-branch-fg)', bg: 'var(--color-accent-branch-bg)', dot: 'var(--color-accent-branch-dot)' },
  'side-effect': { fg: 'var(--color-accent-side-effect-fg)', bg: 'var(--color-accent-side-effect-bg)', dot: 'var(--color-accent-side-effect-dot)' },
  compute: { fg: 'var(--color-accent-compute-fg)', bg: 'var(--color-accent-compute-bg)', dot: 'var(--color-accent-compute-dot)' },
  neutral: { fg: 'var(--color-accent-neutral-fg)', bg: 'var(--color-accent-neutral-bg)', dot: 'var(--color-accent-neutral-dot)' },
};

Object.assign(window, {
  EVENT_TYPES, formatEventType, fmtTime, fmtDateTime, makeWebhook, SEED_WEBHOOKS,
  PROCESSED_CALLS, PC_STATUS_TONE, pcStatusLabel, WORKFLOWS, WF_STATUS_TONE,
  RUNS, RUN_STATUS_TONE, PERSONS, PERSON_STATUS_TONE, PERSON_LIVE_TONE, getPersonSummary, STORYBOARD, ACCENT,
});
