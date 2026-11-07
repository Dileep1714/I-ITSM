import { Alert, Badge, Button, Group, Pagination, Paper, Stack, Table, Text, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPlayerPlay } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { PriorityBadge } from '../../components/Badges';
import { QueryState } from '../../components/QueryState';
import { errorMessage, formatDateTime, fromNow, humanize } from '../../lib/format';

export function SlaViolationsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const violations = useQuery({ queryKey: ['admin', 'sla-violations', page], queryFn: () => api.admin.slaViolations(page, 50) });

  const runMonitor = useMutation({
    mutationFn: api.admin.runSlaMonitor,
    onSuccess: (r) => {
      queryClient.invalidateQueries();
      notifications.show({
        color: 'blue',
        title: 'SLA monitor ran',
        message: `Checked ${r.checked} · ${r.warnings} warnings · ${r.breaches} breaches · ${r.escalations} escalations · ${r.assigned} assigned · ${r.autoVerified} auto-verified`,
      });
    },
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>SLA violations</Title>
        <Button leftSection={<IconPlayerPlay size={16} />} variant="light" loading={runMonitor.isPending} onClick={() => runMonitor.mutate()}>
          Run SLA monitor now
        </Button>
      </Group>
      {runMonitor.error && <Alert color="red">{errorMessage(runMonitor.error)}</Alert>}
      <Text size="sm" c="dimmed">
        The monitor also runs automatically every minute: it raises warnings, records breaches and escalates incidents.
      </Text>
      <Paper withBorder radius="md" p="xs">
        <QueryState query={violations}>
          <Table.ScrollContainer minWidth={800}>
            <Table highlightOnHover verticalSpacing="sm" fz="sm">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Incident</Table.Th>
                  <Table.Th>Missed deadline</Table.Th>
                  <Table.Th>Priority</Table.Th>
                  <Table.Th>Service</Table.Th>
                  <Table.Th>Deadline</Table.Th>
                  <Table.Th>Detected</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {violations.data?.content.map((v) => (
                  <Table.Tr key={v.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/incidents/${v.incidentId}`)}>
                    <Table.Td>
                      <Text size="sm" ff="monospace">
                        {v.incidentNumber}
                      </Text>
                      <Text size="xs" c="dimmed" lineClamp={1}>
                        {v.incidentTitle}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Badge color={v.kind === 'RESOLUTION' ? 'red' : 'orange'} variant="light">
                        {humanize(v.kind)}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      <PriorityBadge priority={v.priority} />
                    </Table.Td>
                    <Table.Td>{v.serviceName}</Table.Td>
                    <Table.Td>{formatDateTime(v.deadline)}</Table.Td>
                    <Table.Td>{fromNow(v.detectedAt)}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          {violations.data?.content.length === 0 && (
            <Text c="dimmed" ta="center" py="lg">
              No SLA violations recorded.
            </Text>
          )}
        </QueryState>
      </Paper>
      {violations.data && violations.data.totalPages > 1 && (
        <Pagination total={violations.data.totalPages} value={page + 1} onChange={(p) => setPage(p - 1)} />
      )}
    </Stack>
  );
}
