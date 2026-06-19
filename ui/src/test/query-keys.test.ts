import { describe, expect, it } from 'vitest'
import { queryKeys } from '../platform/query/queryKeys'

describe('queryKeys list prefixes', () => {
  it('list-prefix helpers are a strict prefix of their filtered counterparts', () => {
    expect(queryKeys.workflows.lists()).toEqual(['workflows', 'list'])
    expect(queryKeys.workflows.list({})).toEqual(['workflows', 'list', {}])

    expect(queryKeys.workflowRuns.lists()).toEqual(['workflow-runs', 'list'])
    expect(queryKeys.workflowRuns.list({})).toEqual(['workflow-runs', 'list', {}])

    expect(queryKeys.processedCalls.lists()).toEqual(['processed-calls', 'list'])
    expect(queryKeys.processedCalls.list({})).toEqual(['processed-calls', 'list', {}])
  })

  it('forKey is a strict prefix of listForKey for the same workflow key', () => {
    expect(queryKeys.workflowRuns.forKey('wf-1')).toEqual(['workflow-runs', 'key', 'wf-1'])
    expect(queryKeys.workflowRuns.listForKey('wf-1', {})).toEqual(['workflow-runs', 'key', 'wf-1', {}])
  })
})
