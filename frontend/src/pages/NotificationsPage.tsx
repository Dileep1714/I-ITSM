import { Button, Card, Group, Pagination, SegmentedControl, Stack, Text, ThemeIcon, Title, UnstyledButton } from '@mantine/core';
import { IconAlertTriangle, IconBell, IconCheck, IconChecks } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { NotificationView } from '../api/types';
import { QueryState } from '../components/QueryState';
import { fromNow } from '../lib/format';

function iconFor(n: NotificationView) {
  if (n.type === 'SLA_BREACHED' || n.type === 'ESCALATED') return { color: 'red', icon: <IconAlertTriangle size={16} /> };
  if (n.type === 'SLA_WARNING') return { color: 'orange', icon: <IconAlertTriangle size={16} /> };
  if (n.type === 'RESOLVED' || n.type === 'CLOSED') return { color: 'teal', icon: <IconCheck size={16} /> };
  return { color: 'blue', icon: <IconBell size={16} /> };
}

export function NotificationsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [page, setPage] = useState(0);
  const list = useQuery({
    queryKey: ['notifications', filter, page],
    queryFn: () => api.notifications.list(filter === 'unread', page, 20),
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });
  const markRead = useMutation({ mutationFn: api.notifications.markRead, onSuccess: refresh });
  const markAll = useMutation({ mutationFn: api.notifications.markAllRead, onSuccess: refresh });

  const open = (n: NotificationView) => {
    if (!n.read) markRead.mutate(n.id);
    if (n.incidentId) navigate(`/incidents/${n.incidentId}`);
  };

  return (
    <Stack maw={800}>
      <Group justify="space-between">
        <Title order={2}>Notifications</Title>
        <Group>
          <SegmentedControl
            value={filter}
            onChange={(v) => {
              setFilter(v as 'all' | 'unread');
              setPage(0);
            }}
            data={[
              { value: 'all', label: 'All' },
              { value: 'unread', label: 'Unread' },
            ]}
          />
          <Button variant="light" leftSection={<IconChecks size={16} />} loading={markAll.isPending} onClick={() => markAll.mutate()}>
            Mark all read
          </Button>
        </Group>
      </Group>
      <QueryState query={list}>
        <Stack gap="xs">
          {list.data?.content.length === 0 && (
            <Text c="dimmed" ta="center" py="xl">
              You're all caught up.
            </Text>
          )}
          {list.data?.content.map((n) => {
            const { color, icon } = iconFor(n);
            return (
              <UnstyledButton key={n.id} onClick={() => open(n)}>
                <Card withBorder p="sm" radius="md" style={{ opacity: n.read ? 0.65 : 1 }}>
                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color={color} variant="light" radius="xl">
                      {icon}
                    </ThemeIcon>
                    <div style={{ flex: 1 }}>
                      <Group justify="space-between" wrap="nowrap">
                        <Text size="sm" fw={n.read ? 400 : 700}>
                          {n.title}
                        </Text>
                        <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                          {fromNow(n.createdAt)}
                        </Text>
                      </Group>
                      <Text size="sm" c="dimmed">
                        {n.message}
                      </Text>
                    </div>
                  </Group>
                </Card>
              </UnstyledButton>
            );
          })}
          {list.data && list.data.totalPages > 1 && (
            <Pagination mt="sm" total={list.data.totalPages} value={page + 1} onChange={(p) => setPage(p - 1)} />
          )}
        </Stack>
      </QueryState>
    </Stack>
  );
}
