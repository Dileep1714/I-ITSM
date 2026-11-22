import dayjs from 'dayjs';
import type { AuditEntryView, IncidentStatus } from '../../api/types';
import { humanize } from '../../lib/format';
import classes from './StampStrip.module.css';

const STEPS: IncidentStatus[] = [
  'NEW',
  'CLASSIFIED',
  'ASSIGNED',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'RESOLVED',
  'VERIFIED',
  'CLOSED',
];

/** Today's stamps show the time only; older ones carry the date as well. */
function stampTime(iso: string) {
  const d = dayjs(iso);
  return d.isSame(dayjs(), 'day') ? d.format('HH:mm') : d.format('D MMM HH:mm');
}

/**
 * The incident's lifecycle as a row of rubber stamps, built from its audit history. On hold and cancelled are
 * stamped after the step the incident had reached.
 */
export function StampStrip({
  status,
  createdAt,
  history,
}: {
  status: IncidentStatus;
  createdAt: string;
  history: AuditEntryView[];
}) {
  const ordered = [...history].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));
  const reachedAt = new Map<IncidentStatus, string>([['NEW', createdAt]]);
  let lastMain: IncidentStatus = 'NEW';
  for (const h of ordered) {
    if (!h.newStatus) continue;
    // Lifecycle steps keep their first stamp; hold and cancel show their latest.
    if (!reachedAt.has(h.newStatus) || !STEPS.includes(h.newStatus)) reachedAt.set(h.newStatus, h.occurredAt);
    if (STEPS.includes(h.newStatus)) lastMain = h.newStatus;
  }

  const current = STEPS.includes(status) ? status : lastMain;
  const currentIndex = STEPS.indexOf(current);
  const interrupted = status === 'ON_HOLD' || status === 'CANCELLED';

  return (
    <ol className={classes.strip} aria-label="Lifecycle">
      {STEPS.map((step, index) => {
        const done = index < currentIndex || (index === currentIndex && (interrupted || step === 'CLOSED'));
        const state = done ? 'done' : index === currentIndex ? 'now' : 'todo';
        const at = reachedAt.get(step);
        return (
          <li
            key={step}
            className={classes.stamp}
            data-state={state}
            aria-current={state === 'now' ? 'step' : undefined}
          >
            <span className={classes.step}>{humanize(step)}</span>
            {state !== 'todo' && at && <span className={classes.time}>{stampTime(at)}</span>}
          </li>
        );
      }).flatMap((node, index) =>
        interrupted && index === currentIndex
          ? [
              node,
              <li key={status} className={classes.stamp} data-state={status === 'ON_HOLD' ? 'hold' : 'void'} aria-current="step">
                <span className={classes.step}>{humanize(status)}</span>
                {reachedAt.get(status) && <span className={classes.time}>{stampTime(reachedAt.get(status)!)}</span>}
              </li>,
            ]
          : [node],
      )}
    </ol>
  );
}
