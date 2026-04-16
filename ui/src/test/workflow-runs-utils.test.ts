import { describe, expect, it } from 'vitest'
import {
  canCancelWorkflowRun,
  formatWorkflowRunReasonCode,
  formatWorkflowRunStatus,
  formatWorkflowRunStepStatus,
  getWorkflowRunStatusTone,
  getWorkflowRunStepStatusTone,
} from '../modules/workflow-runs/lib/workflowRunsDisplay'
import {
  DEFAULT_WORKFLOW_RUNS_PAGE,
  DEFAULT_WORKFLOW_RUNS_SIZE,
  createWorkflowRunsSearchParamsFromState,
  parseWorkflowRunsSearchParams,
  toWorkflowRunsDraftFilters,
} from '../modules/workflow-runs/lib/workflowRunsSearchParams'

describe('workflow runs display helpers', () => {
  it('maps run statuses and tones', () => {
    expect(formatWorkflowRunStatus('PENDING')).toBe('Pending')
    expect(formatWorkflowRunStatus('BLOCKED')).toBe('Blocked')
    expect(formatWorkflowRunStatus('DUPLICATE_IGNORED')).toBe('Duplicate Ignored')
    expect(formatWorkflowRunStatus('CANCELED')).toBe('Canceled')
    expect(formatWorkflowRunStatus('COMPLETED')).toBe('Completed')
    expect(formatWorkflowRunStatus('FAILED')).toBe('Failed')

    expect(getWorkflowRunStatusTone('PENDING')).toBe('warning')
    expect(getWorkflowRunStatusTone('BLOCKED')).toBe('warning')
    expect(getWorkflowRunStatusTone('DUPLICATE_IGNORED')).toBe('info')
    expect(getWorkflowRunStatusTone('CANCELED')).toBe('info')
    expect(getWorkflowRunStatusTone('COMPLETED')).toBe('success')
    expect(getWorkflowRunStatusTone('FAILED')).toBe('error')
  })

  it('maps step statuses and tones', () => {
    expect(formatWorkflowRunStepStatus('PENDING')).toBe('Pending')
    expect(formatWorkflowRunStepStatus('WAITING_DEPENDENCY')).toBe('Waiting Dependency')
    expect(formatWorkflowRunStepStatus('PROCESSING')).toBe('Processing')
    expect(formatWorkflowRunStepStatus('COMPLETED')).toBe('Completed')
    expect(formatWorkflowRunStepStatus('FAILED')).toBe('Failed')
    expect(formatWorkflowRunStepStatus('SKIPPED')).toBe('Skipped')

    expect(getWorkflowRunStepStatusTone('PENDING')).toBe('warning')
    expect(getWorkflowRunStepStatusTone('WAITING_DEPENDENCY')).toBe('info')
    expect(getWorkflowRunStepStatusTone('PROCESSING')).toBe('warning')
    expect(getWorkflowRunStepStatusTone('COMPLETED')).toBe('success')
    expect(getWorkflowRunStepStatusTone('FAILED')).toBe('error')
    expect(getWorkflowRunStepStatusTone('SKIPPED')).toBe('info')
  })

  it('falls back safely for null values', () => {
    expect(formatWorkflowRunStatus(null)).toBe('Unknown')
    expect(getWorkflowRunStatusTone(null)).toBe('info')
    expect(formatWorkflowRunStepStatus(null)).toBe('Unknown')
    expect(getWorkflowRunStepStatusTone(null)).toBe('info')
  })

  it('normalizes reason code values', () => {
    expect(formatWorkflowRunReasonCode('FAILED_TIMEOUT')).toBe('FAILED_TIMEOUT')
    expect(formatWorkflowRunReasonCode('   ')).toBe('-')
    expect(formatWorkflowRunReasonCode(null)).toBe('-')
  })

  it('gates cancel action to pending and blocked runs', () => {
    expect(canCancelWorkflowRun('PENDING')).toBe(true)
    expect(canCancelWorkflowRun('BLOCKED')).toBe(true)
    expect(canCancelWorkflowRun('FAILED')).toBe(false)
    expect(canCancelWorkflowRun('COMPLETED')).toBe(false)
    expect(canCancelWorkflowRun('CANCELED')).toBe(false)
    expect(canCancelWorkflowRun('DUPLICATE_IGNORED')).toBe(false)
    expect(canCancelWorkflowRun(null)).toBe(false)
  })
})

describe('workflow runs search params helpers', () => {
  it('parses defaults and normalizes invalid values', () => {
    const parsed = parseWorkflowRunsSearchParams(new URLSearchParams('status=INVALID&page=-5&size=0&selectedRunId=abc'))
    expect(parsed).toEqual({
      status: undefined,
      page: 0,
      size: 1,
      selectedRunId: undefined,
    })
  })

  it('serializes only non-default values', () => {
    const serialized = createWorkflowRunsSearchParamsFromState({
      status: 'FAILED',
      page: 2,
      size: 50,
      selectedRunId: 91,
    })
    expect(serialized.get('status')).toBe('FAILED')
    expect(serialized.get('page')).toBe('2')
    expect(serialized.get('size')).toBe('50')
    expect(serialized.get('selectedRunId')).toBe('91')
  })

  it('omits defaults when serializing', () => {
    const serialized = createWorkflowRunsSearchParamsFromState({
      page: DEFAULT_WORKFLOW_RUNS_PAGE,
      size: DEFAULT_WORKFLOW_RUNS_SIZE,
      status: undefined,
      selectedRunId: undefined,
    })
    expect(serialized.toString()).toBe('')
  })

  it('builds draft filters for apply/reset behavior', () => {
    expect(
      toWorkflowRunsDraftFilters({
        page: 0,
        size: 20,
      }),
    ).toEqual({
      status: 'ALL',
    })
  })
})
