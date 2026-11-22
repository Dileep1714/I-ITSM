import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { api } from '../api/client';
import type { IncidentDetail, IncidentSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { hue } from '../ui/theme';
import { useToast } from '../ui/toast';
import type { SwipeAction } from '../ui/IncidentRow';
import { errorMessage } from './format';
import { buzz } from './haptics';

/**
 * The one obvious next step for a technician, offered as a swipe on the list row. The server still decides:
 * if the step is no longer allowed, the error is shown and nothing changes.
 */
export function useQuickAction() {
  const { user } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();

  const run = useCallback(
    (call: () => Promise<IncidentDetail>, done: string) =>
      call()
        .then((updated) => {
          queryClient.setQueryData(['incidents', updated.id], updated);
          queryClient.invalidateQueries({ queryKey: ['incidents'] });
          buzz('success');
          toast({ tone: 'success', title: `${updated.number}: ${done}` });
        })
        .catch((e) => toast({ tone: 'error', title: 'Not possible', message: errorMessage(e) })),
    [queryClient, toast],
  );

  return useCallback(
    (i: IncidentSummary): SwipeAction | undefined => {
      if (user?.role !== 'TECHNICIAN') return undefined;
      const mine = i.assigneeName === user.fullName;
      if (!i.assigneeName && (i.status === 'NEW' || i.status === 'CLASSIFIED')) {
        return { label: 'Take', icon: 'person-add', color: hue.indigo, onAction: () => void run(() => api.incidents.take(i.id), 'assigned to you') };
      }
      if (mine && i.status === 'ASSIGNED') {
        return { label: 'Acknowledge', icon: 'hand-left', color: hue.violet, onAction: () => void run(() => api.incidents.acknowledge(i.id), 'acknowledged') };
      }
      if (mine && i.status === 'ACKNOWLEDGED') {
        return { label: 'Start', icon: 'play', color: hue.grape, onAction: () => void run(() => api.incidents.start(i.id), 'work started') };
      }
      return undefined;
    },
    [user, run],
  );
}
