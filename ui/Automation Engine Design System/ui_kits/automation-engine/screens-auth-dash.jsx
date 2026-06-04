/* Login · Signup · Dashboard.
   Auth uses a clean centered card. Dashboard drops the contextual Panel +
   Inspector and leads with a calm pipeline-health band, then refined tables. */
const { useState: useS1 } = React;

function AuthField({ label, ...rest }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em', color: 'var(--color-text-muted)', display: 'block', marginBottom: 6 }}>{label}</label>
      <Input {...rest} />
    </div>
  );
}
function AuthShell({ children }) {
  return (
    <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
      background: 'radial-gradient(700px 320px at 50% -10%, color-mix(in srgb, var(--color-brand) 12%, transparent), transparent 64%), radial-gradient(520px 260px at 88% 108%, color-mix(in srgb, var(--color-brand-2) 9%, transparent), transparent 60%), var(--color-bg)' }}>
      <div style={{ width: 'min(400px,100%)', background: 'var(--color-surface)', border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-float)', padding: '30px 30px 26px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 22 }}>
          <LogoMark size={34} />
          <div>
            <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: '-.01em' }}>Automation Engine</div>
            <div style={{ fontSize: 12, color: 'var(--color-text-muted)', fontWeight: 600 }}>Operations console</div>
          </div>
        </div>
        {children}
      </div>
    </div>
  );
}

function LoginPage({ onLogin, onSignUp, onBack }) {
  const [email, setEmail] = useS1('ops@brokerage.com');
  const [pw, setPw] = useS1('demo-password');
  return (
    <AuthShell>
      <form onSubmit={(e) => { e.preventDefault(); onLogin(); }}>
        <h1 style={{ margin: '0 0 4px', fontSize: 22, fontWeight: 700 }}>Sign in</h1>
        <p style={{ margin: '0 0 24px', fontSize: 14, color: 'var(--color-text-muted)' }}>Internal access only · JWT bearer session.</p>
        <AuthField label="Email" value={email} onChange={(e) => setEmail(e.target.value)} type="email" />
        <AuthField label="Password" value={pw} onChange={(e) => setPw(e.target.value)} type="password" />
        <div style={{ marginTop: 6 }}><Button size="lg" fullWidth type="submit">Sign in</Button></div>
        <p style={{ fontSize: 13, color: 'var(--color-text-muted)', marginTop: 18, marginBottom: 0 }}>
          Need access? <a onClick={onSignUp} style={{ color: 'var(--color-brand)', fontWeight: 600, cursor: 'pointer' }}>Request an account</a>
          {onBack ? <> · <a onClick={onBack} style={{ color: 'var(--color-text-muted)', fontWeight: 600, cursor: 'pointer' }}>Back</a></> : null}
        </p>
      </form>
    </AuthShell>
  );
}

function SignupPage({ onCreate, onSignIn }) {
  const [f, setF] = useS1({ name: '', email: '', pw: '', pw2: '' });
  return (
    <AuthShell>
      <form onSubmit={(e) => { e.preventDefault(); onCreate(); }}>
        <h1 style={{ margin: '0 0 4px', fontSize: 22, fontWeight: 700 }}>Request an account</h1>
        <p style={{ margin: '0 0 24px', fontSize: 14, color: 'var(--color-text-muted)' }}>An admin approves new operator accounts.</p>
        <AuthField label="Full name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} placeholder="Jordan Vega" />
        <AuthField label="Work email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} type="email" placeholder="you@brokerage.com" />
        <div style={{ display: 'flex', gap: 12 }}>
          <div style={{ flex: 1 }}><AuthField label="Password" value={f.pw} onChange={(e) => setF({ ...f, pw: e.target.value })} type="password" /></div>
          <div style={{ flex: 1 }}><AuthField label="Confirm" value={f.pw2} onChange={(e) => setF({ ...f, pw2: e.target.value })} type="password" /></div>
        </div>
        <div style={{ marginTop: 6 }}><Button size="lg" fullWidth type="submit">Create account</Button></div>
        <p style={{ fontSize: 13, color: 'var(--color-text-muted)', marginTop: 18, marginBottom: 0 }}>
          Already have one? <a onClick={onSignIn} style={{ color: 'var(--color-brand)', fontWeight: 600, cursor: 'pointer' }}>Sign in</a>
        </p>
      </form>
    </AuthShell>
  );
}

/* ---------- Dashboard ---------- */
function MiniSpark({ bars, color }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 2, height: 22, marginTop: 8 }}>
      {bars.map((h, i) => <span key={i} style={{ flex: 1, borderRadius: 1.5, minHeight: 2, height: h + '%',
        background: i >= bars.length - 2 ? color : 'color-mix(in srgb, ' + color + ' 35%, transparent)' }} />)}
    </div>
  );
}
function PipelineStage({ dot, label, value, sub, bars, color, last }) {
  return (
    <React.Fragment>
      <div style={{ flex: 1, minWidth: 0, background: 'var(--color-surface)', border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-md)', padding: '14px 16px', boxShadow: 'var(--shadow-subtle)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
          <span style={{ width: 8, height: 8, borderRadius: 999, background: dot }} />
          <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: '.05em', textTransform: 'uppercase', color: 'var(--color-text-muted)' }}>{label}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 8 }}>
          <span style={{ fontSize: 26, fontWeight: 700, letterSpacing: '-.02em', lineHeight: 1 }}>{value}</span>
          <span style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>{sub}</span>
        </div>
        <MiniSpark bars={bars} color={color} />
      </div>
      {!last ? <div aria-hidden="true" style={{ flexShrink: 0, color: 'var(--color-text-muted)', alignSelf: 'center' }}>
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
      </div> : null}
    </React.Fragment>
  );
}

function DashboardPage({ active, onNav, onLogout }) {
  const recent = RUNS.slice(0, 6);
  const failed = RUNS.filter((r) => r.status === 'FAILED');
  return (
    <ShellLayout active={active} onNav={onNav} onLogout={onLogout}>
      <div className="dash-root" style={{ display: 'flex', flexDirection: 'column', gap: 18, maxWidth: 1180 }}>
        <PageHeader title="Dashboard" subtitle="Live view of the pipeline — event ingestion, workflow runs, and run health.">
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, borderRadius: 'var(--radius-pill)', border: '1px solid rgba(15,118,110,.3)',
            background: 'var(--color-status-ok-bg)', color: 'var(--color-status-ok)', padding: '5px 11px', fontSize: 12, fontWeight: 600 }}>
            <span style={{ width: 8, height: 8, borderRadius: 999, background: 'var(--color-live)' }} />Pipeline live
          </span>
        </PageHeader>

        {/* pipeline-health band — the spine */}
        <div className="dash-item" style={{ display: 'flex', alignItems: 'stretch', gap: 10 }}>
          <PipelineStage dot="var(--color-brand)" color="var(--color-brand)" label="Ingested" value="148" sub="events / hr" bars={[30,50,45,65,55,75,60,90]} />
          <PipelineStage dot="var(--color-brand)" color="var(--color-brand)" label="Domain events" value="142" sub="normalized" bars={[40,55,50,60,58,70,66,82]} />
          <PipelineStage dot="var(--color-brand)" color="var(--color-brand)" label="Workflow runs" value="97" sub="started" bars={[35,45,40,55,52,60,58,72]} />
          <PipelineStage dot="var(--color-status-bad)" color="var(--color-status-bad)" label="Failed" value="3" sub="needs replay" bars={[90,100,70,75,55,45,35,25]} last />
        </div>

        {/* two-pane: runs + attention */}
        <div style={{ display: 'grid', gridTemplateColumns: '1.8fr 1fr', gap: 16, alignItems: 'start' }}>
          <div className="dash-item" style={{ '--delay': '120ms' }}>
            <PageCard title="Recent Runs" style={{ padding: 18 }}>
              <DataTable
                columns={[
                  { key: 'id', header: 'Run ID', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.id}</span> },
                  { key: 'wf', header: 'Workflow', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.workflowKey}</span> },
                  { key: 'st', header: 'Status', render: (r) => <StatusBadge label={r.status} tone={RUN_STATUS_TONE[r.status]} /> },
                  { key: 'done', header: 'Completed', render: (r) => r.completedAt ? fmtTime(r.completedAt) : '—' },
                ]}
                rows={recent} getRowKey={(r) => r.id} onRowClick={() => onNav('workflows')} />
              <div style={{ marginTop: 14 }}><Button variant="outline" size="sm" onClick={() => onNav('workflows')}>Browse all runs</Button></div>
            </PageCard>
          </div>

          <div className="dash-item" style={{ '--delay': '200ms', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <PageCard title="Needs attention" style={{ padding: 18 }}>
              {failed.length === 0 ? <p style={{ fontSize: 13, color: 'var(--color-text-muted)', margin: 0 }}>No failed runs — pipeline is healthy.</p> : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {failed.map((r) => (
                    <div key={r.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10,
                      padding: '10px 12px', borderRadius: 'var(--radius-sm)', background: 'var(--color-status-bad-bg)' }}>
                      <div style={{ minWidth: 0 }}>
                        <div className="mono" style={{ fontSize: 12, color: 'var(--color-status-bad)', fontWeight: 600 }}>{r.id}</div>
                        <div style={{ fontSize: 11, color: 'var(--color-text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.reasonCode}</div>
                      </div>
                      <Button size="sm" variant="outline" onClick={() => onNav('processed-calls')}>Replay</Button>
                    </div>
                  ))}
                </div>
              )}
            </PageCard>
            <p style={{ fontSize: 12, color: 'var(--color-text-muted)', margin: '0 2px' }}>Detailed call-to-task accountability metrics are on the roadmap.</p>
          </div>
        </div>
      </div>
    </ShellLayout>
  );
}

Object.assign(window, { LoginPage, SignupPage, DashboardPage });
