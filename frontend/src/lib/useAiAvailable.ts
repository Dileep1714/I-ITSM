import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

/** Whether the server has an AI key configured: AI features are hidden when it does not. */
export function useAiAvailable() {
  const status = useQuery({ queryKey: ['ai', 'status'], queryFn: api.ai.status, staleTime: 5 * 60_000 });
  return status.data?.available === true;
}
