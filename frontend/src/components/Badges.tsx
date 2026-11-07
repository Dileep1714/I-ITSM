import { Badge, Tooltip } from '@mantine/core';
import type { IncidentStatus, PriorityLevel, SlaStatus } from '../api/types';
import { humanize, priorityColor, slaColor, statusColor } from '../lib/format';

const PRIORITY_LABEL: Record<PriorityLevel, string> = { P1: 'Critical', P2: 'High', P3: 'Medium', P4: 'Low' };

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return (
    <Badge color={statusColor[status]} variant="light">
      {humanize(status)}
    </Badge>
  );
}

export function PriorityBadge({ priority }: { priority: PriorityLevel | null | undefined }) {
  if (!priority) {
    return (
      <Badge color="gray" variant="outline">
        Unset
      </Badge>
    );
  }
  return (
    <Tooltip label={PRIORITY_LABEL[priority]}>
      <Badge color={priorityColor[priority]} variant="filled">
        {priority}
      </Badge>
    </Tooltip>
  );
}

export function SlaBadge({ status }: { status: SlaStatus | null | undefined }) {
  if (!status) return null;
  return (
    <Badge color={slaColor[status]} variant="dot">
      {humanize(status)}
    </Badge>
  );
}
