import { Badge, Button, Group, Modal, NumberInput, Paper, Select, Slider, Stack, Table, Text, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { IconPencil, IconPlus, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import type { PriorityLevel, SlaPolicyView } from '../../api/types';
import { PriorityBadge } from '../../components/Badges';
import { QueryState } from '../../components/QueryState';
import { errorMessage, formatMinutes } from '../../lib/format';

const PRIORITIES: PriorityLevel[] = ['P1', 'P2', 'P3', 'P4'];

/** Response and resolution targets per priority, optionally overridden per service. */
export function SlaPoliciesPage() {
  const queryClient = useQueryClient();
  const policies = useQuery({ queryKey: ['admin', 'sla-policies'], queryFn: api.admin.slaPolicies });
  const [editing, setEditing] = useState<SlaPolicyView | 'new' | null>(null);

  const remove = (p: SlaPolicyView) =>
    modals.openConfirmModal({
      title: 'Delete policy',
      children: (
        <Text size="sm">
          Delete the {p.priority} policy for {p.serviceName}? New {p.priority} incidents on that service will use the
          generic {p.priority} targets. Existing incidents keep their deadlines.
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Back' },
      confirmProps: { color: 'red' },
      onConfirm: () =>
        api.admin
          .deleteSlaPolicy(p.id)
          .then(() => queryClient.invalidateQueries({ queryKey: ['admin', 'sla-policies'] }))
          .catch((e) => notifications.show({ color: 'red', message: errorMessage(e) })),
    });

  return (
    <Stack>
      <div>
        <Title order={2}>SLA policies</Title>
        <Text size="sm" c="dimmed">
          A service-specific policy beats the generic one. Changes apply to incidents classified from now on; running
          SLA clocks keep their original deadlines.
        </Text>
      </div>
      <Paper withBorder radius="md" p="md">
        <Group justify="flex-end" mb="sm">
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>
            Service-specific policy
          </Button>
        </Group>
        <QueryState query={policies}>
          <Table.ScrollContainer minWidth={600}>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Priority</Table.Th>
                  <Table.Th>Applies to</Table.Th>
                  <Table.Th ta="right">Response</Table.Th>
                  <Table.Th ta="right">Resolution</Table.Th>
                  <Table.Th ta="right">Warn at</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {policies.data?.map((p) => (
                  <Table.Tr key={p.id}>
                    <Table.Td>
                      <PriorityBadge priority={p.priority} />
                    </Table.Td>
                    <Table.Td>
                      {p.serviceName ?? (
                        <Badge variant="default" size="sm">
                          All services
                        </Badge>
                      )}
                    </Table.Td>
                    <Table.Td ta="right">{formatMinutes(p.responseMinutes)}</Table.Td>
                    <Table.Td ta="right">{formatMinutes(p.resolutionMinutes)}</Table.Td>
                    <Table.Td ta="right">{Math.round(p.warningThreshold * 100)}%</Table.Td>
                    <Table.Td>
                      <Group gap={4} justify="flex-end" wrap="nowrap">
                        <Button size="xs" variant="subtle" leftSection={<IconPencil size={14} />} onClick={() => setEditing(p)}>
                          Edit
                        </Button>
                        {p.serviceId !== null && (
                          <Button size="xs" variant="subtle" color="red" leftSection={<IconTrash size={14} />} onClick={() => remove(p)}>
                            Delete
                          </Button>
                        )}
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </QueryState>
      </Paper>
      {editing && <PolicyModal policy={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function PolicyModal({ policy, onClose }: { policy: SlaPolicyView | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const form = useForm({
    initialValues: {
      priority: (policy?.priority ?? 'P1') as PriorityLevel,
      serviceId: policy?.serviceId ? String(policy.serviceId) : (null as string | null),
      responseMinutes: policy?.responseMinutes ?? 15,
      resolutionMinutes: policy?.resolutionMinutes ?? 120,
      warningThreshold: policy?.warningThreshold ?? 0.75,
    },
    validate: {
      serviceId: (v) => (policy || v ? null : 'Choose a service'),
      responseMinutes: (v) => (Number(v) >= 1 ? null : 'At least 1 minute'),
      resolutionMinutes: (v, values) =>
        Number(v) >= Number(values.responseMinutes) ? null : 'Cannot be shorter than the response target',
    },
  });
  const save = useMutation({
    mutationFn: (v: typeof form.values) => {
      const req = {
        priority: v.priority,
        serviceId: v.serviceId ? Number(v.serviceId) : null,
        responseMinutes: Number(v.responseMinutes),
        resolutionMinutes: Number(v.resolutionMinutes),
        warningThreshold: v.warningThreshold,
      };
      return policy ? api.admin.updateSlaPolicy(policy.id, req) : api.admin.createSlaPolicy(req);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'sla-policies'] });
      notifications.show({ color: 'green', message: 'Policy saved' });
      onClose();
    },
    onError: (e) => notifications.show({ color: 'red', title: 'Not saved', message: errorMessage(e) }),
  });

  return (
    <Modal opened onClose={onClose} title={policy ? `Edit ${policy.priority} policy` : 'New service-specific policy'}>
      <form onSubmit={form.onSubmit((v) => save.mutate(v))}>
        <Stack>
          <Group grow>
            <Select
              label="Priority"
              disabled={!!policy}
              allowDeselect={false}
              data={PRIORITIES}
              {...form.getInputProps('priority')}
            />
            <Select
              label="Service"
              disabled={!!policy}
              placeholder={policy ? 'All services' : 'Choose'}
              data={(services.data ?? []).map((s) => ({ value: String(s.id), label: s.name }))}
              {...form.getInputProps('serviceId')}
            />
          </Group>
          <Group grow>
            <NumberInput label="Response target (minutes)" min={1} max={10080} {...form.getInputProps('responseMinutes')} />
            <NumberInput label="Resolution target (minutes)" min={1} max={43200} {...form.getInputProps('resolutionMinutes')} />
          </Group>
          <div>
            <Text size="sm" fw={500}>
              Warn when {Math.round(form.values.warningThreshold * 100)}% of the time is used
            </Text>
            <Slider
              min={0.5}
              max={0.95}
              step={0.05}
              label={(v) => `${Math.round(v * 100)}%`}
              {...form.getInputProps('warningThreshold')}
            />
          </div>
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={save.isPending}>
              Save
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
