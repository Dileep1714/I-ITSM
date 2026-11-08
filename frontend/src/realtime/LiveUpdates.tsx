import { Client, type IMessage } from '@stomp/stompjs';
import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';
import { tokenStore } from '../api/client';
import type { IncidentEventType, IncidentStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { LiveContext, type LiveStatus } from './liveStatus';

/** Pushed to support staff on /topic/incidents. */
interface IncidentUpdate {
  incidentId: number;
  incidentNumber: string;
  type: IncidentEventType;
  newStatus: IncidentStatus | null;
  actorName: string | null;
  occurredAt: string;
}

/** Pushed to one user on /user/queue/notifications. */
interface NotificationPush {
  incidentId: number | null;
  incidentNumber: string | null;
  type: IncidentEventType;
  title: string;
}

/** Events worth a toast for staff watching the live feed (everything else just refreshes the data). */
const ALERT_TYPES: IncidentEventType[] = ['SLA_BREACHED', 'ESCALATED'];

/**
 * Keeps one STOMP-over-WebSocket connection open while logged in. Server pushes are hints ("incident 42
 * changed"): the matching React Query caches are invalidated and refetched through the normal REST API, so
 * permissions are always applied by the server. Reconnects automatically; the app works without it.
 */
export function LiveUpdatesProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<LiveStatus>('offline');

  useEffect(() => {
    const token = tokenStore.get();
    if (!user || !token) return;
    const isStaff = user.role !== 'EMPLOYEE';
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';

    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      beforeConnect: () => setStatus('connecting'),
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      onConnect: () => {
        setStatus('live');
        client.subscribe('/user/queue/notifications', (message: IMessage) => {
          const push = JSON.parse(message.body) as NotificationPush;
          queryClient.invalidateQueries({ queryKey: ['notifications'] });
          queryClient.invalidateQueries({ queryKey: ['incidents'] });
          notifications.show({ title: push.title, message: push.incidentNumber ?? '', autoClose: 5000 });
        });
        if (isStaff) {
          client.subscribe('/topic/incidents', (message: IMessage) => {
            const update = JSON.parse(message.body) as IncidentUpdate;
            queryClient.invalidateQueries({ queryKey: ['incidents'] });
            queryClient.invalidateQueries({ queryKey: ['reports'] });
            queryClient.invalidateQueries({ queryKey: ['correlations'] });
            if (ALERT_TYPES.includes(update.type) && user.role === 'ADMINISTRATOR') {
              notifications.show({
                color: 'red',
                title: `${update.incidentNumber}: ${update.type === 'SLA_BREACHED' ? 'SLA breached' : 'escalated'}`,
                message: 'Live update',
                autoClose: 6000,
              });
            }
          });
        }
      },
      onWebSocketClose: () => setStatus('connecting'),
      onStompError: () => setStatus('offline'),
    });

    client.activate();
    return () => {
      setStatus('offline');
      void client.deactivate();
    };
  }, [user, queryClient]);

  return <LiveContext.Provider value={status}>{children}</LiveContext.Provider>;
}
