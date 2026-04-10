import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { uiText } from '../../../shared/constants/uiText'
import { Button } from '../../../shared/ui/button'
import { DateInput } from '../../../shared/ui/DateInput'
import { ApplyIcon, FilterIcon, ResetIcon } from '../../../shared/ui/icons'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { Select } from '../../../shared/ui/select'
import { useNotify } from '../../../shared/notifications/useNotify'
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog'
import { useActivatePolicyMutation } from '../data/useActivatePolicyMutation'
import { useCreatePolicyMutation } from '../data/useCreatePolicyMutation'
import { useUpdatePolicyMutation } from '../data/useUpdatePolicyMutation'
import { usePoliciesQuery } from '../data/usePoliciesQuery'
import { usePolicyExecutionsQuery } from '../data/usePolicyExecutionsQuery'
import {
  createPoliciesSearchParams,
  parsePoliciesSearchParams,
  toPoliciesFilterDraft,
  type PoliciesFilterDraft,
  type PoliciesPageSearchState,
  type PoliciesTab,
} from '../lib/policiesSearchParams'
import type { PolicyExecutionRunListItem, PolicyExecutionRunStatus, PolicyResponse } from '../lib/policySchemas'
import { runStatusLabel } from '../lib/policiesDisplay'
import { usePolicyExecutionDetailQuery } from '../data/usePolicyExecutionDetailQuery'
import { ManageTab } from './ManageTab'
import { PolicyFormModal, type PolicyFormData } from './PolicyFormModal'
import { PolicyInspector } from './PolicyInspector'
import { RunInspector } from './RunInspector'
import { RunsTab } from './RunsTab'

const RUN_STATUS_OPTIONS: PolicyExecutionRunStatus[] = [
  'PENDING',
  'BLOCKED_POLICY',
  'DUPLICATE_IGNORED',
  'COMPLETED',
  'FAILED',
]

export function PoliciesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchState = useMemo(() => parsePoliciesSearchParams(searchParams), [searchParams])

  // Draft filter state (same pattern as webhooks page)
  const filterDraftKey = useMemo(
    () =>
      [searchState.status ?? '', searchState.policyKey ?? '', searchState.from ?? '', searchState.to ?? ''].join('|'),
    [searchState.status, searchState.policyKey, searchState.from, searchState.to],
  )
  const [draftFilterState, setDraftFilterState] = useState<{ key: string; value: PoliciesFilterDraft }>(() => ({
    key: filterDraftKey,
    value: toPoliciesFilterDraft(searchState),
  }))
  const draftFilters = draftFilterState.key === filterDraftKey ? draftFilterState.value : toPoliciesFilterDraft(searchState)

  // Cursor stack for prev navigation
  const [cursorStack, setCursorStack] = useState<string[]>([])

  // Queries
  const policiesQuery = usePoliciesQuery()
  const executionsQuery = usePolicyExecutionsQuery(
    {
      status: searchState.status,
      policyKey: searchState.policyKey,
      from: searchState.from,
      to: searchState.to,
      cursor: searchState.cursor,
    },
    searchState.tab === 'runs',
  )

  const executionDetailQuery = usePolicyExecutionDetailQuery(searchState.selectedRun)

  // Mutations
  const createMutation = useCreatePolicyMutation()
  const updateMutation = useUpdatePolicyMutation()
  const activateMutation = useActivatePolicyMutation()
  const notify = useNotify()

  // Modal state
  const [formModalOpen, setFormModalOpen] = useState(false)
  const [editingPolicy, setEditingPolicy] = useState<PolicyResponse | undefined>(undefined)
  const [activateConfirmOpen, setActivateConfirmOpen] = useState(false)
  const [policyToActivate, setPolicyToActivate] = useState<PolicyResponse | undefined>(undefined)

  // Unique policy keys for filter dropdown
  const policyKeyOptions = useMemo(() => {
    const policies = policiesQuery.data ?? []
    const keys = [...new Set(policies.map((p) => p.policyKey))]
    keys.sort()
    return keys
  }, [policiesQuery.data])

  // Panel summary
  const activePolicyCount = useMemo(
    () => (policiesQuery.data ?? []).filter((p) => p.status === 'ACTIVE').length,
    [policiesQuery.data],
  )
  const runsShown = executionsQuery.data?.items.length ?? 0
  const failedCount = useMemo(
    () => (executionsQuery.data?.items ?? []).filter((r) => r.status === 'FAILED').length,
    [executionsQuery.data],
  )

  // --- Handlers ---

  const handleTabChange = (tab: PoliciesTab) => {
    setSearchParams(
      createPoliciesSearchParams({
        ...searchState,
        tab,
        selectedRun: undefined,
        selectedPolicy: undefined,
        cursor: undefined,
      }),
    )
    setCursorStack([])
  }

  const handleApply = useCallback(() => {
    const nextState: PoliciesPageSearchState = {
      tab: searchState.tab,
      status: draftFilters.status === 'ALL' ? undefined : draftFilters.status,
      policyKey: draftFilters.policyKey || undefined,
      from: draftFilters.from || undefined,
      to: draftFilters.to || undefined,
      cursor: undefined,
      selectedRun: undefined,
      selectedPolicy: undefined,
    }
    setSearchParams(createPoliciesSearchParams(nextState))
    setCursorStack([])
  }, [searchState.tab, draftFilters, setSearchParams])

  const handleReset = useCallback(() => {
    setDraftFilterState({
      key: filterDraftKey,
      value: { status: 'ALL', policyKey: '', from: '', to: '' },
    })
    setSearchParams(
      createPoliciesSearchParams({
        tab: searchState.tab,
      }),
    )
    setCursorStack([])
  }, [filterDraftKey, searchState.tab, setSearchParams])

  const handleRunClick = (row: PolicyExecutionRunListItem) => {
    const nextSelectedRun = searchState.selectedRun === row.id ? undefined : row.id
    setSearchParams(
      createPoliciesSearchParams({
        ...searchState,
        selectedRun: nextSelectedRun,
        selectedPolicy: undefined,
      }),
    )
  }

  const handleNext = () => {
    const nextCursor = executionsQuery.data?.nextCursor
    if (!nextCursor) return
    // Push current cursor onto stack before navigating forward
    setCursorStack((prev) => [...prev, searchState.cursor ?? ''])
    setSearchParams(
      createPoliciesSearchParams({
        ...searchState,
        cursor: nextCursor,
        selectedRun: undefined,
      }),
    )
  }

  const handlePrev = () => {
    if (cursorStack.length === 0) return
    const prevCursor = cursorStack[cursorStack.length - 1]
    setCursorStack((prev) => prev.slice(0, -1))
    setSearchParams(
      createPoliciesSearchParams({
        ...searchState,
        cursor: prevCursor || undefined,
        selectedRun: undefined,
      }),
    )
  }

  const handlePolicyClick = (row: PolicyResponse) => {
    const nextSelectedPolicy = searchState.selectedPolicy === row.id ? undefined : row.id
    setSearchParams(
      createPoliciesSearchParams({
        ...searchState,
        selectedPolicy: nextSelectedPolicy,
        selectedRun: undefined,
      }),
    )
  }

  // Selected policy for inspector
  const selectedPolicy = useMemo(() => {
    if (searchState.selectedPolicy === undefined) return undefined
    return (policiesQuery.data ?? []).find((p) => p.id === searchState.selectedPolicy)
  }, [searchState.selectedPolicy, policiesQuery.data])

  // Mutation handlers
  const handleCreateNew = () => {
    setEditingPolicy(undefined)
    setFormModalOpen(true)
  }

  const handleEdit = useCallback(() => {
    if (!selectedPolicy) return
    setEditingPolicy(selectedPolicy)
    setFormModalOpen(true)
  }, [selectedPolicy])

  const handleActivateClick = useCallback(() => {
    if (!selectedPolicy) return
    setPolicyToActivate(selectedPolicy)
    setActivateConfirmOpen(true)
  }, [selectedPolicy])

  const handleActivateConfirm = () => {
    if (!policyToActivate) return
    activateMutation.mutate(
      { id: policyToActivate.id, cmd: { expectedVersion: policyToActivate.version } },
      {
        onSuccess: () => {
          notify.success('Policy activated')
          setActivateConfirmOpen(false)
          setPolicyToActivate(undefined)
        },
        onError: () => {
          notify.error('Failed to activate — policy may have been modified. Please refresh.')
          setActivateConfirmOpen(false)
        },
      },
    )
  }

  const handleFormSubmit = (data: PolicyFormData) => {
    if (editingPolicy) {
      updateMutation.mutate(
        {
          id: editingPolicy.id,
          cmd: {
            enabled: data.enabled,
            expectedVersion: editingPolicy.version,
            blueprint: data.blueprint,
          },
        },
        {
          onSuccess: () => {
            notify.success('Policy updated')
            setFormModalOpen(false)
          },
          onError: () => {
            notify.error('Failed to update — policy may have been modified. Please refresh.')
          },
        },
      )
    } else {
      createMutation.mutate(data, {
        onSuccess: () => {
          notify.success('Policy created')
          setFormModalOpen(false)
        },
        onError: () => {
          notify.error('Failed to create policy.')
        },
      })
    }
  }

  // --- Panel content ---

  const panelBody = useMemo(
    () => (
      <div className="space-y-4">
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
            <FilterIcon />
            {uiText.policies.panelTitle}
          </div>

          <ControlGroup label={uiText.policies.filterPolicyLabel}>
            <Select
              aria-label={uiText.policies.filterPolicyLabel}
              value={draftFilters.policyKey || ''}
              onChange={(e) =>
                setDraftFilterState((prev) => ({
                  key: filterDraftKey,
                  value: {
                    ...(prev.key === filterDraftKey ? prev.value : draftFilters),
                    policyKey: e.target.value,
                  },
                }))
              }
              className="w-full"
            >
              <option value="">{uiText.policies.filterPolicyAll}</option>
              {policyKeyOptions.map((key) => (
                <option key={key} value={key}>
                  {key}
                </option>
              ))}
            </Select>
          </ControlGroup>

          <ControlGroup label={uiText.policies.filterStatusLabel}>
            <Select
              aria-label={uiText.policies.filterStatusLabel}
              value={draftFilters.status}
              onChange={(e) =>
                setDraftFilterState((prev) => ({
                  key: filterDraftKey,
                  value: {
                    ...(prev.key === filterDraftKey ? prev.value : draftFilters),
                    status: e.target.value as PoliciesFilterDraft['status'],
                  },
                }))
              }
              className="w-full"
            >
              <option value="ALL">{uiText.policies.filterStatusAll}</option>
              {RUN_STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {runStatusLabel(s)}
                </option>
              ))}
            </Select>
          </ControlGroup>

          <ControlGroup label={uiText.policies.filterFromLabel}>
            <DateInput
              aria-label={uiText.policies.filterFromLabel}
              value={draftFilters.from}
              onChange={(e) =>
                setDraftFilterState((prev) => ({
                  key: filterDraftKey,
                  value: {
                    ...(prev.key === filterDraftKey ? prev.value : draftFilters),
                    from: e.target.value,
                  },
                }))
              }
              className="w-full"
            />
          </ControlGroup>

          <ControlGroup label={uiText.policies.filterToLabel}>
            <DateInput
              aria-label={uiText.policies.filterToLabel}
              value={draftFilters.to}
              onChange={(e) =>
                setDraftFilterState((prev) => ({
                  key: filterDraftKey,
                  value: {
                    ...(prev.key === filterDraftKey ? prev.value : draftFilters),
                    to: e.target.value,
                  },
                }))
              }
              className="w-full"
            />
          </ControlGroup>

          <div className="flex gap-2">
            <Button type="button" size="sm" onClick={handleApply} className="flex-1">
              <ApplyIcon className="mr-1.5" />
              {uiText.filters.apply}
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={handleReset} className="flex-1">
              <ResetIcon className="mr-1.5" />
              {uiText.filters.reset}
            </Button>
          </div>
        </div>

        <div className="border-t border-[var(--color-border)] pt-3">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">Summary</p>
          <div className="space-y-1 text-sm">
            <SummaryRow label={uiText.policies.summaryActivePolicies} value={activePolicyCount} />
            <SummaryRow label={uiText.policies.summaryRunsShown} value={runsShown} />
            <SummaryRow label={uiText.policies.summaryFailed} value={failedCount} highlight={failedCount > 0} />
          </div>
        </div>
      </div>
    ),
    [
      draftFilters,
      filterDraftKey,
      policyKeyOptions,
      activePolicyCount,
      runsShown,
      failedCount,
      handleApply,
      handleReset,
    ],
  )

  // --- Inspector ---

  const inspectorBody = useMemo(() => {
    // Run inspector (Runs tab)
    if (searchState.selectedRun !== undefined) {
      let body: React.ReactNode
      if (executionDetailQuery.isPending) {
        body = <p className="text-sm text-[var(--color-text-muted)]">{uiText.policies.inspectorLoading}</p>
      } else if (executionDetailQuery.isError || !executionDetailQuery.data) {
        body = <p className="text-sm text-[var(--color-status-bad)]">{uiText.policies.inspectorError}</p>
      } else {
        body = <RunInspector detail={executionDetailQuery.data} />
      }
      return { title: uiText.policies.runInspectorTitle, body }
    }

    // Policy inspector (Manage tab)
    if (selectedPolicy !== undefined) {
      return {
        title: uiText.policies.policyInspectorTitle,
        body: (
          <PolicyInspector
            policy={selectedPolicy}
            onEdit={handleEdit}
            onActivate={handleActivateClick}
            isActivating={activateMutation.isPending}
          />
        ),
      }
    }

    // Nothing selected — show hint
    const hint =
      searchState.tab === 'runs' ? uiText.policies.inspectorEmptyRuns : uiText.policies.inspectorEmptyManage
    return {
      title: uiText.policies.inspectorTitle,
      body: <p className="text-sm text-[var(--color-text-muted)]">{hint}</p>,
    }
  }, [
    searchState.tab,
    searchState.selectedRun,
    executionDetailQuery.isPending,
    executionDetailQuery.isError,
    executionDetailQuery.data,
    selectedPolicy,
    handleEdit,
    handleActivateClick,
    activateMutation.isPending,
  ])

  const panelContent = useMemo(
    () => ({ title: uiText.policies.title, body: panelBody }),
    [panelBody],
  )

  useShellRegionRegistration({
    panel: panelContent,
    inspector: inspectorBody,
  })

  // --- Render ---

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.policies.title} subtitle={uiText.policies.subtitle}>
        <div className="flex gap-1">
          <TabButton
            label={uiText.policies.tabRuns}
            active={searchState.tab === 'runs'}
            onClick={() => handleTabChange('runs')}
          />
          <TabButton
            label={uiText.policies.tabManage}
            active={searchState.tab === 'manage'}
            onClick={() => handleTabChange('manage')}
          />
        </div>
      </PageHeader>

      {searchState.tab === 'runs' ? (
        <RunsTab
          rows={executionsQuery.data?.items ?? []}
          isPending={executionsQuery.isPending}
          isError={executionsQuery.isError}
          selectedRunId={searchState.selectedRun}
          nextCursor={executionsQuery.data?.nextCursor ?? null}
          hasPrev={cursorStack.length > 0}
          onRowClick={handleRunClick}
          onNext={handleNext}
          onPrev={handlePrev}
        />
      ) : (
        <ManageTab
          rows={policiesQuery.data ?? []}
          isPending={policiesQuery.isPending}
          isError={policiesQuery.isError}
          selectedPolicyId={searchState.selectedPolicy}
          onRowClick={handlePolicyClick}
          onCreateNew={handleCreateNew}
        />
      )}

      <PolicyFormModal
        open={formModalOpen}
        onOpenChange={setFormModalOpen}
        policy={editingPolicy}
        onSubmit={handleFormSubmit}
        isPending={editingPolicy ? updateMutation.isPending : createMutation.isPending}
      />

      <ConfirmDialog
        open={activateConfirmOpen}
        title="Activate Policy"
        description={
          policyToActivate
            ? `Activate ${policyToActivate.policyKey} v${policyToActivate.version}? This will deactivate the current active version.`
            : ''
        }
        confirmLabel="Activate"
        onOpenChange={setActivateConfirmOpen}
        onConfirm={handleActivateConfirm}
      />
    </div>
  )
}

function TabButton({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
        active
          ? 'bg-[var(--color-brand-soft)] text-[var(--color-brand)]'
          : 'text-[var(--color-text-muted)] hover:text-[var(--color-text)]'
      }`}
    >
      {label}
    </button>
  )
}

function ControlGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block space-y-1">
      <span className="text-xs text-[var(--color-text-muted)]">{label}</span>
      {children}
    </label>
  )
}

function SummaryRow({ label, value, highlight = false }: { label: string; value: number; highlight?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-[var(--color-text-muted)]">{label}</span>
      <span className={`font-mono text-xs font-semibold ${highlight ? 'text-[var(--color-status-bad)]' : 'text-[var(--color-text)]'}`}>
        {value}
      </span>
    </div>
  )
}
