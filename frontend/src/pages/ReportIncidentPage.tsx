import { Alert, Button, Card, Grid, Group, NumberInput, Paper, Select, Stack, Text, TextInput, Textarea, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDebouncedValue } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconBolt } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Impact, Urgency } from '../api/types';
import { PriorityBadge } from '../components/Badges';
import { errorMessage, humanize } from '../lib/format';

const IMPACTS: Impact[] = ['LOW', 'MEDIUM', 'HIGH', 'EXTENSIVE'];
const URGENCIES: Urgency[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

const IMPACT_HELP: Record<Impact, string> = {
  LOW: 'Only me, minor inconvenience',
  MEDIUM: 'My team, or one function degraded',
  HIGH: 'A department, or a key function down',
  EXTENSIVE: 'Many departments or the whole organisation',
};

interface FormValues {
  title: string;
  description: string;
  serviceId: string | null;
  categoryId: string | null;
  impact: Impact;
  urgency: Urgency;
  affectedUsers: number | string;
}

export function ReportIncidentPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.catalog.categories });

  const form = useForm<FormValues>({
    initialValues: {
      title: '',
      description: '',
      serviceId: null,
      categoryId: null,
      impact: 'LOW',
      urgency: 'MEDIUM',
      affectedUsers: 1,
    },
    validate: {
      title: (v) => (v.trim() ? (v.length > 150 ? 'At most 150 characters' : null) : 'Enter a short title'),
      description: (v) => (v.trim().length >= 10 ? null : 'Describe the problem (at least 10 characters)'),
      serviceId: (v) => (v ? null : 'Choose the affected service'),
      affectedUsers: (v) => (Number(v) >= 1 ? null : 'At least 1'),
    },
  });

  const { serviceId, impact, urgency, affectedUsers } = form.values;
  const [previewInput] = useDebouncedValue({ serviceId, impact, urgency, affectedUsers: Number(affectedUsers) || 1 }, 300);
  const preview = useQuery({
    queryKey: ['priority-preview', previewInput],
    queryFn: () =>
      api.catalog.previewPriority({ ...previewInput, serviceId: Number(previewInput.serviceId) }),
    enabled: previewInput.serviceId !== null,
  });

  const report = useMutation({
    mutationFn: (values: FormValues) =>
      api.incidents.report({
        title: values.title.trim(),
        description: values.description.trim(),
        serviceId: Number(values.serviceId),
        categoryId: values.categoryId ? Number(values.categoryId) : null,
        impact: values.impact,
        urgency: values.urgency,
        affectedUsers: Number(values.affectedUsers) || 1,
      }),
    onSuccess: (incident) => {
      queryClient.invalidateQueries({ queryKey: ['incidents'] });
      notifications.show({ color: 'green', title: `Incident ${incident.number} reported`, message: incident.title });
      navigate(`/incidents/${incident.id}`);
    },
  });

  const selectedService = services.data?.find((s) => String(s.id) === serviceId);

  return (
    <Stack maw={1100}>
      <Title order={2}>Report an incident</Title>
      <Grid>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Paper withBorder p="lg" radius="md">
            <form onSubmit={form.onSubmit((v) => report.mutate(v))}>
              <Stack>
                {report.error && (
                  <Alert color="red" icon={<IconAlertCircle size={16} />} style={{ whiteSpace: 'pre-line' }}>
                    {errorMessage(report.error)}
                  </Alert>
                )}
                <TextInput label="Title" placeholder="e.g. Cannot connect to VPN" required {...form.getInputProps('title')} />
                <Textarea
                  label="Description"
                  placeholder="What happened, when it started, any error messages…"
                  autosize
                  minRows={4}
                  required
                  {...form.getInputProps('description')}
                />
                <Group grow align="flex-start">
                  <Select
                    label="Affected service"
                    required
                    searchable
                    data={(services.data ?? []).map((s) => ({ value: String(s.id), label: s.name }))}
                    {...form.getInputProps('serviceId')}
                  />
                  <Select
                    label="Category"
                    description="Optional: speeds up assignment"
                    clearable
                    data={(categories.data ?? [])
                      .filter((c) => c.active)
                      .map((c) => ({ value: String(c.id), label: c.name }))}
                    {...form.getInputProps('categoryId')}
                  />
                </Group>
                <Group grow align="flex-start">
                  <Select
                    label="Impact"
                    description={IMPACT_HELP[impact]}
                    allowDeselect={false}
                    data={IMPACTS.map((v) => ({ value: v, label: humanize(v) }))}
                    {...form.getInputProps('impact')}
                  />
                  <Select
                    label="Urgency"
                    description="How soon does it need fixing?"
                    allowDeselect={false}
                    data={URGENCIES.map((v) => ({ value: v, label: humanize(v) }))}
                    {...form.getInputProps('urgency')}
                  />
                  <NumberInput label="Users affected" min={1} max={1_000_000} {...form.getInputProps('affectedUsers')} />
                </Group>
                <Group justify="flex-end">
                  <Button variant="default" onClick={() => navigate(-1)}>
                    Cancel
                  </Button>
                  <Button type="submit" loading={report.isPending}>
                    Submit incident
                  </Button>
                </Group>
              </Stack>
            </form>
          </Paper>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 4 }}>
          <Stack>
            <Card withBorder radius="md">
              <Group gap="xs" mb="xs">
                <IconBolt size={18} />
                <Text fw={600}>Calculated priority</Text>
              </Group>
              {!serviceId && (
                <Text size="sm" c="dimmed">
                  Choose a service to see the priority this incident will get.
                </Text>
              )}
              {preview.data && (
                <Stack gap="xs">
                  <Group>
                    <PriorityBadge priority={preview.data.level} />
                    <Text fw={500}>{preview.data.label}</Text>
                    <Text size="sm" c="dimmed">
                      score {preview.data.score.toFixed(1)}
                    </Text>
                  </Group>
                  <Text size="sm" c="dimmed">
                    {preview.data.rationale}
                  </Text>
                </Stack>
              )}
              {preview.error && (
                <Text size="sm" c="red">
                  {errorMessage(preview.error)}
                </Text>
              )}
            </Card>
            {selectedService && (
              <Card withBorder radius="md">
                <Text fw={600}>{selectedService.name}</Text>
                <Text size="sm" c="dimmed" mb="xs">
                  {selectedService.description}
                </Text>
                <Text size="sm">Criticality: {humanize(selectedService.criticality)}</Text>
                <Text size="sm">Status: {humanize(selectedService.status)}</Text>
                {selectedService.dependsOn.length > 0 && (
                  <Text size="sm">Depends on: {selectedService.dependsOn.join(', ')}</Text>
                )}
              </Card>
            )}
          </Stack>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
