import { Alert, Anchor, Badge, Button, Card, Grid, Group, NumberInput, Paper, Select, Stack, Text, TextInput, Textarea, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDebouncedValue } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconBolt, IconCopy, IconSparkles } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import type { Impact, Urgency } from '../api/types';
import { PriorityBadge, StatusBadge } from '../components/Badges';
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

  // Intelligent features: suggest a category and spot an incident someone already reported, as the user types.
  const { title, description } = form.values;
  const [draft] = useDebouncedValue({ title: title.trim(), description: description.trim(), serviceId }, 500);
  const analysis = useQuery({
    queryKey: ['draft-analysis', draft],
    queryFn: () =>
      api.intelligence.analyze({
        title: draft.title,
        description: draft.description,
        serviceId: draft.serviceId ? Number(draft.serviceId) : null,
      }),
    enabled: draft.title.length >= 4 || draft.description.length >= 10,
    placeholderData: (previous) => previous,
  });
  const topSuggestion = analysis.data?.categorySuggestions[0];
  const alreadyReported = analysis.data?.similarIncidents ?? [];

  const report = useMutation({
    mutationFn: ({ values, allowDuplicate }: { values: FormValues; allowDuplicate: boolean }) =>
      api.incidents.report({
        title: values.title.trim(),
        description: values.description.trim(),
        serviceId: Number(values.serviceId),
        categoryId: values.categoryId ? Number(values.categoryId) : null,
        impact: values.impact,
        urgency: values.urgency,
        affectedUsers: Number(values.affectedUsers) || 1,
        allowDuplicate,
      }),
    onSuccess: (incident) => {
      queryClient.invalidateQueries({ queryKey: ['incidents'] });
      notifications.show({ color: 'green', title: `Incident ${incident.number} reported`, message: incident.title });
      navigate(`/incidents/${incident.id}`);
    },
  });

  const selectedService = services.data?.find((s) => String(s.id) === serviceId);
  const duplicateWarning = report.error instanceof ApiError && report.error.code === 'DUPLICATE_INCIDENT';

  return (
    <Stack maw={1100}>
      <Title order={2}>Report an incident</Title>
      <Grid>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Paper withBorder p="lg" radius="md">
            <form onSubmit={form.onSubmit((values) => report.mutate({ values, allowDuplicate: false }))}>
              <Stack>
                {duplicateWarning && (
                  <Alert color="yellow" icon={<IconCopy size={16} />} title="Already reported?">
                    <Text size="sm">{errorMessage(report.error)}</Text>
                    <Group mt="sm" gap="xs">
                      <Button size="xs" variant="default" onClick={() => navigate('/incidents')}>
                        View my incidents
                      </Button>
                      <Button
                        size="xs"
                        color="yellow"
                        loading={report.isPending}
                        onClick={() => report.mutate({ values: form.getValues(), allowDuplicate: true })}
                      >
                        It's a different problem: submit anyway
                      </Button>
                    </Group>
                  </Alert>
                )}
                {report.error && !duplicateWarning && (
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
                    description={
                      topSuggestion && !form.values.categoryId ? (
                        <Anchor
                          component="button"
                          type="button"
                          size="xs"
                          onClick={() => form.setFieldValue('categoryId', String(topSuggestion.categoryId))}
                        >
                          Suggested: {topSuggestion.name} ({Math.round(topSuggestion.confidence * 100)}%), use it
                        </Anchor>
                      ) : (
                        'Optional: detected automatically if left empty'
                      )
                    }
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
            {(topSuggestion || alreadyReported.length > 0) && (
              <Card withBorder radius="md">
                <Group gap="xs" mb="xs">
                  <IconSparkles size={18} />
                  <Text fw={600}>Smart assistant</Text>
                </Group>
                <Stack gap="sm">
                  {analysis.data?.categorySuggestions.map((s) => (
                    <div key={s.categoryId}>
                      <Group gap="xs">
                        <Text size="sm" fw={500}>
                          {s.name}
                        </Text>
                        <Badge size="xs" variant="light" color={s.confidence >= 0.5 ? 'teal' : 'gray'}>
                          {Math.round(s.confidence * 100)}% confident
                        </Badge>
                      </Group>
                      <Text size="xs" c="dimmed">
                        {s.reasons.join(' · ')}
                      </Text>
                    </div>
                  ))}
                  {alreadyReported.length > 0 && (
                    <div>
                      <Text size="sm" fw={500} mb={4}>
                        Possibly already reported
                      </Text>
                      <Stack gap={6}>
                        {alreadyReported.slice(0, 4).map((r) => (
                          <Group key={r.id} gap="xs" wrap="nowrap" align="flex-start">
                            <StatusBadge status={r.status} />
                            <Text size="xs" style={{ flex: 1 }}>
                              <Text span size="xs" ff="monospace">
                                {r.number}
                              </Text>{' '}
                              {r.title}
                              <Text span size="xs" c="dimmed">
                                {' '}
                                · {r.serviceName}
                              </Text>
                            </Text>
                          </Group>
                        ))}
                      </Stack>
                      <Text size="xs" c="dimmed" mt={4}>
                        Others may have reported this already. Reporting it again is fine: support staff will link duplicates.
                      </Text>
                    </div>
                  )}
                </Stack>
              </Card>
            )}
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
