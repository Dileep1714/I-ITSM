import { ActionIcon, Indicator } from '@mantine/core';
import { IconBell } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';

/** Header bell with the unread count; polls every 30 seconds. */
export function NotificationBell() {
  const navigate = useNavigate();
  const { data } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: api.notifications.unreadCount,
    refetchInterval: 30_000,
  });
  const unread = data?.unread ?? 0;

  return (
    <Indicator label={unread > 99 ? '99+' : unread} size={16} disabled={unread === 0} color="carbon" radius={0} offset={4}>
      <ActionIcon variant="subtle" color="gray" size="lg" aria-label="Notifications" onClick={() => navigate('/notifications')}>
        <IconBell size={18} stroke={1.6} />
      </ActionIcon>
    </Indicator>
  );
}
