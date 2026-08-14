import type { ColumnDef } from '@shared/ui/DataTable'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { uiText } from '@shared/constants/uiText'
import type { WorkflowResponse } from '@platform/contracts/workflowSchemas'
import { formatWorkflowStatus, getWorkflowStatusTone } from '../lib/workflowsDisplay'

export const workflowColumns: ColumnDef<WorkflowResponse>[] = [
  {
    key: 'key',
    header: uiText.workflows.keyHeader,
    render: (row) => <span className="font-mono text-xs">{row.key}</span>,
  },
  {
    key: 'name',
    header: uiText.workflows.nameHeader,
    render: (row) => row.name,
  },
  {
    key: 'status',
    header: uiText.workflows.statusHeader,
    render: (row) => <StatusBadge label={formatWorkflowStatus(row.status)} tone={getWorkflowStatusTone(row.status)} />,
  },
  {
    key: 'version',
    header: uiText.workflows.versionHeader,
    render: (row) => row.versionNumber ?? '-',
  },
  {
    key: 'created',
    header: uiText.workflows.createdHeader,
    render: () => uiText.workflows.createdAtUnknown,
  },
]
