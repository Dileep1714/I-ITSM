import { Badge, Card, Group, Radio, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { StrategySettings, StrategyView } from '../../api/types';
import { QueryState } from '../../components/QueryState';
import { errorMessage, humanize } from '../../lib/format';

function StrategyPicker({
  title,
  description,
  strategies,
  onSelect,
  pending,
}: {
  title: string;
  description: string;
  strategies: StrategyView[];
  onSelect: (key: string) => void;
  pending: boolean;
}) {
  const active = strategies.find((s) => s.active)?.key ?? null;
  return (
    <Card withBorder radius="md" p="lg">
      <Text fw={600}>{title}</Text>
      <Text size="sm" c="dimmed" mb="md">
        {description}
      </Text>
      <Radio.Group value={active} onChange={(key) => !pending && onSelect(key)}>
        <Stack gap="sm">
          {strategies.map((s) => (
            <Radio.Card key={s.key} value={s.key} p="sm" radius="md" withBorder disabled={pending}>
              <Group wrap="nowrap" align="flex-start">
                <Radio.Indicator />
                <div>
                  <Group gap="xs">
                    <Text size="sm" fw={600}>
                      {s.displayName}
                    </Text>
                    {s.active && (
                      <Badge size="xs" color="green">
                        Active
                      </Badge>
                    )}
                  </Group>
                  <Text size="xs" c="dimmed">
                    {s.description}
                  </Text>
                </div>
              </Group>
            </Radio.Card>
          ))}
        </Stack>
      </Radio.Group>
    </Card>
  );
}

export function SettingsPage() {
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: ['admin', 'strategies'], queryFn: api.admin.strategies });

  const onSuccess = (data: StrategySettings, key: string) => {
    queryClient.setQueryData(['admin', 'strategies'], data);
    notifications.show({ color: 'green', message: `Switched to ${humanize(key)}` });
  };
  const onError = (e: unknown) => notifications.show({ color: 'red', message: errorMessage(e) });

  const assignment = useMutation({ mutationFn: api.admin.selectAssignment, onSuccess, onError });
  const priority = useMutation({ mutationFn: api.admin.selectPriority, onSuccess, onError });

  return (
    <Stack>
      <Title order={2}>Settings</Title>
      <QueryState query={settings}>
        {settings.data && (
          <>
            <Text size="sm" c="dimmed">
              Changes take effect immediately for new classifications and assignments. Automatic assignment is{' '}
              <b>{settings.data.autoAssign ? 'on' : 'off'}</b>.
            </Text>
            <SimpleGrid cols={{ base: 1, md: 2 }}>
              <StrategyPicker
                title="Technician assignment"
                description="How a technician is chosen for a classified incident."
                strategies={settings.data.assignmentStrategies}
                onSelect={(key) => assignment.mutate(key)}
                pending={assignment.isPending}
              />
              <StrategyPicker
                title="Priority calculation"
                description="How impact, urgency, service criticality and affected users become P1–P4."
                strategies={settings.data.priorityCalculators}
                onSelect={(key) => priority.mutate(key)}
                pending={priority.isPending}
              />
            </SimpleGrid>
          </>
        )}
      </QueryState>
    </Stack>
  );
}
