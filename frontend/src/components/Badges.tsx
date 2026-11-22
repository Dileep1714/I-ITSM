import { Box, Tooltip } from '@mantine/core';
import type { IncidentStatus, PriorityLevel, SlaStatus } from '../api/types';
import { humanize } from '../lib/format';
import classes from './Badges.module.css';

const PRIORITY_LABEL: Record<PriorityLevel, string> = { P1: 'Critical', P2: 'High', P3: 'Medium', P4: 'Low' };

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return (
    <span className={classes.status} data-status={status}>
      {humanize(status)}
    </span>
  );
}

export function PriorityBadge({ priority }: { priority: PriorityLevel | null | undefined }) {
  if (!priority) {
    return (
      <span className={classes.priority} data-level="unset">
        Unset
      </span>
    );
  }
  return (
    <Tooltip label={PRIORITY_LABEL[priority]}>
      <Box component="span" className={classes.priority} data-level={priority} aria-label={`${priority}, ${PRIORITY_LABEL[priority]}`}>
        {priority}
      </Box>
    </Tooltip>
  );
}

export function SlaBadge({ status }: { status: SlaStatus | null | undefined }) {
  if (!status) return null;
  return (
    <span className={classes.sla} data-sla={status}>
      {humanize(status)}
    </span>
  );
}
