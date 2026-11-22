import { Group, Stack, Table, Text, UnstyledButton } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useNavigate } from 'react-router-dom';
import type { IncidentSummary } from '../api/types';
import { fromNow } from '../lib/format';
import type { ReactNode } from 'react';
import { PriorityBadge, SlaBadge, StatusBadge } from './Badges';
import { MiniStrip, StampedEmpty } from './Docket';
import classes from './IncidentTable.module.css';

/** Incident list: a full table on tablets and desktops, one tappable card per incident on phones. */
export function IncidentTable({
  incidents,
  emptyText = 'No incidents',
  emptyStamp = 'No dockets',
  emptyAction,
}: {
  incidents: IncidentSummary[];
  emptyText?: string;
  emptyStamp?: string;
  emptyAction?: ReactNode;
}) {
  const navigate = useNavigate();
  const phone = useMediaQuery('(max-width: 48em)'); // below Mantine's "sm" breakpoint

  if (incidents.length === 0) {
    return <StampedEmpty stamp={emptyStamp} text={emptyText} action={emptyAction} />;
  }

  if (phone) {
    return (
      <div className={classes.cards}>
        {incidents.map((i) => (
          <UnstyledButton key={i.id} className={classes.card} onClick={() => navigate(`/incidents/${i.id}`)}>
            <Stack gap={6}>
              <Group justify="space-between" wrap="nowrap" gap="xs">
                <Text size="sm" ff="monospace" fw={700} c="var(--app-ink-carbon)">
                  {i.number}
                </Text>
                <Text size="xs" c="dimmed">
                  {fromNow(i.createdAt)}
                </Text>
              </Group>
              <Text size="sm" fw={600} lineClamp={2}>
                {i.title}
              </Text>
              <Group gap={6}>
                <PriorityBadge priority={i.priority} />
                <StatusBadge status={i.status} />
                <SlaBadge status={i.slaStatus} />
              </Group>
              <MiniStrip status={i.status} />
              <Text size="xs" c="dimmed" lineClamp={1}>
                {i.serviceName} · {i.assigneeName ?? 'Unassigned'}
              </Text>
            </Stack>
          </UnstyledButton>
        ))}
      </div>
    );
  }

  return (
    <Table.ScrollContainer minWidth={800}>
      <Table highlightOnHover verticalSpacing="sm">
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Number</Table.Th>
            <Table.Th>Title</Table.Th>
            <Table.Th>Priority</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th>SLA</Table.Th>
            <Table.Th>Service</Table.Th>
            <Table.Th>Assignee</Table.Th>
            <Table.Th>Created</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {incidents.map((i) => (
            <Table.Tr key={i.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/incidents/${i.id}`)}>
              <Table.Td>
                <Text size="sm" ff="monospace" fw={700} c="var(--app-ink-carbon)" style={{ whiteSpace: 'nowrap' }}>
                  {i.number}
                </Text>
              </Table.Td>
              <Table.Td>
                <Text size="sm" fw={500} lineClamp={1}>
                  {i.title}
                </Text>
                <Text size="xs" c="dimmed">
                  {i.categoryName ?? 'Uncategorised'} · by {i.reporterName}
                </Text>
              </Table.Td>
              <Table.Td>
                <PriorityBadge priority={i.priority} />
              </Table.Td>
              <Table.Td>
                <StatusBadge status={i.status} />
                <div>
                  <MiniStrip status={i.status} />
                </div>
              </Table.Td>
              <Table.Td>
                <SlaBadge status={i.slaStatus} />
              </Table.Td>
              <Table.Td>
                <Text size="sm">{i.serviceName}</Text>
              </Table.Td>
              <Table.Td>
                <Text size="sm" c={i.assigneeName ? undefined : 'dimmed'}>
                  {i.assigneeName ?? 'Unassigned'}
                </Text>
              </Table.Td>
              <Table.Td>
                <Text size="sm" c="dimmed">
                  {fromNow(i.createdAt)}
                </Text>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  );
}
