import { Accordion, Alert, Badge, Group, Stack, Text, Title } from '@mantine/core';
import { IconInfoCircle, IconMail, IconMessage } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { QueryState } from '../../components/QueryState';
import { formatDateTime } from '../../lib/format';

export function OutboxPage() {
  const outbox = useQuery({ queryKey: ['admin', 'outbox'], queryFn: () => api.admin.outbox(100), refetchInterval: 15_000 });

  return (
    <Stack maw={900}>
      <Title order={2}>Outbox</Title>
      <QueryState query={outbox}>
        {outbox.data?.emailSimulated && (
          <Alert icon={<IconInfoCircle size={16} />} color="blue">
            Email is simulated: messages are recorded here instead of being sent. SMS is always simulated.
          </Alert>
        )}
        {outbox.data?.messages.length === 0 && (
          <Text c="dimmed" ta="center" py="lg">
            No messages sent yet.
          </Text>
        )}
        <Accordion variant="separated">
          {outbox.data?.messages.map((m, index) => (
            <Accordion.Item key={`${m.sentAt}-${index}`} value={`${m.sentAt}-${index}`}>
              <Accordion.Control icon={m.channel === 'SMS' ? <IconMessage size={18} /> : <IconMail size={18} />}>
                <Group justify="space-between" wrap="nowrap" pr="sm">
                  <div>
                    <Text size="sm" fw={500} lineClamp={1}>
                      {m.subject || '(no subject)'}
                    </Text>
                    <Text size="xs" c="dimmed">
                      to {m.to}
                    </Text>
                  </div>
                  <Group gap="xs" wrap="nowrap">
                    <Badge size="sm" variant="light" color={m.channel === 'SMS' ? 'grape' : 'blue'}>
                      {m.channel}
                    </Badge>
                    <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                      {formatDateTime(m.sentAt)}
                    </Text>
                  </Group>
                </Group>
              </Accordion.Control>
              <Accordion.Panel>
                <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
                  {m.body}
                </Text>
              </Accordion.Panel>
            </Accordion.Item>
          ))}
        </Accordion>
      </QueryState>
    </Stack>
  );
}
