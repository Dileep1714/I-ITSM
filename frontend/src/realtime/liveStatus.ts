import { createContext, useContext } from 'react';

export type LiveStatus = 'connecting' | 'live' | 'offline';

export const LiveContext = createContext<LiveStatus>('offline');

/** Whether the real-time connection is up, for the "Live" indicator. */
export function useLiveStatus() {
  return useContext(LiveContext);
}
