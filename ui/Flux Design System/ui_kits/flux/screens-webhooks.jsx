/* Webhooks — live streaming feed. Mirrors WebhooksPage.tsx:
   pulse-dot live pill, icon pause, activity tick-strip, structured filters, inspector. */
const { useState: useW, useEffect: useWE, useRef: useWR, useMemo: useWM } = React;

const MAX_TICKS = 20;

function LivePill({ state }) {
  const map = {
    open:       { label: 'Live', bd: 'rgba(15,118,110,.3)', bg: 'var(--color-status-ok-bg)', fg: 'var(--color-status-ok)', dot: 'var(--color-live)' },
    connecting: { label: 'Connecting', bd: 'rgba(180,83,9,.3)', bg: 'var(--color-status-warn-bg)', fg: 'var(--color-status-warn)', dot: 'var(--color-status-warn)' },
    error:      { label: 'Reconnecting', bd: 'rgba(190,18,60,.3)', bg: 'var(--color-status-bad-bg)', fg: 'var(--color-status-bad)', dot: 'var(--color-status-bad)' },
    paused:     { label: 'Paused', bd: 'var(--color-border)', bg: 'var(--color-surface-alt)', fg: 'var(--color-text-muted)', dot: 'var(--color-text-muted)' },
  };
  const s = map[state];
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, borderRadius: 'var(--radius-pill)',
      border: `1px solid ${s.bd}`, background: s.bg, color: s.fg, padding: '5px 11px', fontSize: 12, fontWeight: 600 }}>
      <span style={{ width: 8, height: 8, borderRadius: 999, background: s.dot }} />{s.label}
    </span>
  );
}

function ActivityTickStrip({ filled, beatToken }) {
  const pulse = Math.min(filled, MAX_TICKS - 1);
  return (
    <div aria-hidden="true" style={{ display: 'flex', gap: 4, alignItems: 'center', marginBottom: 12 }}>
      {Array.from({ length: MAX_TICKS }, (_, i) => (
        <span key={i} style={{ height: 6, flex: 1, borderRadius: 999, position: 'relative',
          background: i < filled ? 'color-mix(in srgb, var(--color-live) 60%, transparent)' : 'var(--color-border)' }}>
          {beatToken && i === pulse
            ? <span key={beatToken} className="heartbeat-tick" style={{ position: 'absolute', inset: 0, borderRadius: 999, background: 'var(--color-status-warn)' }} />
            : null}
        </span>
      ))}
    </div>
  );
}

function WebhooksPage({ active, onNav, onLogout }) {
  const [rows, setRows] = useW(SEED_WEBHOOKS);
  const [paused, setPaused] = useW(false);
  const [selectedId, setSelectedId] = useW(null);
  const [beat, setBeat] = useW(null);
  const [draft, setDraft] = useW({ source: 'ALL', status: 'ALL', eventType: '', from: '', to: '' });
  const [appliedET, setAppliedET] = useW('');
  const timer = useWR(null);

  useWE(() => {
    if (paused) return;
    timer.current = setInterval(() => {
      const ev = makeWebhook(0);
      setRows((prev) => [ev, ...prev].slice(0, 60));
      setBeat(Date.now() + ':' + Math.random());
    }, 2600);
    return () => clearInterval(timer.current);
  }, [paused]);

  const visible = useWM(() => appliedET ? rows.filter((r) => r.eventType === appliedET) : rows, [rows, appliedET]);
  const selected = rows.find((r) => r.id === selectedId);

  const inspector = {
    title: 'Webhook Inspector',
    body: selected ? (
      <section style={{ display: 'flex', flexDirection: 'column', gap: 12, fontSize: 14 }}>
        <h4 style={{ margin: 0, fontWeight: 600 }}>Webhook Event Detail</h4>
        <Field label="Event ID" value={selected.eventId} mono />
        <Field label="Source" value={selected.source} />
        <Field label="Event Type" value={formatEventType(selected.eventType)} />
        <Field label="Status" value={selected.status} />
        <Field label="Payload Hash" value={selected.payloadHash} mono />
        <Field label="Received At" value={fmtDateTime(selected.receivedAt)} mono />
        <div>
          <p style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em', color: 'var(--color-text-muted)', margin: '0 0 4px' }}>Payload</p>
          <pre className="mono" style={{ margin: 0, maxHeight: 200, overflow: 'auto', borderRadius: 'var(--radius-sm)', border: '1px solid var(--color-border)', background: 'var(--color-surface-alt)', padding: 10, fontSize: 11.5 }}>{JSON.stringify(selected.payload, null, 2)}</pre>
        </div>
      </section>
    ) : <p style={{ fontSize: 14, color: 'var(--color-text-muted)', margin: 0 }}>Select a webhook row to load event detail.</p>,
  };

  const apply = () => setAppliedET(draft.eventType);
  const reset = () => { setDraft({ source: 'ALL', status: 'ALL', eventType: '', from: '', to: '' }); setAppliedET(''); };

  return (
    <ShellLayout active={active} onNav={onNav} onLogout={onLogout} inspector={inspector}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <PageHeader title="Webhooks" subtitle="Webhook event operations workspace.">
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <LivePill state={paused ? 'paused' : 'open'} />
              <Button size="icon" variant="outline" aria-label={paused ? 'Resume webhook live stream' : 'Pause webhook live stream'} onClick={() => setPaused((p) => !p)}>
                {paused ? <ResumeIcon /> : <PauseIcon />}
              </Button>
            </div>
            <span className="mono" style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>Last heartbeat: {rows[0] ? fmtTime(rows[0].receivedAt) : '—'}</span>
          </div>
        </PageHeader>

        <FilterBar actions={<>
          <Button size="sm" onClick={apply}><ApplyIcon style={{ marginRight: 6 }} />Apply</Button>
          <Button size="sm" variant="outline" onClick={reset}><ResetIcon style={{ marginRight: 6 }} />Reset</Button>
        </>}>
          <FilterIcon style={{ color: 'var(--color-text-muted)' }} />
          <ControlGroup label="Source"><Dropdown ariaLabel="Source" value={draft.source} width={138} onChange={(v) => setDraft({ ...draft, source: v })} options={[{ value: 'ALL', label: 'All sources' }, { value: 'FUB', label: 'FUB' }]} /></ControlGroup>
          <ControlGroup label="Status"><Dropdown ariaLabel="Status" value={draft.status} width={138} onChange={(v) => setDraft({ ...draft, status: v })} options={[{ value: 'ALL', label: 'All statuses' }, { value: 'RECEIVED', label: 'RECEIVED' }]} /></ControlGroup>
          <ControlGroup label="Event Type"><Dropdown ariaLabel="Event Type" value={draft.eventType} width={196} onChange={(v) => setDraft({ ...draft, eventType: v })} options={[{ value: '', label: 'All event types' }, ...EVENT_TYPES.map((t) => ({ value: t, label: formatEventType(t) }))]} /></ControlGroup>
          <ControlGroup label="From"><DateInput value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} style={{ width: 160 }} /></ControlGroup>
        </FilterBar>

        <PageCard title="Webhook History">
          <ActivityTickStrip filled={Math.min(visible.length, MAX_TICKS)} beatToken={paused ? null : beat} />
          <DataTable
            columns={[
              { key: 'eventId', header: 'Event ID', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.eventId}</span> },
              { key: 'source', header: 'Source', render: (r) => r.source },
              { key: 'eventType', header: 'Event Type', render: (r) => formatEventType(r.eventType) },
              { key: 'status', header: 'Status', render: (r) => <StatusBadge label={r.status} tone="info" /> },
              { key: 'receivedAt', header: 'Received At', render: (r) => fmtTime(r.receivedAt) },
            ]}
            rows={visible.slice(0, 18)} getRowKey={(r) => r.id}
            onRowClick={(r) => setSelectedId(r.id)} selectedRowKey={selectedId}
            rowClassName={(r) => (!paused && rows[0] && r.id === rows[0].id) ? 'row-new' : ''}
            emptyMessage="No webhook events found for the selected filters." />
        </PageCard>
      </div>
    </ShellLayout>
  );
}

function Field({ label, value, mono }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em', color: 'var(--color-text-muted)' }}>{label}</span>
      <span className={mono ? 'mono' : ''} style={{ fontSize: mono ? 12 : 14 }}>{value}</span>
    </div>
  );
}

Object.assign(window, { WebhooksPage, LivePill, ActivityTickStrip, Field });
