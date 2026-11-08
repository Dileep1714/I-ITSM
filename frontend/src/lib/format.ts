import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import { ApiError } from '../api/client';
import type { IncidentStatus, PriorityLevel, SlaStatus } from '../api/types';

dayjs.extend(relativeTime);

/** "IN_PROGRESS" -> "In progress". */
export function humanize(value: string | null | undefined): string {
  if (!value) return '—';
  const s = value.replace(/_/g, ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dayjs(iso).format('D MMM YYYY, HH:mm') : '—';
}

export function fromNow(iso: string | null | undefined): string {
  return iso ? dayjs(iso).fromNow() : '—';
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.fieldErrors.length > 0) {
      return error.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('\n');
    }
    return error.message;
  }
  return error instanceof Error ? error.message : 'Something went wrong';
}

export const statusColor: Record<IncidentStatus, string> = {
  NEW: 'blue',
  CLASSIFIED: 'cyan',
  ASSIGNED: 'indigo',
  ACKNOWLEDGED: 'violet',
  IN_PROGRESS: 'grape',
  ON_HOLD: 'yellow',
  RESOLVED: 'teal',
  VERIFIED: 'green',
  CLOSED: 'gray',
  CANCELLED: 'dark',
};

export const priorityColor: Record<PriorityLevel, string> = {
  P1: 'red',
  P2: 'orange',
  P3: 'yellow',
  P4: 'gray',
};

export const slaColor: Record<SlaStatus, string> = {
  NOT_STARTED: 'gray',
  ON_TRACK: 'green',
  AT_RISK: 'orange',
  PAUSED: 'yellow',
  BREACHED: 'red',
  MET: 'teal',
};

export const OPEN_STATUSES: IncidentStatus[] = [
  'NEW',
  'CLASSIFIED',
  'ASSIGNED',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'ON_HOLD',
];

/** 42 → "42 min", 210 → "3.5 h", 3000 → "2.1 d"; null → "—". */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) return '—';
  if (minutes < 60) return `${Math.round(minutes)} min`;
  if (minutes < 60 * 24) return `${(minutes / 60).toFixed(1)} h`;
  return `${(minutes / 60 / 24).toFixed(1)} d`;
}
