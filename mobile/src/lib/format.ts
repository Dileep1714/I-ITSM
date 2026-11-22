import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import { ApiError } from '../api/client';
import type { IncidentStatus } from '../api/types';

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
    if (error.fieldErrors.length > 0) return error.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('\n');
    return error.message;
  }
  return error instanceof Error ? error.message : 'Something went wrong';
}

export const OPEN_STATUSES: IncidentStatus[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'ON_HOLD'];
