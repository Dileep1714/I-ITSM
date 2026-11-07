import { Anchor, Button, Group, Paper, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconAlertTriangle, IconCircleCheck, IconClockExclamation, IconFlame, IconPlus, IconTicket } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { IncidentTable } from '../components/IncidentTable';
import { QueryState } from '../components/QueryState';
import { OPEN_STATUSES } from '../lib/format';

function StatCard({ label, value, icon, color }: { label: string; value: number; icon: ReactNode; color: string }) {
  return (
    <Paper withBorder p="md" radius="md">
      <Group justify="space-between">
        <div>
          <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
            {label}
          </Text>
          <Text fw={700} fz={28}>
            {value}
          </Text>
        </div>
        <ThemeIcon color={color} variant="light" size={44} radius="md">
          {icon}
        </ThemeIcon>
      </Group>
    </Paper>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  const incidents = useQuery({ queryKey: ['incidents', 'list'], queryFn: () => api.incidents.list(0, 200) });
  const all = incidents.data?.content ?? [];

  const open = all.filter((i) => OPEN_STATUSES.includes(i.status));
  const atRisk = open.filter((i) => i.slaStatus === 'AT_RISK');
  const breached = open.filter((i) => i.slaStatus === 'BREACHED');
  const critical = open.filter((i) => i.priority === 'P1' || i.priority === 'P2');
  const awaitingMe =
    user?.role === 'EMPLOYEE' ? all.filter((i) => i.status === 'RESOLVED' || i.status === 'ON_HOLD') : [];

  const attention = [...breached, ...atRisk, ...critical.filter((i) => !atRisk.includes(i) && !breached.includes(i))];

  return (
    <Stack>
      <Group justify="space-between">
        <div>
          <Title order={2}>Welcome, {user?.fullName.split(' ')[0]}</Title>
          <Text c="dimmed" size="sm">
            {user?.role === 'EMPLOYEE' && 'Track the incidents you have reported.'}
            {user?.role === 'TECHNICIAN' && 'Your queue of assigned incidents.'}
            {user?.role === 'ADMINISTRATOR' && 'Operational overview of all incidents.'}
          </Text>
        </div>
        {user?.role === 'EMPLOYEE' && (
          <Button component={Link} to="/incidents/new" leftSection={<IconPlus size={16} />}>
            Report incident
          </Button>
        )}
      </Group>

      <QueryState query={incidents}>
        <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>
          <StatCard label="Open" value={open.length} color="blue" icon={<IconTicket size={24} />} />
          <StatCard label="High priority (P1/P2)" value={critical.length} color="orange" icon={<IconFlame size={24} />} />
          <StatCard label="SLA at risk" value={atRisk.length} color="yellow" icon={<IconClockExclamation size={24} />} />
          <StatCard label="SLA breached" value={breached.length} color="red" icon={<IconAlertTriangle size={24} />} />
        </SimpleGrid>

        {awaitingMe.length > 0 && (
          <Paper withBorder radius="md" p="md">
            <Group gap="xs" mb="sm">
              <IconCircleCheck size={18} />
              <Text fw={600}>Waiting for your response</Text>
            </Group>
            <IncidentTable incidents={awaitingMe} />
          </Paper>
        )}

        {user?.role !== 'EMPLOYEE' && (
          <Paper withBorder radius="md" p="md">
            <Text fw={600} mb="sm">
              Needs attention
            </Text>
            <IncidentTable incidents={attention.slice(0, 10)} emptyText="Nothing urgent right now" />
          </Paper>
        )}

        <Paper withBorder radius="md" p="md">
          <Group justify="space-between" mb="sm">
            <Text fw={600}>Recent incidents</Text>
            <Anchor component={Link} to="/incidents" size="sm">
              View all
            </Anchor>
          </Group>
          <IncidentTable incidents={all.slice(0, 8)} emptyText="No incidents yet" />
        </Paper>
      </QueryState>
    </Stack>
  );
}
