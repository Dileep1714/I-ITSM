import {
  Badge,
  Button,
  Group,
  Modal,
  MultiSelect,
  Paper,
  Select,
  Stack,
  Switch,
  Table,
  Tabs,
  TagsInput,
  Text,
  TextInput,
  Textarea,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconPencil, IconPlus } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import type { BusinessCriticality, CategoryAdminView, ServiceAdminView, ServiceStatus } from '../../api/types';
import { QueryState } from '../../components/QueryState';
import { errorMessage, humanize } from '../../lib/format';

const CRITICALITIES: BusinessCriticality[] = ['LOW', 'MEDIUM', 'HIGH', 'MISSION_CRITICAL'];
const STATUSES: ServiceStatus[] = ['OPERATIONAL', 'DEGRADED', 'PARTIAL_OUTAGE', 'MAJOR_OUTAGE', 'MAINTENANCE'];

/** Categories (with categorization keywords) and IT services (with their dependency graph). */
export function CatalogPage() {
  return (
    <Stack>
      <div>
        <Title order={2}>Catalogue</Title>
        <Text size="sm" c="dimmed">
          Changes apply immediately: new categories appear on the report form and feed automatic categorization.
        </Text>
      </div>
      <Tabs defaultValue="categories">
        <Tabs.List mb="md">
          <Tabs.Tab value="categories">Categories</Tabs.Tab>
          <Tabs.Tab value="services">Services</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="categories">
          <Categories />
        </Tabs.Panel>
        <Tabs.Panel value="services">
          <Services />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  );
}

// ----- categories ----------------------------------------------------------------------------------------------

function Categories() {
  const list = useQuery({ queryKey: ['admin', 'categories'], queryFn: api.admin.categories });
  const [editing, setEditing] = useState<CategoryAdminView | 'new' | null>(null);

  return (
    <Paper withBorder radius="md" p="md">
      <Group justify="flex-end" mb="sm">
        <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>
          New category
        </Button>
      </Group>
      <QueryState query={list}>
        <Table.ScrollContainer minWidth={640}>
          <Table striped highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Name</Table.Th>
                <Table.Th>Keywords (used for automatic categorization)</Table.Th>
                <Table.Th>Status</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {list.data?.map((c) => (
                <Table.Tr key={c.id}>
                  <Table.Td>
                    <Text fw={500} size="sm">
                      {c.name}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {c.description}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Group gap={4}>
                      {c.keywords.map((k) => (
                        <Badge key={k} size="sm" variant="default">
                          {k}
                        </Badge>
                      ))}
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <Badge color={c.active ? 'green' : 'gray'} variant="light">
                      {c.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    <Button size="xs" variant="subtle" leftSection={<IconPencil size={14} />} onClick={() => setEditing(c)}>
                      Edit
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      </QueryState>
      {editing && <CategoryModal category={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </Paper>
  );
}

function CategoryModal({ category, onClose }: { category: CategoryAdminView | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const form = useForm({
    initialValues: {
      name: category?.name ?? '',
      description: category?.description ?? '',
      keywords: category?.keywords ?? [],
      active: category?.active ?? true,
    },
    validate: { name: (v) => (v.trim() ? null : 'Enter a name') },
  });
  const save = useMutation({
    mutationFn: (v: typeof form.values) => {
      const req = { name: v.name.trim(), description: v.description.trim() || null, keywords: v.keywords, active: v.active };
      return category ? api.admin.updateCategory(category.id, req) : api.admin.createCategory(req);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'categories'] });
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      notifications.show({ color: 'green', message: `Saved ${saved.name}` });
      onClose();
    },
    onError: (e) => notifications.show({ color: 'red', message: errorMessage(e) }),
  });

  return (
    <Modal opened onClose={onClose} title={category ? `Edit ${category.name}` : 'New category'}>
      <form onSubmit={form.onSubmit((v) => save.mutate(v))}>
        <Stack>
          <TextInput label="Name" required {...form.getInputProps('name')} />
          <Textarea label="Description" autosize {...form.getInputProps('description')} />
          <TagsInput
            label="Keywords"
            description="Words or phrases that indicate this category, e.g. vpn, packet loss. Press Enter after each."
            {...form.getInputProps('keywords')}
          />
          <Switch label="Active (can be chosen for new incidents)" {...form.getInputProps('active', { type: 'checkbox' })} />
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

// ----- services ------------------------------------------------------------------------------------------------

function Services() {
  const list = useQuery({ queryKey: ['admin', 'services'], queryFn: api.admin.services });
  const [editing, setEditing] = useState<ServiceAdminView | 'new' | null>(null);

  return (
    <Paper withBorder radius="md" p="md">
      <Group justify="flex-end" mb="sm">
        <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>
          New service
        </Button>
      </Group>
      <QueryState query={list}>
        <Table.ScrollContainer minWidth={640}>
          <Table striped highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Service</Table.Th>
                <Table.Th>Criticality</Table.Th>
                <Table.Th>Status</Table.Th>
                <Table.Th>Depends on</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {list.data?.map((s) => (
                <Table.Tr key={s.id}>
                  <Table.Td>
                    <Text fw={500} size="sm">
                      {s.name}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {s.description}
                    </Text>
                  </Table.Td>
                  <Table.Td>{humanize(s.criticality)}</Table.Td>
                  <Table.Td>
                    <Badge color={s.status === 'OPERATIONAL' ? 'green' : 'orange'} variant="light">
                      {humanize(s.status)}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{s.dependsOn.map((d) => d.name).join(', ') || '—'}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Button size="xs" variant="subtle" leftSection={<IconPencil size={14} />} onClick={() => setEditing(s)}>
                      Edit
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      </QueryState>
      {editing && (
        <ServiceModal service={editing === 'new' ? null : editing} all={list.data ?? []} onClose={() => setEditing(null)} />
      )}
    </Paper>
  );
}

function ServiceModal({ service, all, onClose }: { service: ServiceAdminView | null; all: ServiceAdminView[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const form = useForm({
    initialValues: {
      name: service?.name ?? '',
      description: service?.description ?? '',
      criticality: (service?.criticality ?? 'MEDIUM') as BusinessCriticality,
      status: (service?.status ?? 'OPERATIONAL') as ServiceStatus,
      dependsOnIds: service?.dependsOn.map((d) => String(d.id)) ?? [],
    },
    validate: { name: (v) => (v.trim() ? null : 'Enter a name') },
  });
  const save = useMutation({
    mutationFn: (v: typeof form.values) => {
      const req = {
        name: v.name.trim(),
        description: v.description.trim() || null,
        criticality: v.criticality,
        status: v.status,
        dependsOnIds: v.dependsOnIds.map(Number),
      };
      return service ? api.admin.updateService(service.id, req) : api.admin.createService(req);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'services'] });
      queryClient.invalidateQueries({ queryKey: ['services'] });
      notifications.show({ color: 'green', message: `Saved ${saved.name}` });
      onClose();
    },
    onError: (e) => notifications.show({ color: 'red', title: 'Not saved', message: errorMessage(e) }),
  });

  return (
    <Modal opened onClose={onClose} title={service ? `Edit ${service.name}` : 'New service'}>
      <form onSubmit={form.onSubmit((v) => save.mutate(v))}>
        <Stack>
          <TextInput label="Name" required {...form.getInputProps('name')} />
          <Textarea label="Description" autosize {...form.getInputProps('description')} />
          <Group grow>
            <Select
              label="Business criticality"
              description="Feeds the priority calculation"
              allowDeselect={false}
              data={CRITICALITIES.map((c) => ({ value: c, label: humanize(c) }))}
              {...form.getInputProps('criticality')}
            />
            <Select
              label="Status"
              allowDeselect={false}
              data={STATUSES.map((s) => ({ value: s, label: humanize(s) }))}
              {...form.getInputProps('status')}
            />
          </Group>
          <MultiSelect
            label="Depends on"
            description="Used for root-cause correlation. Circular dependencies are rejected."
            searchable
            data={all.filter((s) => s.id !== service?.id).map((s) => ({ value: String(s.id), label: s.name }))}
            {...form.getInputProps('dependsOnIds')}
          />
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
