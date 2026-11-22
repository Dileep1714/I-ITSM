import { Client, type IMessage } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { AppState } from 'react-native';
import { buzz } from '../lib/haptics';
import { initSystemNotifications, showSystemNotification } from '../lib/systemNotifications';
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { tokenStore } from '../api/client';
import type { IncidentEventType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { WS_URL } from '../lib/config';
import { useToast } from '../ui/toast';

export type LiveStatus = 'connecting' | 'live' | 'offline';
const LiveContext = createContext<LiveStatus>('offline');
export const useLiveStatus = () => useContext(LiveContext);

interface NotificationPush {
  incidentId: number | null;
  incidentNumber: string | null;
  type: IncidentEventType;
  title: string;
}

/**
 * Same real-time channel as the web app: STOMP over WebSocket. A push is a hint ("incident 42 changed"); the
 * affected queries are refetched over REST, so the server still decides what this user may see.
 */
export function LiveUpdatesProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [status, setStatus] = useState<LiveStatus>('offline');

  useEffect(() => {
    const token = tokenStore.get();
    if (!user || !token) return;
    void initSystemNotifications();

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      // React Native's WebSocket mishandles the NUL byte that ends each STOMP frame; these are the documented fixes.
      forceBinaryWSFrames: true,
      appendMissingNULLonIncoming: true,
      beforeConnect: () => setStatus('connecting'),
      onConnect: () => {
        setStatus('live');
        client.subscribe('/user/queue/notifications', (message: IMessage) => {
          const push = JSON.parse(message.body) as NotificationPush;
          queryClient.invalidateQueries({ queryKey: ['notifications'] });
          queryClient.invalidateQueries({ queryKey: ['incidents'] });
          if (AppState.currentState !== 'active') {
            // App in the background: a real notification in the shade (tap opens the incident).
            void showSystemNotification(push.title, push.incidentNumber ?? undefined, push.incidentId);
            return;
          }
          buzz('warning');
          toast({
            tone: 'info',
            title: push.title,
            message: push.incidentNumber ? `${push.incidentNumber} · tap to open` : undefined,
            onPress: push.incidentId ? () => router.push(`/incident/${push.incidentId}`) : undefined,
          });
        });
        if (user.role !== 'EMPLOYEE') {
          client.subscribe('/topic/incidents', () => {
            queryClient.invalidateQueries({ queryKey: ['incidents'] });
            queryClient.invalidateQueries({ queryKey: ['correlations'] });
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
  }, [user, queryClient, toast]);

  return <LiveContext.Provider value={status}>{children}</LiveContext.Provider>;
}
