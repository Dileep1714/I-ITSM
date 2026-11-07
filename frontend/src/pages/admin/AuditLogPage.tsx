import { Badge, Pagination, Paper, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { QueryState } from '../../components/QueryState';
import { formatDateTime, humanize } from '../../lib/format';

export function AuditLogPage() {
  const [page, setPage] = useState(0);
  const audit = useQuery({ queryKey: ['admin', 'audit', page], queryFn: () => api.admin.audit(page, 50) });

  return (
    <Stack>
      <Title order={2}>Audit log</Title>
      <Paper withBorder radius="md" p="xs">
        <QueryState query={audit}>
          <Table.ScrollContainer minWidth={900}>
            <Table verticalSpacing="xs" fz="sm" striped>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>When</Table.Th>
                  <Table.Th>Incident</Table.Th>
                  <Table.Th>Action</Table.Th>
                  <Table.Th>Status change</Table.Th>
                  <Table.Th>Description</Table.Th>
                  <Table.Th>By</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {audit.data?.content.map((e) => (
                  <Table.Tr key={e.id}>
                    <Table.Td style={{ whiteSpace: 'nowrap' }}>{formatDateTime(e.occurredAt)}</Table.Td>
                    <Table.Td ff="monospace">{e.incidentNumber}</Table.Td>
                    <Table.Td>
                      <Badge variant="light" size="sm">
                        {humanize(e.action)}
                      </Badge>
                    </Table.Td>
                    <Table.Td style={{ whiteSpace: 'nowrap' }}>
                      {e.previousStatus !== e.newStatus && e.newStatus
                        ? `${humanize(e.previousStatus)} → ${humanize(e.newStatus)}`
                        : '—'}
                    </Table.Td>
                    <Table.Td>{e.description}</Table.Td>
                    <Table.Td>{e.actorName ?? <Text span c="dimmed" size="sm">System</Text>}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          {audit.data?.content.length === 0 && (
            <Text c="dimmed" ta="center" py="lg">
              No audit entries yet.
            </Text>
          )}
        </QueryState>
      </Paper>
      {audit.data && audit.data.totalPages > 1 && (
        <Pagination total={audit.data.totalPages} value={page + 1} onChange={(p) => setPage(p - 1)} />
      )}
    </Stack>
  );
}
