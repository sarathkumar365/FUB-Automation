/* Settings — panel section-nav + clean rows (the cleaner layout).
   Lean sections after removals: Business hours · Feature flags · Connections
   (editable) · Managed webhooks. One card per section, hairline-separated
   rows, no nested boxes. Edits raise a Save bar. */
const { useState: useSet } = React;

const TZ = ['America/Toronto', 'America/New_York', 'America/Chicago', 'America/Denver', 'America/Los_Angeles', 'UTC'];

const tog = (def) => ({ t: 'toggle', def });
const num = (def, unit) => ({ t: 'number', def, unit });
const sel = (def, options) => ({ t: 'select', def, options });
const text = (def) => ({ t: 'text', def });
const secret = (def) => ({ t: 'secret', def });

const SECTIONS = [
  { id: 'hours', title: 'Business hours', editable: true,
    note: 'When automations are allowed to act on events.',
    rows: [
      { label: 'Timezone', key: 'automation.business-hours.timezone', ctrl: sel('America/Toronto', TZ) },
      { label: 'Start hour', key: 'automation.business-hours.start-hour', desc: 'Hour of day work begins (0–23).', ctrl: num(9, 'h') },
      { label: 'End hour', key: 'automation.business-hours.end-hour', desc: 'Hour of day work ends (0–23).', ctrl: num(18, 'h') },
      { label: 'Weekdays only', key: 'automation.business-hours.weekdays-only', desc: 'Skip weekends when applying business hours.', ctrl: tog(true) },
    ] },
  { id: 'flags', title: 'Feature flags', editable: true,
    note: 'Platform-level switches. Changes apply globally.',
    rows: [
      { label: 'Emit engine-write events', key: 'engine.write.emit-events', desc: 'Re-emit engine writes as domain events so downstream workflows can react.', ctrl: tog(false) },
      { label: 'Workflow worker', key: 'workflow.worker.enabled', desc: 'Master switch for the engine processing loop.', ctrl: tog(true) },
      { label: 'Stale-run requeue', key: 'workflow.worker.stale-processing-enabled', desc: 'Requeue runs stuck in PROCESSING past the stale timeout.', ctrl: tog(true) },
      { label: 'Follow Up Boss source', key: 'webhook.sources.fub.enabled', desc: 'Accept inbound events from the FUB source.', ctrl: tog(true) },
    ] },
  { id: 'connections', title: 'Connections', editable: true,
    note: 'Follow Up Boss credentials — used to authenticate and verify inbound webhooks.',
    connected: true,
    rows: [
      { label: 'Base URL', key: 'fub.base-url', ctrl: text('https://api.followupboss.com/v1') },
      { label: 'X-System', key: 'fub.x-system', ctrl: text('AutomationEngineProd') },
      { label: 'API key', key: 'fub.api-key', ctrl: secret('fk_live_8a21c9d4e7') },
      { label: 'X-System key', key: 'fub.x-system-key', ctrl: secret('xs_7b3f01aa92') },
    ] },
  { id: 'managed', title: 'Managed webhooks', managed: true,
    note: 'FUB registrations this system keeps in sync.' },
];

const MANAGED = [
  { event: 'callsCreated', last: '2m ago' },
  { event: 'peopleCreated', last: '6m ago' },
  { event: 'peopleUpdated', last: '1m ago' },
];

function SecretField({ value, onChange }) {
  const [show, setShow] = useSet(false);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <Input type={show ? 'text' : 'password'} value={value} onChange={(e) => onChange(e.target.value)} style={{ width: 210, height: 34, fontSize: 13, fontFamily: 'var(--font-mono)' }} />
      <button type="button" onClick={() => setShow((s) => !s)} style={{ border: 'none', background: 'none', fontSize: 12, fontWeight: 600, color: 'var(--color-brand)', cursor: 'pointer' }}>{show ? 'Hide' : 'Show'}</button>
    </div>
  );
}

function SettingRow({ row, val, onChange, last }) {
  const c = row.ctrl;
  let control;
  if (c.t === 'toggle') control = <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}><span style={{ fontSize: 11, fontWeight: 700, color: val ? 'var(--color-status-ok)' : 'var(--color-text-muted)', minWidth: 22, textAlign: 'right' }}>{val ? 'ON' : 'OFF'}</span><Toggle checked={val} onChange={onChange} ariaLabel={row.label} /></div>;
  else if (c.t === 'number') control = <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Input type="number" value={val} onChange={(e) => onChange(e.target.value)} style={{ width: 84, height: 34, fontSize: 13 }} />{c.unit ? <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>{c.unit}</span> : null}</div>;
  else if (c.t === 'select') control = <Dropdown value={val} width={210} ariaLabel={row.label} onChange={onChange} options={c.options} />;
  else if (c.t === 'text') control = <Input value={val} onChange={(e) => onChange(e.target.value)} style={{ width: 280, height: 34, fontSize: 13 }} />;
  else if (c.t === 'secret') control = <SecretField value={val} onChange={onChange} />;

  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 24, alignItems: 'center',
      padding: '15px 0', borderBottom: last ? 'none' : '1px solid color-mix(in srgb, var(--color-border) 60%, transparent)' }}>
      <div style={{ minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column', gap: 3 }}>
        <span style={{ fontSize: 14, fontWeight: 600 }}>{row.label}</span>
        {row.desc ? <span style={{ fontSize: 12.5, color: 'var(--color-text-muted)', maxWidth: 460 }}>{row.desc}</span> : null}
        <span className="mono" style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>{row.key}</span>
      </div>
      <div style={{ flexShrink: 0 }}>{control}</div>
    </div>
  );
}

function SettingsPage({ active, onNav, onLogout }) {
  const [sectionId, setSectionId] = useSet('hours');
  const [vals, setVals] = useSet(() => {
    const v = {};
    SECTIONS.forEach((s) => (s.rows || []).forEach((r) => { if (r.ctrl) v[r.key] = r.ctrl.def; }));
    return v;
  });
  const [dirty, setDirty] = useSet(false);
  const [toast, setToast] = useSet(null);
  const section = SECTIONS.find((s) => s.id === sectionId);
  const setVal = (k, v) => { setVals((p) => ({ ...p, [k]: v })); setDirty(true); };
  const reset = () => { const v = {}; SECTIONS.forEach((s) => (s.rows || []).forEach((r) => { if (r.ctrl) v[r.key] = r.ctrl.def; })); setVals(v); setDirty(false); };
  const save = () => { setDirty(false); setToast({ kind: 'success', title: 'Settings saved' }); setTimeout(() => setToast(null), 2400); };

  const panel = { title: 'Configuration', body: (
    <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {SECTIONS.map((s) => {
        const on = s.id === sectionId;
        return (
          <button key={s.id} onClick={() => setSectionId(s.id)}
            style={{ textAlign: 'left', border: 'none', borderRadius: 'var(--radius-sm)', padding: '8px 10px', fontSize: 13,
              cursor: 'pointer', fontWeight: on ? 600 : 500, background: on ? 'var(--color-brand-soft)' : 'transparent',
              color: on ? 'var(--color-brand)' : 'var(--color-text)' }}>{s.title}</button>
        );
      })}
    </nav>
  ) };

  return (
    <ShellLayout active={active} onNav={onNav} onLogout={onLogout} panel={panel}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 720 }}>
        <PageHeader title="Settings" subtitle="Platform configuration for this workspace." />

        {section.managed ? (
          <PageCard title="Managed webhooks" style={{ padding: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginBottom: 14 }}>
              <p style={{ margin: 0, fontSize: 13, color: 'var(--color-text-muted)' }}>{section.note}</p>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: 11.5, color: 'var(--color-text-muted)' }}>Synced 2m ago</span>
                <Button size="sm" variant="outline"><RefreshIcon style={{ marginRight: 6 }} />Sync now</Button>
              </div>
            </div>
            <DataTable
              columns={[
                { key: 'event', header: 'Event', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.event}</span> },
                { key: 'status', header: 'Status', render: () => <StatusBadge label="Active" tone="success" /> },
                { key: 'last', header: 'Last delivery', render: (r) => r.last },
              ]}
              rows={MANAGED} getRowKey={(r) => r.event} />
          </PageCard>
        ) : (
          <PageCard title={section.title} style={{ padding: '18px 20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
              <p style={{ margin: 0, fontSize: 13, color: 'var(--color-text-muted)', maxWidth: 520 }}>{section.note}</p>
              {section.connected ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600, color: 'var(--color-status-ok)', background: 'var(--color-status-ok-bg)', borderRadius: 999, padding: '4px 10px', flexShrink: 0 }}><span style={{ width: 7, height: 7, borderRadius: 999, background: 'var(--color-live)' }} />Connected</span> : null}
            </div>
            <div style={{ marginTop: 6 }}>
              {section.rows.map((r, i) => <SettingRow key={r.key} row={r} val={vals[r.key]} onChange={(v) => setVal(r.key, v)} last={i === section.rows.length - 1} />)}
            </div>
          </PageCard>
        )}

        {dirty ? (
          <div style={{ position: 'sticky', bottom: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12,
            background: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-hover)', padding: '12px 16px' }}>
            <span style={{ fontSize: 13, fontWeight: 500 }}>You have unsaved changes.</span>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button size="sm" variant="outline" onClick={reset}>Reset</Button>
              <Button size="sm" onClick={save}>Save changes</Button>
            </div>
          </div>
        ) : null}
      </div>
      <Toast toast={toast} />
    </ShellLayout>
  );
}

Object.assign(window, { SettingsPage });
