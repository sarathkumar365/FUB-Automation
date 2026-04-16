import { describe, expect, it } from 'vitest'
import {
  canActivateWorkflow,
  canArchiveWorkflow,
  canDeactivateWorkflow,
  canEditWorkflow,
  canValidateWorkflow,
  formatWorkflowStatus,
  getWorkflowStatusTone,
} from '../modules/workflows/lib/workflowsDisplay'
import {
  DEFAULT_WORKFLOW_DETAIL_RUNS_PAGE,
  DEFAULT_WORKFLOW_DETAIL_RUNS_SIZE,
  DEFAULT_WORKFLOW_PAGE,
  DEFAULT_WORKFLOW_SIZE,
  createWorkflowDetailSearchParamsFromState,
  createWorkflowsSearchParamsFromState,
  parseWorkflowDetailSearchParams,
  parseWorkflowsSearchParams,
  toWorkflowDetailRunsDraftFilters,
  toWorkflowsDraftFilters,
} from '../modules/workflows/lib/workflowsSearchParams'

describe('workflow display helpers', () => {
  it('maps workflow statuses to user-facing labels and tones', () => {
    expect(formatWorkflowStatus('DRAFT')).toBe('Draft')
    expect(formatWorkflowStatus('ACTIVE')).toBe('Active')
    expect(formatWorkflowStatus('INACTIVE')).toBe('Inactive')
    expect(formatWorkflowStatus('ARCHIVED')).toBe('Archived')

    expect(getWorkflowStatusTone('DRAFT')).toBe('warning')
    expect(getWorkflowStatusTone('ACTIVE')).toBe('success')
    expect(getWorkflowStatusTone('INACTIVE')).toBe('info')
    expect(getWorkflowStatusTone('ARCHIVED')).toBe('error')
  })

  it('falls back safely for null status', () => {
    expect(formatWorkflowStatus(null)).toBe('Unknown')
    expect(getWorkflowStatusTone(null)).toBe('info')
  })

  it('applies lifecycle action gating by status', () => {
    expect(canEditWorkflow('DRAFT')).toBe(true)
    expect(canEditWorkflow('ACTIVE')).toBe(false)
    expect(canValidateWorkflow('ACTIVE')).toBe(true)
    expect(canActivateWorkflow('INACTIVE')).toBe(true)
    expect(canDeactivateWorkflow('ACTIVE')).toBe(true)
    expect(canArchiveWorkflow('ARCHIVED')).toBe(false)
    expect(canArchiveWorkflow(null)).toBe(false)
  })
})

describe('workflow search params helpers', () => {
  it('parses default state and normalizes invalid values', () => {
    const params = new URLSearchParams('status=INVALID&page=-4&size=0&selectedKey=   ')
    const parsed = parseWorkflowsSearchParams(params)

    expect(parsed).toEqual({
      status: undefined,
      page: 0,
      size: 1,
      selectedKey: undefined,
    })
  })

  it('serializes only non-default values', () => {
    const params = createWorkflowsSearchParamsFromState({
      status: 'ACTIVE',
      page: 2,
      size: 50,
      selectedKey: 'wf_a',
    })

    expect(params.get('status')).toBe('ACTIVE')
    expect(params.get('page')).toBe('2')
    expect(params.get('size')).toBe('50')
    expect(params.get('selectedKey')).toBe('wf_a')
  })

  it('omits default values from search params', () => {
    const params = createWorkflowsSearchParamsFromState({
      page: DEFAULT_WORKFLOW_PAGE,
      size: DEFAULT_WORKFLOW_SIZE,
      status: undefined,
      selectedKey: undefined,
    })

    expect(params.toString()).toBe('')
  })

  it('builds draft filters for apply/reset pattern', () => {
    expect(
      toWorkflowsDraftFilters({
        page: 0,
        size: 20,
      }),
    ).toEqual({
      status: 'ALL',
    })
  })

  it('parses and serializes workflow detail search state', () => {
    const parsed = parseWorkflowDetailSearchParams(
      new URLSearchParams('tab=runs&runStatus=FAILED&runPage=2&runSize=50'),
    )
    expect(parsed).toEqual({
      tab: 'runs',
      runStatus: 'FAILED',
      runPage: 2,
      runSize: 50,
    })

    const serialized = createWorkflowDetailSearchParamsFromState({
      tab: 'runs',
      runStatus: 'FAILED',
      runPage: 2,
      runSize: 50,
    })
    expect(serialized.get('tab')).toBe('runs')
    expect(serialized.get('runStatus')).toBe('FAILED')
    expect(serialized.get('runPage')).toBe('2')
    expect(serialized.get('runSize')).toBe('50')
  })

  it('normalizes invalid workflow detail run filters to defaults', () => {
    const parsed = parseWorkflowDetailSearchParams(new URLSearchParams('tab=runs&runStatus=BAD&runPage=-9&runSize=0'))
    expect(parsed).toEqual({
      tab: 'runs',
      runStatus: undefined,
      runPage: 0,
      runSize: 1,
    })
  })

  it('omits default workflow detail run state in serialization', () => {
    const serialized = createWorkflowDetailSearchParamsFromState({
      tab: 'definition',
      runStatus: undefined,
      runPage: DEFAULT_WORKFLOW_DETAIL_RUNS_PAGE,
      runSize: DEFAULT_WORKFLOW_DETAIL_RUNS_SIZE,
    })
    expect(serialized.toString()).toBe('')
  })

  it('builds workflow-detail runs draft filters for apply/reset pattern', () => {
    expect(
      toWorkflowDetailRunsDraftFilters({
        tab: 'runs',
        runPage: 0,
        runSize: 20,
      }),
    ).toEqual({
      status: 'ALL',
    })
  })
})
