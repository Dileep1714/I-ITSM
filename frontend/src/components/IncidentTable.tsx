import { Table, Text } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import type { IncidentSummary } from '../api/types';
import { fromNow } from '../lib/format';
import { PriorityBadge, SlaBadge, StatusBadge } from './Badges';

export function IncidentTable({ incidents, emptyText = 'No incidents' }: { incidents: IncidentSummary[]; emptyText?: string }) {
  const navigate = useNavigate();

  if (incidents.length === 0) {
    return (
      <Text c="dimmed" ta="center" py="xl">
        {emptyText}
      </Text>
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
                <Text size="sm" ff="monospace">
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
