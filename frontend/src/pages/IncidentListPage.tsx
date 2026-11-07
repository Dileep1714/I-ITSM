import { Button, Group, MultiSelect, Paper, Select, Stack, Text, TextInput, Title } from '@mantine/core';
import { IconPlus, IconSearch } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { IncidentStatus, PriorityLevel, SlaStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { IncidentTable } from '../components/IncidentTable';
import { QueryState } from '../components/QueryState';
import { humanize } from '../lib/format';

const STATUSES: IncidentStatus[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED'];
const PRIORITIES: PriorityLevel[] = ['P1', 'P2', 'P3', 'P4'];
const SLA: SlaStatus[] = ['NOT_STARTED', 'ON_TRACK', 'AT_RISK', 'PAUSED', 'BREACHED', 'MET'];

export function IncidentListPage() {
  const { user } = useAuth();
  const incidents = useQuery({ queryKey: ['incidents', 'list'], queryFn: () => api.incidents.list(0, 200) });

  const [text, setText] = useState('');
  const [statuses, setStatuses] = useState<string[]>([]);
  const [priorities, setPriorities] = useState<string[]>([]);
  const [sla, setSla] = useState<string | null>(null);
  const [service, setService] = useState<string | null>(null);

  const all = useMemo(() => incidents.data?.content ?? [], [incidents.data]);
  const services = useMemo(() => [...new Set(all.map((i) => i.serviceName))].sort(), [all]);

  const filtered = useMemo(() => {
    const q = text.trim().toLowerCase();
    return all.filter(
      (i) =>
        (!q || i.number.toLowerCase().includes(q) || i.title.toLowerCase().includes(q) ||
          (i.assigneeName ?? '').toLowerCase().includes(q) || (i.categoryName ?? '').toLowerCase().includes(q)) &&
        (statuses.length === 0 || statuses.includes(i.status)) &&
        (priorities.length === 0 || (i.priority !== null && priorities.includes(i.priority))) &&
        (!sla || i.slaStatus === sla) &&
        (!service || i.serviceName === service),
    );
  }, [all, text, statuses, priorities, sla, service]);

  const heading =
    user?.role === 'EMPLOYEE' ? 'My incidents' : user?.role === 'TECHNICIAN' ? 'Assigned to me' : 'All incidents';

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>{heading}</Title>
        {user?.role === 'EMPLOYEE' && (
          <Button component={Link} to="/incidents/new" leftSection={<IconPlus size={16} />}>
            Report incident
          </Button>
        )}
      </Group>
      <Paper withBorder p="md" radius="md">
        <Group grow align="flex-end" wrap="wrap">
          <TextInput
            label="Search"
            placeholder="Number, title, assignee, category"
            leftSection={<IconSearch size={16} />}
            value={text}
            onChange={(e) => setText(e.currentTarget.value)}
          />
          <MultiSelect label="Status" data={STATUSES.map((s) => ({ value: s, label: humanize(s) }))} value={statuses} onChange={setStatuses} clearable />
          <MultiSelect label="Priority" data={PRIORITIES} value={priorities} onChange={setPriorities} clearable />
          <Select label="SLA" data={SLA.map((s) => ({ value: s, label: humanize(s) }))} value={sla} onChange={setSla} clearable />
          <Select label="Service" data={services} value={service} onChange={setService} clearable searchable />
        </Group>
      </Paper>
      <Paper withBorder radius="md" p="xs">
        <QueryState query={incidents}>
          <IncidentTable incidents={filtered} emptyText={all.length === 0 ? 'No incidents yet' : 'No incidents match the filters'} />
        </QueryState>
      </Paper>
      {incidents.data && (
        <Text size="xs" c="dimmed">
          Showing {filtered.length} of {incidents.data.totalElements}
        </Text>
      )}
    </Stack>
  );
}
