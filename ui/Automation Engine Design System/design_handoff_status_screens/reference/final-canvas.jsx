/* FINAL — Direction B (ghost glyph) with Direction C's mono status strip
   folded in. The chosen family. Light + dark + in-shell 404. */

function Dark({ children }) {
  return <div data-theme="dark" style={{ height: '100%' }}>{children}</div>;
}

const FW = 1180, FH = 720;
const FSHELL_W = 1340, FSHELL_H = 760;

function FinalBoard() {
  return (
    <DesignCanvas>
      <DCSection id="final-light" title="Final · B + console strip"
        subtitle="Ghost-glyph shell with Direction C's mono status line folded in — the chosen family. Light.">
        <DCArtboard id="f-err" label="App error" width={FW} height={FH}>
          <AppErrorFallback variant="B" strip detailsOpen />
        </DCArtboard>
        <DCArtboard id="f-404" label="404 — Not found" width={FW} height={FH}>
          <NotFoundPage variant="B" strip />
        </DCArtboard>
        <DCArtboard id="f-sess" label="Session disabled" width={FW} height={FH}>
          <SessionDisabledPage variant="B" strip />
        </DCArtboard>
      </DCSection>

      <DCSection id="final-dark" title="Final · Dark"
        subtitle="Same components, token-remapped automatically to the deep-slate canvas.">
        <DCArtboard id="fd-err" label="App error" width={FW} height={FH}>
          <Dark><AppErrorFallback variant="B" strip /></Dark>
        </DCArtboard>
        <DCArtboard id="fd-404" label="404 — Not found" width={FW} height={FH}>
          <Dark><NotFoundPage variant="B" strip /></Dark>
        </DCArtboard>
        <DCArtboard id="fd-sess" label="Session disabled" width={FW} height={FH}>
          <Dark><SessionDisabledPage variant="B" strip /></Dark>
        </DCArtboard>
      </DCSection>

      <DCSection id="final-shell" title="404 inside the four-region shell"
        subtitle="Content-area placement beside the rail — gradient drops, page sits calm. Light + dark.">
        <DCArtboard id="fs-l" label="In shell · light" width={FSHELL_W} height={FSHELL_H}>
          <NotFoundPage inShell />
        </DCArtboard>
        <DCArtboard id="fs-d" label="In shell · dark" width={FSHELL_W} height={FSHELL_H}>
          <Dark><NotFoundPage inShell /></Dark>
        </DCArtboard>
      </DCSection>
    </DesignCanvas>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<FinalBoard />);
