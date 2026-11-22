import { Paper, Stack, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { ServiceHealth, ServiceHealthLevel } from '../api/types';
import { PriorityBadge } from '../components/Badges';
import { QueryState } from '../components/QueryState';
import { humanize } from '../lib/format';
import classes from './ServiceMapPage.module.css';

const NODE_W = 152;
const NODE_H = 66;
const GAP_X = 26;
const GAP_Y = 84;
const PAD = 24;

const HEALTH_LABEL: Record<ServiceHealthLevel, string> = {
  OPERATIONAL: 'Operational',
  MAINTENANCE: 'Maintenance',
  IMPACTED: 'Impacted',
  DEGRADED: 'Degraded',
  OUTAGE: 'Outage',
};
const TROUBLE: ServiceHealthLevel[] = ['OUTAGE', 'DEGRADED'];

interface Placed {
  s: ServiceHealth;
  x: number;
  y: number;
}

/**
 * Layered layout: services that depend on nothing sit on the bottom row (infrastructure), every other service sits
 * one row above its highest dependency. Within a row, nodes are ordered by the average position of their
 * neighbours (two barycentre sweeps) to keep edges from crossing.
 */
function layout(services: ServiceHealth[]) {
  const byId = new Map(services.map((s) => [s.id, s]));
  const level = new Map<number, number>();
  const levelOf = (s: ServiceHealth): number => {
    const known = level.get(s.id);
    if (known !== undefined) return known;
    const deps = s.dependsOn.map((id) => byId.get(id)).filter(Boolean) as ServiceHealth[];
    const l = deps.length === 0 ? 0 : 1 + Math.max(...deps.map(levelOf));
    level.set(s.id, l);
    return l;
  };
  services.forEach(levelOf);
  const top = Math.max(0, ...level.values());
  const rows: ServiceHealth[][] = Array.from({ length: top + 1 }, () => []);
  [...services].sort((a, b) => a.name.localeCompare(b.name)).forEach((s) => rows[level.get(s.id)!].push(s));

  const order = new Map<number, number>();
  rows.forEach((row) => row.forEach((s, i) => order.set(s.id, i)));
  const dependents = (id: number) => services.filter((o) => o.dependsOn.includes(id)).map((o) => o.id);
  for (let sweep = 0; sweep < 2; sweep++) {
    rows.forEach((row) => {
      const score = (s: ServiceHealth) => {
        const n = [...s.dependsOn, ...dependents(s.id)].map((id) => order.get(id) ?? 0);
        return n.length ? n.reduce((a, b) => a + b, 0) / n.length : (order.get(s.id) ?? 0);
      };
      row.sort((a, b) => score(a) - score(b));
      row.forEach((s, i) => order.set(s.id, i));
    });
  }

  const widest = Math.max(...rows.map((r) => r.length));
  const width = PAD * 2 + widest * NODE_W + (widest - 1) * GAP_X;
  const height = PAD * 2 + rows.length * NODE_H + (rows.length - 1) * GAP_Y;
  const placed = new Map<number, Placed>();
  rows.forEach((row, l) => {
    const rowWidth = row.length * NODE_W + (row.length - 1) * GAP_X;
    const startX = (width - rowWidth) / 2;
    const y = PAD + (top - l) * (NODE_H + GAP_Y); // higher levels on top
    row.forEach((s, i) => placed.set(s.id, { s, x: startX + i * (NODE_W + GAP_X), y }));
  });
  return { placed, width, height };
}

/** Everything connected to `id` along dependency edges: what it needs, and what needs it. */
function chainOf(services: ServiceHealth[], id: number) {
  const byId = new Map(services.map((s) => [s.id, s]));
  const chain = new Set<number>([id]);
  const walk = (start: number, next: (s: ServiceHealth) => number[]) => {
    const stack = [start];
    while (stack.length) {
      const s = byId.get(stack.pop()!);
      if (!s) continue;
      for (const n of next(s)) {
        if (!chain.has(n)) {
          chain.add(n);
          stack.push(n);
        }
      }
    }
  };
  walk(id, (s) => s.dependsOn);
  walk(id, (s) => services.filter((o) => o.dependsOn.includes(s.id)).map((o) => o.id));
  return chain;
}

/** Service map and status page: live health of every IT service and how problems spread along dependencies. */
export function ServiceMapPage() {
  const health = useQuery({ queryKey: ['catalog', 'health'], queryFn: api.catalog.health, refetchInterval: 30_000 });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const services = useMemo(() => health.data ?? [], [health.data]);
  const graph = useMemo(() => (services.length ? layout(services) : null), [services]);
  const chain = useMemo(() => (selectedId != null ? chainOf(services, selectedId) : null), [services, selectedId]);
  const selected = services.find((s) => s.id === selectedId) ?? null;
  const issues = services.filter((s) => s.health !== 'OPERATIONAL');

  return (
    <Stack maw={1300}>
      <Title order={2}>Service map</Title>
      <QueryState query={health}>
        {/* Status page */}
        <Paper withBorder p="md" className={classes.status} data-ok={issues.length === 0 || undefined}>
          {issues.length === 0 ? (
            <Text fw={700}>All systems operational</Text>
          ) : (
            <>
              <Text fw={700} mb={6}>
                Known issues · IT is aware of these
              </Text>
              <ul className={classes.issues}>
                {issues.map((s) => (
                  <li key={s.id}>
                    <button type="button" className={classes.issueButton} onClick={() => setSelectedId(s.id)}>
                      <span className={classes.healthMark} data-health={s.health}>
                        {HEALTH_LABEL[s.health]}
                      </span>
                      <span className={classes.issueName}>{s.name}</span>
                      <span className={classes.issueWhy}>{s.reasons[s.reasons.length - 1] ?? ''}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </Paper>

        <div className={classes.body}>
          {/* Map */}
          <Paper withBorder p={0} className={classes.mapWrap}>
            {graph && (
              <svg
                className={classes.map}
                viewBox={`0 0 ${graph.width} ${graph.height}`}
                style={{ minWidth: Math.min(graph.width, 720) }}
                role="img"
                aria-label="Dependency map of IT services"
              >
                <defs>
                  <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                    <path d="M0,0 L10,5 L0,10 z" className={classes.arrow} />
                  </marker>
                  <marker id="arrow-bad" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                    <path d="M0,0 L10,5 L0,10 z" className={classes.arrowBad} />
                  </marker>
                </defs>
                {/* Edges: from a service down to what it depends on. Red when the dependency is in trouble. */}
                {services.flatMap((s) =>
                  s.dependsOn.map((depId) => {
                    const from = graph.placed.get(s.id);
                    const to = graph.placed.get(depId);
                    if (!from || !to) return null;
                    const bad = TROUBLE.includes(to.s.health);
                    const dim = chain && !(chain.has(s.id) && chain.has(depId));
                    const x1 = from.x + NODE_W / 2;
                    const y1 = from.y + NODE_H;
                    const x2 = to.x + NODE_W / 2;
                    const y2 = to.y;
                    const my = (y1 + y2) / 2;
                    return (
                      <path
                        key={`${s.id}-${depId}`}
                        d={`M${x1},${y1} C${x1},${my} ${x2},${my} ${x2},${y2 - 2}`}
                        className={classes.edge}
                        data-bad={bad || undefined}
                        data-dim={dim || undefined}
                        markerEnd={`url(#${bad ? 'arrow-bad' : 'arrow'})`}
                      />
                    );
                  }),
                )}
                {/* Nodes */}
                {[...graph.placed.values()].map(({ s, x, y }) => (
                  <g
                    key={s.id}
                    transform={`translate(${x},${y})`}
                    className={classes.node}
                    data-health={s.health}
                    data-selected={selectedId === s.id || undefined}
                    data-dim={(chain && !chain.has(s.id)) || undefined}
                    onClick={() => setSelectedId(selectedId === s.id ? null : s.id)}
                    onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && setSelectedId(s.id)}
                    tabIndex={0}
                    role="button"
                    aria-label={`${s.name}: ${HEALTH_LABEL[s.health]}`}
                  >
                    <rect width={NODE_W} height={NODE_H} className={classes.nodeBox} />
                    {s.health === 'OUTAGE' && <rect x={3} y={3} width={NODE_W - 6} height={NODE_H - 6} className={classes.nodeInner} />}
                    <text x={12} y={26} className={classes.nodeName}>
                      {s.name}
                    </text>
                    <text x={12} y={48} className={classes.nodeHealth}>
                      {HEALTH_LABEL[s.health].toUpperCase()}
                      {s.openIncidents > 0 ? ` · ${s.openIncidents} OPEN` : ''}
                    </text>
                  </g>
                ))}
              </svg>
            )}
            <div className={classes.legend}>
              Arrows point from a service to what it depends on. Red lines carry a problem upward to everything that
              relies on it. Select a service to follow its chain.
            </div>
          </Paper>

          {/* Details */}
          <Paper withBorder p="lg" className={classes.panel}>
            {!selected ? (
              <Text size="sm" c="dimmed">
                Select a service on the map to see why it is in this state, what it depends on, and what it would take
                down with it.
              </Text>
            ) : (
              <Stack gap="sm">
                <div>
                  <span className={classes.healthMark} data-health={selected.health}>
                    {HEALTH_LABEL[selected.health]}
                  </span>
                  <Text fw={800} fz={22} mt={6} lh={1.2}>
                    {selected.name}
                  </Text>
                  <Text size="sm" c="dimmed">
                    {selected.description}
                  </Text>
                </div>
                {selected.reasons.length > 0 && (
                  <ul className={classes.reasons}>
                    {selected.reasons.map((r) => (
                      <li key={r}>{r}</li>
                    ))}
                  </ul>
                )}
                <div className={classes.facts}>
                  <div>
                    <span className={classes.label}>Criticality</span>
                    <span>{humanize(selected.criticality)}</span>
                  </div>
                  <div>
                    <span className={classes.label}>Blast radius</span>
                    <span>
                      {selected.blastRadius === 0
                        ? 'Nothing depends on it'
                        : `${selected.blastRadius} ${selected.blastRadius === 1 ? 'service depends' : 'services depend'} on it`}
                    </span>
                  </div>
                  <div>
                    <span className={classes.label}>Open incidents</span>
                    <span className={classes.figureRow}>
                      <span className={classes.figure}>{selected.openIncidents}</span>
                      {selected.worstPriority && <PriorityBadge priority={selected.worstPriority} />}
                      {selected.breachedIncidents > 0 && (
                        <span className={classes.breached}>{selected.breachedIncidents} past SLA</span>
                      )}
                    </span>
                  </div>
                  <div>
                    <span className={classes.label}>Depends on</span>
                    <span>
                      {selected.dependsOn.length === 0
                        ? 'Nothing (infrastructure)'
                        : selected.dependsOn.map((id) => services.find((s) => s.id === id)?.name).join(', ')}
                    </span>
                  </div>
                </div>
                {selected.openIncidents > 0 && (
                  <Link to={`/incidents?serviceId=${selected.id}&scope=ALL`} className={classes.link}>
                    View its open incidents →
                  </Link>
                )}
              </Stack>
            )}
          </Paper>
        </div>
      </QueryState>
    </Stack>
  );
}
