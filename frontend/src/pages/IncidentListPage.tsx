import {
  Button,
  Group,
  MultiSelect,
  Pagination,
  Paper,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconFilterOff, IconPlus, IconSearch } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { IncidentSearch, IncidentStatus, PriorityLevel, SlaStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { IncidentTable } from '../components/IncidentTable';
import { QueryState } from '../components/QueryState';
import { humanize } from '../lib/format';

const STATUSES: IncidentStatus[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED'];
const PRIORITIES: PriorityLevel[] = ['P1', 'P2', 'P3', 'P4'];
const SLA: SlaStatus[] = ['NOT_STARTED', 'ON_TRACK', 'AT_RISK', 'PAUSED', 'BREACHED', 'MET'];
const PAGE_SIZE = 20;

/** Filter keys kept in the URL, e.g. /incidents?scope=ALL&sla=BREACHED. */
const KEYS = ['q', 'status', 'priority', 'sla', 'serviceId', 'categoryId', 'assignee', 'from', 'to', 'scope'] as const;

/** A yyyy-mm-dd date from the date input, as the instant of local midnight (optionally the next day). */
function localDayStart(date: string, plusDays = 0): string | undefined {
  if (!date) return undefined;
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d + plusDays).toISOString();
}

export function IncidentListPage() {
  const { user } = useAuth();
  const isEmployee = user?.role === 'EMPLOYEE';
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(0);

  const list = (key: string) => params.get(key)?.split(',').filter(Boolean) ?? [];
  const value = (key: string) => params.get(key) ?? '';
  const update = (key: (typeof KEYS)[number], v: string | string[] | null) => {
    const next = new URLSearchParams(params);
    const s = Array.isArray(v) ? v.join(',') : v ?? '';
    if (s) next.set(key, s);
    else next.delete(key);
    setParams(next, { replace: true });
    setPage(0);
  };

  // The search box updates the URL after a short pause, not on every key press.
  const [text, setText] = useState(value('q'));
  const [debouncedText] = useDebouncedValue(text, 350);
  useEffect(() => {
    if (debouncedText.trim() !== value('q')) update('q', debouncedText.trim());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedText]);

  const scope = isEmployee ? 'MINE' : value('scope') === 'MINE' ? 'MINE' : value('scope') === 'ALL' ? 'ALL' : user?.role === 'ADMINISTRATOR' ? 'ALL' : 'MINE';
  const assignee = value('assignee');

  const filters: IncidentSearch = useMemo(
    () => ({
      q: params.get('q') ?? undefined,
      status: list('status') as IncidentStatus[],
      priority: list('priority') as PriorityLevel[],
      sla: list('sla') as SlaStatus[],
      serviceId: params.get('serviceId') ? Number(params.get('serviceId')) : undefined,
      categoryId: params.get('categoryId') ? Number(params.get('categoryId')) : undefined,
      unassigned: assignee === 'none' ? true : undefined,
      assigneeId: assignee && assignee !== 'none' ? Number(assignee) : undefined,
      createdFrom: localDayStart(value('from')),
      createdTo: localDayStart(value('to'), 1),
      scope,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [params, scope],
  );

  const incidents = useQuery({
    queryKey: ['incidents', 'search', filters, page],
    queryFn: () => api.incidents.search(filters, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.catalog.categories });

  const hasFilters = KEYS.some((k) => k !== 'scope' && params.get(k));
  const clear = () => {
    setText('');
    setParams(params.get('scope') ? { scope: params.get('scope')! } : {}, { replace: true });
    setPage(0);
  };

  const heading = isEmployee ? 'My incidents' : scope === 'MINE' && user?.role === 'TECHNICIAN' ? 'Assigned to me' : 'All incidents';
  const data = incidents.data;

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>{heading}</Title>
        <Group>
          {!isEmployee && (
            <SegmentedControl
              value={scope}
              onChange={(v) => update('scope', v)}
              data={[
                { value: 'MINE', label: user?.role === 'TECHNICIAN' ? 'Assigned to me' : 'Mine' },
                { value: 'ALL', label: 'All incidents' },
              ]}
            />
          )}
          {isEmployee && (
            <Button component={Link} to="/incidents/new" leftSection={<IconPlus size={16} />}>
              Report incident
            </Button>
          )}
        </Group>
      </Group>

      <Paper withBorder p="md" radius="md">
        <Stack gap="sm">
          <TextInput
            placeholder="Search number, title or description"
            leftSection={<IconSearch size={16} />}
            value={text}
            onChange={(e) => setText(e.currentTarget.value)}
          />
          <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }} spacing="sm">
            <MultiSelect
              label="Status"
              data={STATUSES.map((s) => ({ value: s, label: humanize(s) }))}
              value={list('status')}
              onChange={(v) => update('status', v)}
              clearable
            />
            <MultiSelect label="Priority" data={PRIORITIES} value={list('priority')} onChange={(v) => update('priority', v)} clearable />
            <MultiSelect
              label="SLA"
              data={SLA.map((s) => ({ value: s, label: humanize(s) }))}
              value={list('sla')}
              onChange={(v) => update('sla', v)}
              clearable
            />
            <Select
              label="Service"
              data={(services.data ?? []).map((s) => ({ value: String(s.id), label: s.name }))}
              value={value('serviceId') || null}
              onChange={(v) => update('serviceId', v)}
              clearable
              searchable
            />
            <Select
              label="Category"
              data={(categories.data ?? []).map((c) => ({ value: String(c.id), label: c.name }))}
              value={value('categoryId') || null}
              onChange={(v) => update('categoryId', v)}
              clearable
              searchable
            />
            {!isEmployee && (
              <Select
                label="Assignee"
                data={[
                  { value: 'none', label: 'Unassigned' },
                  ...(user ? [{ value: String(user.id), label: 'Me' }] : []),
                ]}
                value={assignee || null}
                onChange={(v) => update('assignee', v)}
                clearable
              />
            )}
            <TextInput label="Created from" type="date" value={value('from')} onChange={(e) => update('from', e.currentTarget.value)} />
            <TextInput label="Created to" type="date" value={value('to')} onChange={(e) => update('to', e.currentTarget.value)} />
          </SimpleGrid>
          {hasFilters && (
            <Group justify="flex-end">
              <Button variant="subtle" size="xs" leftSection={<IconFilterOff size={14} />} onClick={clear}>
                Clear filters
              </Button>
            </Group>
          )}
        </Stack>
      </Paper>

      <Paper withBorder radius="md" p="xs" style={{ opacity: incidents.isPlaceholderData ? 0.6 : 1 }}>
        <QueryState query={incidents}>
          <IncidentTable
            incidents={data?.content ?? []}
            emptyText={hasFilters ? 'No incidents match the filters' : 'No incidents yet'}
          />
        </QueryState>
      </Paper>

      {data && (
        <Group justify="space-between">
          <Text size="xs" c="dimmed">
            {data.totalElements} incident{data.totalElements === 1 ? '' : 's'}
          </Text>
          {data.totalPages > 1 && <Pagination total={data.totalPages} value={page + 1} onChange={(p) => setPage(p - 1)} />}
        </Group>
      )}
    </Stack>
  );
}
