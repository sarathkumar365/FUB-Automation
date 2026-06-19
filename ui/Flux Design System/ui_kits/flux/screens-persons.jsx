/* Persons (formerly Leads) — list + rich detail.
   Mirrors GET /admin/persons and GET /admin/persons/{id}/summary:
   identifiers, timestamps, live refresh status, a unified activity timeline,
   and per-stream recents (calls / workflow runs / webhooks) + stored snapshot. */
const { useState: usePr } = React;

const ACT_META = {
  call:    { dot: 'var(--color-accent-trigger-dot)', tag: 'Call' },
  run:     { dot: 'var(--color-accent-wait-dot)', tag: 'Run' },
  webhook: { dot: 'var(--color-accent-side-effect-dot)', tag: 'Webhook' },
};

function relTime(d) {
  const s = Math.floor((Date.now() - d.getTime()) / 1000);
  if (s < 60) return s + 's ago';
  if (s < 3600) return Math.floor(s / 60) + 'm ago';
  if (s < 86400) return Math.floor(s / 3600) + 'h ago';
  return Math.floor(s / 86400) + 'd ago';
}

function PersonsPage({ active, onNav, onLogout }) {
  const [selId, setSelId] = usePr(null);
  const [filter, setFilter] = usePr('all');
  const [live, setLive] = usePr(true);

  if (selId) {
    const sum = getPersonSummary(selId, live);
    const p = sum.person;
    const acts = filter === 'all' ? sum.activity
      : sum.activity.filter((a) => (filter === 'calls' ? a.kind === 'call' : filter === 'runs' ? a.kind === 'run' : a.kind === 'webhook'));

    const inspector = { title: 'Person', body: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, fontSize: 14 }}>
        <section>
          <h4 style={{ margin: '0 0 8px', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em', color: 'var(--color-text-muted)' }}>Identifiers</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Field label="Internal ID" value={p.id} mono />
            <Field label="Source System" value={p.sourceSystem} />
            <Field label="Source Person ID" value={p.sourcePersonId} mono />
            <Field label="Status" value={p.status} />
          </div>
        </section>
        <section>
          <h4 style={{ margin: '0 0 8px', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em', color: 'var(--color-text-muted)' }}>Timestamps</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Field label="Created" value={fmtDateTime(p.createdAt)} mono />
            <Field label="Updated" value={fmtDateTime(p.updatedAt)} mono />
            <Field label="Last Synced" value={fmtDateTime(p.lastSyncedAt)} mono />
          </div>
        </section>
        <section>
          <h4 style={{ margin: '0 0 8px', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em', color: 'var(--color-text-muted)' }}>Stored snapshot</h4>
          <pre className="mono" style={{ margin: 0, maxHeight: 180, overflow: 'auto', borderRadius: 'var(--radius-sm)', border: '1px solid var(--color-border)', background: 'var(--color-surface-alt)', padding: 10, fontSize: 11 }}>{JSON.stringify(p.snapshot, null, 2)}</pre>
        </section>
      </div>
    ) };

    return (
      <ShellLayout active={active} onNav={onNav} onLogout={onLogout} inspector={inspector}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 760 }}>
          <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
            <a onClick={() => setSelId(null)} style={{ color: 'var(--color-brand)', cursor: 'pointer', fontWeight: 600 }}>Persons</a>
            <span style={{ margin: '0 6px' }}>›</span><span className="mono">{p.sourcePersonId}</span>
          </div>
          <PageHeader title={p.name === '(unnamed)' ? 'Person ' + p.sourcePersonId : p.name}
            subtitle={`${p.sourceSystem} · ${p.snapshot.stage || '—'}`}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <StatusBadge label={sum.liveStatus === 'ok' ? 'Live · ' + sum.liveMessage : sum.liveMessage} tone={PERSON_LIVE_TONE[sum.liveStatus]} />
              <Button size="sm" variant="outline" onClick={() => setLive((v) => !v)}><RefreshIcon style={{ marginRight: 6 }} />Refresh from FUB</Button>
            </div>
          </PageHeader>

          <PageCard title="Activity" style={{ padding: 18 }}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
              {[['all', 'All'], ['calls', 'Calls'], ['runs', 'Workflow runs'], ['webhooks', 'Webhooks']].map(([v, l]) => (
                <button key={v} onClick={() => setFilter(v)} style={{ border: '1px solid var(--color-border)', borderRadius: 'var(--radius-pill)',
                  padding: '4px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer',
                  background: filter === v ? 'var(--color-brand-soft)' : 'var(--color-surface)',
                  color: filter === v ? 'var(--color-brand)' : 'var(--color-text-muted)',
                  borderColor: filter === v ? 'transparent' : 'var(--color-border)' }}>{l}</button>
              ))}
            </div>
            {acts.length === 0 ? <p style={{ fontSize: 13, color: 'var(--color-text-muted)', margin: 0 }}>No recent activity recorded for this person.</p> : (
              <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 0, paddingLeft: 6 }}>
                <span style={{ position: 'absolute', left: 11, top: 6, bottom: 6, width: 2, background: 'var(--color-border)' }} />
                {acts.map((a, i) => (
                  <div key={i} style={{ position: 'relative', display: 'flex', gap: 14, alignItems: 'flex-start', padding: '8px 0' }}>
                    <span style={{ width: 11, height: 11, borderRadius: 999, background: ACT_META[a.kind].dot, marginTop: 3, flexShrink: 0, zIndex: 1, boxShadow: '0 0 0 3px var(--color-surface)' }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                        <span style={{ fontSize: 13.5, fontWeight: 500 }}>{a.label}</span>
                        <span style={{ fontSize: 12, color: 'var(--color-text-muted)', whiteSpace: 'nowrap' }}>{relTime(a.at)}</span>
                      </div>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>{ACT_META[a.kind].tag} · {a.ref}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </PageCard>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12 }}>
            <StreamCard title="Recent calls" rows={sum.recentCalls.map((c) => ({ a: c.id, b: c.outcome }))} />
            <StreamCard title="Workflow runs" rows={sum.recentWorkflowRuns.map((r) => ({ a: r.id, b: r.status }))} />
            <StreamCard title="Webhooks" rows={sum.recentWebhookEvents.map((w) => ({ a: w.id, b: formatEventType(w.eventType) }))} />
          </div>
        </div>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout active={active} onNav={onNav} onLogout={onLogout}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 1080 }}>
        <PageHeader title="Persons" subtitle="Local person records synced from source systems, with unified activity." />
        <PageCard title="Persons" style={{ padding: 18 }}>
          <DataTable
            columns={[
              { key: 'src', header: 'Source', render: (r) => r.sourceSystem },
              { key: 'pid', header: 'Person ID', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.sourcePersonId}</span> },
              { key: 'name', header: 'Name', render: (r) => <span style={{ color: r.name === '(unnamed)' ? 'var(--color-text-muted)' : 'inherit', fontWeight: 500 }}>{r.name}</span> },
              { key: 'status', header: 'Status', render: (r) => <StatusBadge label={r.status} tone={PERSON_STATUS_TONE[r.status]} /> },
              { key: 'updated', header: 'Updated', render: (r) => relTime(r.updatedAt) },
              { key: 'synced', header: 'Last Synced', render: (r) => relTime(r.lastSyncedAt) },
            ]}
            rows={PERSONS} getRowKey={(r) => r.sourcePersonId} onRowClick={(r) => { setSelId(r.sourcePersonId); setFilter('all'); setLive(true); }}
            getRowAriaLabel={(r) => 'Open person ' + r.sourcePersonId} emptyMessage="No persons match the selected filters." />
        </PageCard>
      </div>
    </ShellLayout>
  );
}

function StreamCard({ title, rows }) {
  return (
    <div style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', padding: 14 }}>
      <h4 style={{ margin: '0 0 10px', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em', color: 'var(--color-text-muted)' }}>{title}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {rows.map((r, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: 'space-between', gap: 8, fontSize: 12.5 }}>
            <span className="mono" style={{ color: 'var(--color-text-muted)' }}>{r.a}</span>
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.b}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

Object.assign(window, { PersonsPage });
