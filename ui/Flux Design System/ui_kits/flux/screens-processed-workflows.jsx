/* Processed Calls · Workflows (list + detail + storyboard) · Leads. */
const { useState: useP, useMemo: usePM } = React;

/* ---------- Processed Calls ---------- */
function ProcessedCallsPage({ active, onNav, onLogout }) {
  const [status, setStatus] = useP('ALL');
  const [selected, setSelected] = useP(null);
  const [pending, setPending] = useP(null);
  const [toast, setToast] = useP(null);
  const [outcome, setOutcome] = useP(null);

  const rows = usePM(() => status === 'ALL' ? PROCESSED_CALLS : PROCESSED_CALLS.filter((r) => r.status === status), [status]);
  const sel = PROCESSED_CALLS.find((r) => r.callId === selected);

  const fire = (msg) => { setToast(msg); setTimeout(() => setToast(null), 2600); };
  const confirmReplay = () => {
    const id = pending; setPending(null);
    setOutcome({ callId: id, at: new Date() });
    fire({ kind: 'success', title: 'Replay accepted' });
  };

  const inspector = {
    title: 'Replay Inspector',
    body: sel ? (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, fontSize: 14 }}>
        <section>
          <h3 style={{ margin: '0 0 8px', fontSize: 14, fontWeight: 600 }}>Selected Call</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <Field label="Call ID" value={sel.callId} mono />
            <Field label="Status" value={pcStatusLabel(sel.status)} />
            <Field label="Updated At" value={fmtDateTime(sel.updatedAt)} mono />
          </div>
        </section>
        <section>
          <h3 style={{ margin: '0 0 8px', fontSize: 14, fontWeight: 600 }}>Last Replay Outcome</h3>
          <p style={{ margin: 0, color: 'var(--color-text-muted)' }}>
            {outcome && outcome.callId === sel.callId ? `Replay accepted (${fmtDateTime(outcome.at)})`
              : sel.status === 'FAILED' ? 'Replay failed call' : 'Replay unavailable for non-FAILED status.'}
          </p>
        </section>
      </div>
    ) : <p style={{ fontSize: 14, color: 'var(--color-text-muted)', margin: 0 }}>Select a processed call row to view context.</p>,
  };

  return (
    <ShellLayout active={active} onNav={onNav} onLogout={onLogout} inspector={inspector}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <PageHeader title="Processed Calls" subtitle="Review processed call outcomes and replay activity." />
        <FilterBar actions={<>
          <Button size="sm"><ApplyIcon style={{ marginRight: 6 }} />Apply</Button>
          <Button size="sm" variant="outline" onClick={() => setStatus('ALL')}><ResetIcon style={{ marginRight: 6 }} />Reset</Button>
        </>}>
          <FilterIcon style={{ color: 'var(--color-text-muted)' }} />
          <ControlGroup label="Status"><Dropdown ariaLabel="Status" value={status} width={180} onChange={(v) => setStatus(v)}
            options={[{ value: 'ALL', label: 'All statuses' }, ...['RECEIVED','PROCESSING','SKIPPED','TASK_CREATED','FAILED'].map((s) => ({ value: s, label: pcStatusLabel(s) }))]} /></ControlGroup>
        </FilterBar>
        <PageCard title="Processed Calls History">
          <DataTable
            columns={[
              { key: 'callId', header: 'Call ID', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.callId}</span> },
              { key: 'status', header: 'Status', render: (r) => <StatusBadge label={pcStatusLabel(r.status)} tone={PC_STATUS_TONE[r.status]} /> },
              { key: 'rule', header: 'Rule Applied', render: (r) => r.ruleApplied || '—' },
              { key: 'task', header: 'Task ID', render: (r) => r.taskId ?? '—' },
              { key: 'fail', header: 'Failure Reason', render: (r) => r.failureReason ? <span style={{ color: 'var(--color-status-bad)' }}>{r.failureReason}</span> : '—' },
              { key: 'retry', header: 'Retries', render: (r) => r.retryCount },
              { key: 'updated', header: 'Updated At', render: (r) => fmtTime(r.updatedAt) },
              { key: 'replay', header: 'Replay', cellStyle: { textAlign: 'right' }, render: (r) => (
                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <Button size="icon" variant="outline" disabled={r.status !== 'FAILED'}
                    title={r.status === 'FAILED' ? 'Replay failed call' : 'Replay is available only for FAILED calls'}
                    aria-label={`Replay processed call ${r.callId}`}
                    onClick={(e) => { e.stopPropagation(); setPending(r.callId); }}><ReplayIcon /></Button>
                </div>) },
            ]}
            rows={rows} getRowKey={(r) => r.callId} onRowClick={(r) => setSelected(r.callId)} selectedRowKey={selected}
            emptyMessage="No processed calls available yet." />
        </PageCard>
      </div>
      <ConfirmDialog open={pending !== null} title="Confirm Replay"
        description="This will replay the selected FAILED call and re-run automation."
        confirmLabel="Replay" onConfirm={confirmReplay} onCancel={() => setPending(null)} />
      <Toast toast={toast} />
    </ShellLayout>
  );
}

/* ---------- Workflows: storyboard ---------- */
function StoryboardCanvas() {
  const { nodes, edges } = STORYBOARD;
  const NW = 132, NH = 38;
  const center = (id) => { const n = nodes.find((x) => x.id === id); return { x: n.x + NW / 2, y: n.y + NH / 2 }; };
  return (
    <div className="storyboard-canvas" style={{ position: 'relative', height: 200, borderRadius: 'var(--radius-sm)',
      border: '1px solid var(--color-border)', background: 'var(--color-surface)', overflow: 'hidden' }}>
      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
        {edges.map(([a, b], i) => { const p = center(a), q = center(b);
          return <path key={i} d={`M${p.x},${p.y} C${(p.x+q.x)/2},${p.y} ${(p.x+q.x)/2},${q.y} ${q.x},${q.y}`} fill="none" stroke="var(--color-storyboard-edge)" strokeWidth="1.5" />; })}
      </svg>
      {nodes.map((n) => { const a = ACCENT[n.kind];
        return (
          <div key={n.id} style={{ position: 'absolute', left: n.x, top: n.y, width: NW, height: NH,
            display: 'flex', alignItems: 'center', gap: 8, padding: '0 10px', borderRadius: 'var(--radius-sm)',
            background: 'var(--color-surface)', border: '1px solid var(--color-storyboard-card-border)',
            boxShadow: 'var(--color-storyboard-card-shadow)' }}>
            <span style={{ width: 9, height: 9, borderRadius: 999, background: a.dot, flexShrink: 0 }} />
            <span style={{ fontSize: 12, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{n.label}</span>
          </div>); })}
    </div>
  );
}

function WorkflowsPage({ active, onNav, onLogout }) {
  const [selKey, setSelKey] = useP(null);
  const [tab, setTab] = useP('Definition');
  const wf = WORKFLOWS.find((w) => w.key === selKey);

  const panel = { title: selKey ? 'Workflow Catalog' : 'Step Types', body: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}>
      {['Trigger','Wait','Branch','Side-effect','Compute'].map((k) => {
        const key = k.toLowerCase(); const a = ACCENT[key] || ACCENT.neutral;
        return <div key={k} style={{ display: 'flex', alignItems: 'center', gap: 8 }}><span style={{ width: 9, height: 9, borderRadius: 999, background: a.dot }} />{k}</div>;
      })}
    </div>) };

  if (wf) {
    const runs = RUNS.filter((r) => r.workflowKey === wf.key);
    const inspector = { title: 'Workflow Inspector', body: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, fontSize: 14 }}>
        <Field label="Key" value={wf.key} mono />
        <Field label="Status" value={wf.status} />
        <Field label="Version" value={'v' + wf.version} />
        <Field label="Trigger" value={formatEventType(wf.trigger)} />
      </div>) };
    return (
      <ShellLayout active={active} onNav={onNav} onLogout={onLogout} panel={panel} inspector={inspector}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
            <a onClick={() => setSelKey(null)} style={{ color: 'var(--color-brand)', cursor: 'pointer', fontWeight: 600 }}>Workflows</a>
            <span style={{ margin: '0 6px' }}>›</span><span className="mono">{wf.key}</span>
          </div>
          <PageHeader title={wf.name} subtitle="Workflow definition and lifecycle controls.">
            <div style={{ display: 'flex', gap: 8 }}>
              <Button size="sm" variant="outline">Validate</Button>
              <Button size="sm">{wf.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}</Button>
            </div>
          </PageHeader>
          <div style={{ display: 'flex', gap: 24, borderBottom: '1px solid var(--color-border)' }}>
            {['Definition', 'Storyboard', 'Runs'].map((t) => (
              <button key={t} onClick={() => setTab(t)} style={{ border: 'none', background: 'none', padding: '6px 2px',
                marginBottom: -1, borderBottom: `2px solid ${tab === t ? 'var(--color-brand)' : 'transparent'}`,
                fontSize: 14, fontWeight: tab === t ? 600 : 500, color: tab === t ? 'var(--color-text)' : 'var(--color-text-muted)' }}>{t}</button>
            ))}
          </div>
          {tab === 'Storyboard' ? <StoryboardCanvas />
            : tab === 'Runs' ? (
              <PageCard title="Workflow Runs">
                <DataTable columns={[
                  { key: 'id', header: 'Run ID', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.id}</span> },
                  { key: 'st', header: 'Status', render: (r) => <StatusBadge label={r.status} tone={RUN_STATUS_TONE[r.status]} /> },
                  { key: 'reason', header: 'Reason Code', render: (r) => r.reasonCode || 'None' },
                  { key: 'started', header: 'Started', render: (r) => fmtTime(r.startedAt) },
                ]} rows={runs} getRowKey={(r) => r.id} emptyMessage="No workflow runs found." />
              </PageCard>
            ) : (
              <PageCard title="Metadata">
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                  <Field label="Key" value={wf.key} mono />
                  <Field label="Name" value={wf.name} />
                  <Field label="Version" value={'v' + wf.version} />
                  <Field label="Created" value={fmtDateTime(wf.createdAt).slice(0, 10)} />
                </div>
              </PageCard>
            )}
        </div>
      </ShellLayout>
    );
  }

  const inspector = { title: 'Workflow Inspector', body: <p style={{ fontSize: 14, color: 'var(--color-text-muted)', margin: 0 }}>Select a workflow from the list to preview details.</p> };
  return (
    <ShellLayout active={active} onNav={onNav} onLogout={onLogout} panel={panel} inspector={inspector}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <PageHeader title="Workflows" subtitle="Read-only workflow management surface (Phase 2.1 foundation)." >
          <Button size="sm">Create Workflow</Button>
        </PageHeader>
        <PageCard title="Workflow Definitions">
          <DataTable columns={[
            { key: 'key', header: 'Key', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.key}</span> },
            { key: 'name', header: 'Name', render: (r) => r.name },
            { key: 'status', header: 'Status', render: (r) => <StatusBadge label={r.status} tone={WF_STATUS_TONE[r.status]} /> },
            { key: 'version', header: 'Version', render: (r) => 'v' + r.version },
            { key: 'created', header: 'Created', render: (r) => fmtDateTime(r.createdAt).slice(0, 10) },
          ]} rows={WORKFLOWS} getRowKey={(r) => r.key} onRowClick={(r) => { setSelKey(r.key); setTab('Storyboard'); }}
          getRowAriaLabel={(r) => 'Open workflow ' + r.key} emptyMessage="No workflows found." />
        </PageCard>
      </div>
    </ShellLayout>
  );
}

Object.assign(window, { ProcessedCallsPage, WorkflowsPage, StoryboardCanvas });
