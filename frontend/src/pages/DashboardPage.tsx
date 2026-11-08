import { Anchor, Badge, Button, Group, Paper, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconAlertTriangle, IconCircleCheck, IconTopologyStar3, IconClockExclamation, IconFlame, IconPlus, IconTicket } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { IncidentSearch } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { IncidentTable } from '../components/IncidentTable';
import { QueryState } from '../components/QueryState';
import { OPEN_STATUSES } from '../lib/format';

function StatCard({ label, value, icon, color, to }: { label: string; value: number | undefined; icon: ReactNode; color: string; to: string }) {
  return (
    <Paper withBorder p="md" radius="md" component={Link} to={to} style={{ textDecoration: 'none', color: 'inherit' }}>
      <Group justify="space-between">
        <div>
          <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
            {label}
          </Text>
          <Text fw={700} fz={28}>
            {value ?? '–'}
          </Text>
        </div>
        <ThemeIcon color={color} variant="transparent" size={28}>
          {icon}
        </ThemeIcon>
      </Group>
    </Paper>
  );
}

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

      <SimpleGrid cols={{ base: 2, md: 4 }}>
        <StatCard label="Open" value={counts.open} color="blue" icon={<IconTicket size={22} stroke={1.6} />} to={link({})} />
        <StatCard label="High priority (P1/P2)" value={counts.critical} color="orange" icon={<IconFlame size={22} stroke={1.6} />} to={link({ priority: 'P1,P2' })} />
        <StatCard label="SLA at risk" value={counts.atRisk} color="yellow" icon={<IconClockExclamation size={22} stroke={1.6} />} to={link({ sla: 'AT_RISK' })} />
        <StatCard label="SLA breached" value={counts.breached} color="red" icon={<IconAlertTriangle size={22} stroke={1.6} />} to={link({ sla: 'BREACHED' })} />
      </SimpleGrid>

      {correlations.data?.map((c) => (
        <Paper
          key={c.rootServiceId}
          withBorder
          p="md"
          style={{ borderLeft: '3px solid var(--mantine-color-orange-6)' }}
        >
          <Group gap="xs" mb={4} wrap="nowrap">
            <IconTopologyStar3 size={18} stroke={1.6} color="var(--mantine-color-orange-6)" />
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
          <Text fw={600} mb="sm">
            Needs attention
          </Text>
          <QueryState query={attention}>
            <IncidentTable incidents={attention.data?.content ?? []} emptyText="No SLA at risk or breached right now" />
          </QueryState>
        </Paper>
      )}

      <Paper withBorder radius="md" p="md">
        <Group justify="space-between" mb="sm">
          <Text fw={600}>Recent incidents</Text>
          <Anchor component={Link} to="/incidents" size="sm">
            View all
          </Anchor>
        </Group>
        <QueryState query={recent}>
          <IncidentTable incidents={recent.data?.content ?? []} emptyText="No incidents yet" />
        </QueryState>
      </Paper>
    </Stack>
  );
}
