import { Paper, Stack, Text, Tooltip } from '@mantine/core';
import { IconChartLine } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { SlaForecast } from '../api/types';
import { PriorityBadge } from './Badges';
import classes from './SlaForecast.module.css';

const RISK_LABEL: Record<SlaForecast['risk'], string> = { LOW: 'Low risk', MEDIUM: 'Medium risk', HIGH: 'Breach likely' };

function time(iso: string) {
  const d = dayjs(iso);
  return d.isSame(dayjs(), 'day') ? d.format('HH:mm') : d.format('D MMM, HH:mm');
}

/**
 * Where the expected finish falls against the deadline: a carbon track from now to whichever is later, with the
 * deadline as a red tick and the forecast as a filled bar (red when it runs past the deadline).
 */
function FinishLine({ f }: { f: SlaForecast }) {
  const now = dayjs();
  const finish = dayjs(f.expectedFinish);
  const deadline = dayjs(f.deadline);
  const end = Math.max(finish.diff(now), deadline.diff(now), 1);
  const finishPct = Math.min(100, (finish.diff(now) / end) * 100);
  const deadlinePct = Math.min(100, (deadline.diff(now) / end) * 100);
  const late = finish.isAfter(deadline);
  return (
    <div className={classes.track} role="img" aria-label={`Expected finish ${time(f.expectedFinish)}, deadline ${time(f.deadline)}`}>
      <div className={classes.fill} data-late={late || undefined} style={{ width: `${finishPct}%` }} />
      <div className={classes.deadline} style={{ left: `${deadlinePct}%` }} />
    </div>
  );
}

/** Predictive SLA on the incident page (support staff): breach chance, expected finish vs deadline, and why. */
export function SlaForecastCard({ incidentId }: { incidentId: number }) {
  const forecast = useQuery({
    queryKey: ['incidents', incidentId, 'sla-forecast'],
    queryFn: () => api.incidents.slaForecast(incidentId),
    refetchInterval: 60_000,
  });
  const f = forecast.data;
  if (!f) return null;
  const pct = Math.round(f.probability * 100);

  return (
    <Paper withBorder p="lg" className={classes.card} data-risk={f.risk}>
      <div className={classes.head}>
        <IconChartLine size={18} stroke={1.6} />
        <Text fw={700}>SLA forecast</Text>
      </div>
      <div className={classes.figureRow}>
        <span className={classes.figure}>{pct}%</span>
        <span className={classes.risk} data-risk={f.risk}>
          {RISK_LABEL[f.risk]}
        </span>
      </div>
      <Text size="sm" c="dimmed" mb="sm">
        chance of missing the resolution deadline
      </Text>

      <FinishLine f={f} />
      <div className={classes.legend}>
        <span>
          <span className={classes.label}>Expected finish</span>
          <span className={classes.time}>{time(f.expectedFinish)}</span>
        </span>
        <span style={{ textAlign: 'right' }}>
          <span className={classes.label}>Deadline</span>
          <span className={classes.time}>{time(f.deadline)}</span>
        </span>
      </div>

      <ul className={classes.reasons}>
        {f.reasons.map((r) => (
          <li key={r}>{r}</li>
        ))}
      </ul>
      <Text size="xs" c="dimmed" mt="xs">
        {f.lowConfidence
          ? `Low confidence: only ${f.sampleSize} comparable resolved ${f.sampleSize === 1 ? 'incident' : 'incidents'}, so the estimate leans on the SLA target.`
          : `Learned from ${f.sampleSize} resolved ${f.basis.toLowerCase()}.`}
      </Text>
    </Paper>
  );
}

/** Dashboard list for support staff: open incidents most likely to miss their deadline, before they do. */
export function LikelyToBreach() {
  const forecasts = useQuery({
    queryKey: ['incidents', 'sla-forecasts'],
    queryFn: () => api.intelligence.slaForecasts('MEDIUM'),
    refetchInterval: 60_000,
  });
  const rows = forecasts.data ?? [];
  if (rows.length === 0) return null;

  return (
    <Paper withBorder p="md">
      <div className={classes.head}>
        <IconChartLine size={18} stroke={1.6} />
        <Text fw={700}>Likely to breach</Text>
        <Text size="sm" c="dimmed">
          · forecast from past resolution times
        </Text>
      </div>
      <Stack gap={0} mt="xs">
        {rows.slice(0, 6).map((f) => (
          <Link key={f.incidentId} to={`/incidents/${f.incidentId}`} className={classes.row}>
            <span className={classes.rowNumber}>{f.number}</span>
            <PriorityBadge priority={f.priority} />
            <span className={classes.rowTitle}>{f.title}</span>
            <Tooltip label={f.reasons.join(' · ')} multiline w={320}>
              <span className={classes.rowPct} data-risk={f.risk}>
                {Math.round(f.probability * 100)}%
              </span>
            </Tooltip>
            <span className={classes.rowWhen}>
              finish ~{time(f.expectedFinish)} · due {time(f.deadline)}
            </span>
          </Link>
        ))}
      </Stack>
    </Paper>
  );
}
