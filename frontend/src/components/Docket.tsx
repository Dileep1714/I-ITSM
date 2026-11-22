import dayjs from 'dayjs';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { IncidentStatus } from '../api/types';
import { humanize } from '../lib/format';
import classes from './Docket.module.css';

// ----- queue ledger -----------------------------------------------------------------------------------------------

export interface LedgerCell {
  label: string;
  value: number | undefined;
  to: string;
  /** Non-zero values are alarms: stamp red, with a corner stamp. */
  alarm?: 'risk' | 'breach';
  foot?: string;
}

/**
 * The queue's counts as one ruled docket sheet: a typed date line, then each count as a large typed figure.
 * Zeros recede; non-zero alarms are stamped. Every cell opens the matching filtered list.
 */
export function QueueLedger({ title, cells }: { title: string; cells: LedgerCell[] }) {
  return (
    <nav className={classes.ledger} aria-label={title}>
      <div className={classes.ledgerHead}>
        <span>{title.toUpperCase()}</span>
        <span>{dayjs().format('ddd D MMM YYYY · HH:mm').toUpperCase()}</span>
      </div>
      <div className={classes.ledgerCells}>
        {cells.map((c) => {
          const zero = c.value === 0;
          const alarm = !!c.alarm && !!c.value;
          return (
            <Link
              key={c.label}
              to={c.to}
              className={classes.cell}
              data-zero={zero || undefined}
              data-alarm={alarm || undefined}
              aria-label={`${c.label}: ${c.value ?? 'loading'}`}
            >
              <span className={classes.cellLabel}>{c.label}</span>
              {alarm && (
                <span className={classes.cornerStamp} data-double={c.alarm === 'breach' || undefined}>
                  {c.alarm === 'breach' ? 'Late' : 'Act now'}
                </span>
              )}
              <span className={classes.figure}>{c.value === undefined ? '··' : String(c.value).padStart(2, '0')}</span>
              {c.foot && <span className={classes.cellFoot}>{c.foot}</span>}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

// ----- section heading ---------------------------------------------------------------------------------------------

/** A section heading with its count as a typed figure, and an optional action on the right. */
export function SectionHead({ title, count, action }: { title: string; count?: number; action?: ReactNode }) {
  return (
    <div className={classes.sectionHead}>
      <span className={classes.sectionTitle}>
        {title}
        {count !== undefined && <span className={classes.sectionCount}>{String(count).padStart(2, '0')}</span>}
      </span>
      {action}
    </div>
  );
}

// ----- miniature lifecycle strip -----------------------------------------------------------------------------------

const STEPS: IncidentStatus[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'CLOSED'];

/**
 * The incident page's stamp strip in miniature, for list rows: one tick per lifecycle step, filled up to the
 * current step, which is a larger square (dashed red while on hold). Cancelled incidents fade.
 */
export function MiniStrip({ status }: { status: IncidentStatus }) {
  const hold = status === 'ON_HOLD';
  const ended = status === 'CANCELLED';
  const current = hold ? STEPS.indexOf('IN_PROGRESS') : ended ? -1 : STEPS.indexOf(status);
  const closed = status === 'CLOSED';
  return (
    <span className={classes.mini} data-ended={ended || undefined} role="img" aria-label={`Lifecycle: ${humanize(status)}`}>
      {STEPS.map((step, i) => {
        const state =
          i < current || (closed && i === current) ? 'done' : i === current ? (hold ? 'hold' : 'now') : 'todo';
        return <span key={step} className={classes.tick} data-state={state} />;
      })}
    </span>
  );
}

// ----- stamped empty state -----------------------------------------------------------------------------------------

/** An empty list as a blank ruled sheet with a rubber stamp, e.g. ALL CLEAR or NO DOCKETS. */
export function StampedEmpty({ stamp, text, action }: { stamp: string; text?: string; action?: ReactNode }) {
  return (
    <div className={classes.empty}>
      <span className={classes.emptyStamp}>{stamp}</span>
      {text && <span className={classes.emptyText}>{text}</span>}
      {action}
    </div>
  );
}
