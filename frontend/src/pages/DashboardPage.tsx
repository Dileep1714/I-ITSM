import { Anchor, Badge, Button, Group, Paper, Stack, Text, Title } from '@mantine/core';
import { IconCircleCheck, IconPlus, IconTopologyStar3 } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { IncidentSearch } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { QueueLedger, SectionHead } from '../components/Docket';
import { IncidentTable } from '../components/IncidentTable';
import { QueryState } from '../components/QueryState';
import { LikelyToBreach } from '../components/SlaForecast';
import { OPEN_STATUSES } from '../lib/format';

/** Counts one search on the server (only the total is used). */
function useCount(filters: IncidentSearch) {
  return useQuery({
    queryKey: ['incidents', 'count', filters],
    queryFn: () => api.incidents.search(filters, 0, 1),
    select: (page) => page.totalElements,
  }).data;
}

export function DashboardPage() {
  const { user } = useAuth();
  const isEmployee = user?.role === 'EMPLOYEE';

  // Technicians see their own queue, administrators everything, employees their reports (enforced by the server).
  const scope = user?.role === 'ADMINISTRATOR' ? 'ALL' : 'MINE';
  const open: IncidentSearch = { status: OPEN_STATUSES, scope };
  const counts = {
    open: useCount(open),
    critical: useCount({ ...open, priority: ['P1', 'P2'] }),
    atRisk: useCount({ ...open, sla: ['AT_RISK'] }),
    breached: useCount({ ...open, sla: ['BREACHED'] }),
  };
  const link = (extra: Record<string, string>) =>
    `/incidents?${new URLSearchParams({ status: OPEN_STATUSES.join(','), scope, ...extra })}`;

  const recent = useQuery({ queryKey: ['incidents', 'recent'], queryFn: () => api.incidents.list(0, 8) });
  const attention = useQuery({
    queryKey: ['incidents', 'attention', scope],
    queryFn: () => api.incidents.search({ ...open, sla: ['BREACHED', 'AT_RISK'] }, 0, 10, 'priority.level,asc'),
    enabled: !isEmployee,
  });
  const correlations = useQuery({
    queryKey: ['correlations'],
    queryFn: api.intelligence.correlations,
    enabled: !isEmployee,
    refetchInterval: 60_000,
  });
  const awaiting = useQuery({
    queryKey: ['incidents', 'awaiting-me'],
    queryFn: () => api.incidents.search({ status: ['RESOLVED', 'ON_HOLD'] }, 0, 10),
    enabled: isEmployee,
  });

  return (
    <Stack>
      <Group justify="space-between">
        <div>
          <Title order={2}>Welcome, {user?.fullName.split(' ')[0]}</Title>
          <Text c="dimmed" size="sm">
            {isEmployee && 'Track the incidents you have reported.'}
            {user?.role === 'TECHNICIAN' && 'Your queue of assigned incidents.'}
            {user?.role === 'ADMINISTRATOR' && 'Operational overview of all incidents.'}
          </Text>
        </div>
        {isEmployee && (
          <Button component={Link} to="/incidents/new" leftSection={<IconPlus size={16} />}>
            Report incident
          </Button>
        )}
      </Group>

      <QueueLedger
        title={isEmployee ? 'Your reports' : user?.role === 'ADMINISTRATOR' ? 'Service desk' : 'Your queue'}
        cells={[
          { label: 'Open', value: counts.open, to: link({}), foot: isEmployee ? 'reported, not yet closed' : 'not yet closed' },
          { label: 'High priority', value: counts.critical, to: link({ priority: 'P1,P2' }), foot: 'P1 and P2' },
          { label: 'SLA at risk', value: counts.atRisk, to: link({ sla: 'AT_RISK' }), alarm: 'risk', foot: 'deadline close' },
          { label: 'SLA breached', value: counts.breached, to: link({ sla: 'BREACHED' }), alarm: 'breach', foot: 'past the deadline' },
        ]}
      />

      {!isEmployee && <LikelyToBreach />}

      {correlations.data?.map((c) => (
        <Paper
          key={c.rootServiceId}
          withBorder
          p="md"
          style={{ borderColor: 'var(--app-stamp)' }}
        >
          <Group gap="xs" mb={4} wrap="nowrap">
            <IconTopologyStar3 size={18} stroke={1.6} color="var(--app-stamp)" />
            <Text fw={600} size="sm">
              Possible common cause: {c.rootServiceName}
            </Text>
          </Group>
          <Text size="sm" c="dimmed">
            {c.explanation}
          </Text>
          <Group gap={6} mt="sm">
            {c.incidents.map((i) => (
              <Badge
                key={i.id}
                component={Link}
                to={`/incidents/${i.id}`}
                variant="default"
                ff="monospace"
                style={{ cursor: 'pointer', fontWeight: 400 }}
              >
                {i.number} · {i.serviceName}
              </Badge>
            ))}
          </Group>
        </Paper>
      ))}

      {isEmployee && (awaiting.data?.content.length ?? 0) > 0 && (
        <Paper withBorder radius="md" p="md">
          <Group gap="xs" mb="sm">
            <IconCircleCheck size={18} />
            <Text fw={600}>Waiting for your response</Text>
          </Group>
          <IncidentTable incidents={awaiting.data?.content ?? []} />
        </Paper>
      )}

      {!isEmployee && (
        <Paper withBorder radius="md" p="md">
          <SectionHead title="Needs attention" count={attention.data?.totalElements} />
          <QueryState query={attention}>
            <IncidentTable
              incidents={attention.data?.content ?? []}
              emptyStamp="All clear"
              emptyText="Nothing at risk or past its deadline right now."
            />
          </QueryState>
        </Paper>
      )}

      <Paper withBorder radius="md" p="md">
        <SectionHead
          title="Recent incidents"
          count={recent.data?.totalElements}
          action={
            <Anchor component={Link} to="/incidents" size="sm">
              View all
            </Anchor>
          }
        />
        <QueryState query={recent}>
          <IncidentTable
            incidents={recent.data?.content ?? []}
            emptyStamp="No dockets"
            emptyText={isEmployee ? 'You have not reported anything yet.' : 'No incidents have been reported yet.'}
            emptyAction={
              isEmployee ? (
                <Button component={Link} to="/incidents/new" leftSection={<IconPlus size={16} />}>
                  Report an incident
                </Button>
              ) : undefined
            }
          />
        </QueryState>
      </Paper>
    </Stack>
  );
}
