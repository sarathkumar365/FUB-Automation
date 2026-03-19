import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { uiText } from '../shared/constants/uiText'
import { AppContentFrame } from '../shared/ui/AppContentFrame'
import { AppPanel } from '../shared/ui/AppPanel'
import { AppRail } from '../shared/ui/AppRail'
import { PanelNav } from '../shared/ui/PanelNav'
import { Button } from '../shared/ui/button'
import { InspectorPanel } from '../shared/ui/InspectorPanel'
import { ShellRegionsProvider } from './ShellRegionsProvider'
import { useShellRegions } from './useShellRegions'

export function AppShell() {
  return (
    <ShellRegionsProvider>
      <ShellLayout />
    </ShellRegionsProvider>
  )
}

function ShellLayout() {
  const [isPanelOpen, setPanelOpen] = useState(false)
  const [isInspectorOpen, setInspectorOpen] = useState(false)
  const { panelContent, inspectorContent } = useShellRegions()
  const hasDesktopPanel = Boolean(panelContent?.title || panelContent?.body)
  const hasPanelBody = Boolean(panelContent?.body)

  return (
    <div className="min-h-screen bg-[var(--color-bg)] text-[var(--color-text)] lg:h-screen lg:overflow-hidden">
      <div className="flex min-h-screen w-full lg:h-full">
        <AppRail />

        {hasDesktopPanel ? (
          <AppPanel title={panelContent?.title} className="hidden lg:block">
            {panelContent?.body}
          </AppPanel>
        ) : null}

        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          <header className="flex items-center justify-between border-b border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3 lg:hidden">
            <Button variant="outline" size="sm" onClick={() => setPanelOpen((value) => !value)}>
              {isPanelOpen ? uiText.app.shell.closePanel : uiText.app.shell.openPanel}
            </Button>
            <p className="text-sm font-semibold">{uiText.app.title}</p>
            <Button variant="outline" size="sm" onClick={() => setInspectorOpen((value) => !value)}>
              {isInspectorOpen ? uiText.app.shell.closeInspector : uiText.app.shell.openInspector}
            </Button>
          </header>

          <AppContentFrame className="min-h-0 flex-1 overflow-auto">
            <Outlet />
          </AppContentFrame>
        </div>

        <InspectorPanel title={inspectorContent?.title} className="hidden w-[320px] lg:block lg:overflow-y-auto">
          {inspectorContent?.body}
        </InspectorPanel>
      </div>

      {isPanelOpen ? (
        <div className="fixed inset-0 z-50 bg-black/30 lg:hidden" onClick={() => setPanelOpen(false)}>
          <div onClick={(event) => event.stopPropagation()}>
            <AppPanel title={panelContent?.title} className="h-full max-w-[280px]">
              <PanelNav onNavigate={() => setPanelOpen(false)} />
              {hasPanelBody ? <div className="my-3 border-t border-[var(--color-border)]" /> : null}
              {panelContent?.body}
            </AppPanel>
          </div>
        </div>
      ) : null}

      {isInspectorOpen ? (
        <div className="fixed inset-0 z-50 bg-black/30 lg:hidden" onClick={() => setInspectorOpen(false)}>
          <div onClick={(event) => event.stopPropagation()}>
            <InspectorPanel title={inspectorContent?.title} className="ml-auto h-full w-[min(85vw,320px)] border-l border-[var(--color-border)]">
              {inspectorContent?.body}
            </InspectorPanel>
          </div>
        </div>
      ) : null}
    </div>
  )
}
