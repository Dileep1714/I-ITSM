import Ionicons from '@expo/vector-icons/Ionicons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { router, Stack } from 'expo-router';
import { useState, type ReactNode } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { api } from '../api/client';
import type { IncidentSearch } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { OPEN_STATUSES } from '../lib/format';
import { useQuickAction } from '../lib/quickActions';
import { useLiveStatus } from '../realtime/LiveUpdates';
import { IncidentRow } from '../ui/IncidentRow';
import { Badge, Banner, Empty, Fab, ListRow, Loading, ProgressBar, Row, Section, T, type IconName } from '../ui/kit';
import { Ledger, LedgerCell } from '../ui/Docket';
import { LiveChip } from '../ui/LiveChip';
import { hue, priorityHue, useColors } from '../ui/theme';

function useCount(filters: IncidentSearch) {
  return useQuery({
    queryKey: ['incidents', 'count', filters],
    queryFn: () => api.incidents.search(filters, 0, 1),
    select: (page) => page.totalElements,
  }).data;
}

/** One count in the queue ledger; stamp-red counts (SLA at risk, breached) become alarms when non-zero. */
function Stat({ label, value, color, preset }: { label: string; value: string | number | undefined; icon?: IconName; color: string; preset?: string }) {
  const alarm = color === hue.red ? 'breach' : color === hue.orange ? 'risk' : undefined;
  return (
    <LedgerCell
      label={label}
      value={value}
      alarm={alarm}
      onPress={preset ? () => router.navigate({ pathname: '/incidents', params: { preset } }) : undefined}
    />
  );
}

function Stats({ title, children }: { title?: string; children: ReactNode }) {
  return <Ledger title={title}>{children}</Ledger>;
}

/** Live service status in one row: all operational, or how many services have known issues. */
function ServiceStatusRow() {
  const health = useQuery({ queryKey: ['catalog', 'health'], queryFn: api.catalog.health, refetchInterval: 60_000 });
  const issues = (health.data ?? []).filter((s) => s.health !== 'OPERATIONAL');
  return (
    <Section title="SERVICE STATUS">
      <ListRow
        icon={issues.length ? 'warning-outline' : 'checkmark-circle-outline'}
        iconColor={issues.length ? hue.red : undefined}
        title={issues.length ? `${issues.length} known ${issues.length === 1 ? 'issue' : 'issues'}` : 'All systems operational'}
        subtitle={issues.length ? issues.map((s) => s.name).join(', ') : 'Every IT service is working normally'}
        onPress={() => router.push('/services')}
        chevron
      />
    </Section>
  );
}

/** Shared frame: native header title, pull-to-refresh that reloads everything on screen. */
function HomeFrame({ title, children, fab }: { title: string; children: ReactNode; fab?: ReactNode }) {
  const c = useColors();
  const queryClient = useQueryClient();
  const [refreshing, setRefreshing] = useState(false);
  const refresh = async () => {
    setRefreshing(true);
    await queryClient.invalidateQueries();
    setRefreshing(false);
  };
  return (
    <View style={{ flex: 1 }}>
      <Stack.Screen options={{ title, headerRight: () => <LiveChip /> }} />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={{ padding: 16, gap: 20, paddingBottom: fab ? 96 : 32 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={c.primary} colors={[c.primary]} />}
      >
        {children}
      </ScrollView>
      {fab}
    </View>
  );
}

function Correlations() {
  const correlations = useQuery({ queryKey: ['correlations'], queryFn: api.intelligence.correlations });
  if (!correlations.data?.length) return null;
  return (
    <Section title="POSSIBLE COMMON CAUSE">
      {correlations.data.map((corr) => (
        <ListRow
          key={corr.rootServiceId}
          icon="git-network-outline"
          iconColor={hue.orange}
          title={corr.rootServiceName}
          subtitle={`${corr.incidents.length} open incidents · ${corr.explanation}`}
          onPress={() => corr.incidents[0] && router.push(`/incident/${corr.incidents[0].id}`)}
          chevron
        />
      ))}
    </Section>
  );
}

// ---------------------------------------------------------------- employee

export function EmployeeHome() {
  const { user } = useAuth();
  const open: IncidentSearch = { status: OPEN_STATUSES, scope: 'MINE' };
  const openCount = useCount(open);
  const resolvedCount = useCount({ status: ['RESOLVED'], scope: 'MINE' });
  const awaiting = useQuery({ queryKey: ['incidents', 'awaiting-me'], queryFn: () => api.incidents.search({ status: ['RESOLVED', 'ON_HOLD'] }, 0, 10) });
  const recent = useQuery({ queryKey: ['incidents', 'recent'], queryFn: () => api.incidents.list(0, 5) });

  return (
    <HomeFrame title={`Hi, ${user?.fullName.split(' ')[0]}`} fab={<Fab label="Report" icon="add" onPress={() => router.push('/report')} />}>
      <Stats title="Your reports">
        <Stat label="Open" value={openCount} icon="ticket-outline" color={hue.blue} preset="open" />
        <Stat label="Awaiting you" value={resolvedCount} icon="checkmark-done-outline" color={hue.teal} preset="resolved" />
      </Stats>
      <ServiceStatusRow />

      {(awaiting.data?.content.length ?? 0) > 0 && (
        <>
          <Banner tone="info" icon="chatbubble-ellipses-outline" title="Support is waiting for you">
            Confirm a fix or answer a question so your incidents can move on.
          </Banner>
          <Section title="NEEDS YOUR RESPONSE">
            {awaiting.data!.content.map((i) => (
              <IncidentRow key={i.id} incident={i} />
            ))}
          </Section>
        </>
      )}

      <Section
        title="RECENT"
        action={
          <Pressable onPress={() => router.navigate('/incidents')} hitSlop={8}>
            <T v="label" color={hue.indigo}>
              SEE ALL
            </T>
          </Pressable>
        }
      >
        {recent.isPending ? <Loading /> : recent.data?.content.length ? recent.data.content.map((i) => <IncidentRow key={i.id} incident={i} />) : <Empty stamp="No dockets" text="You haven't reported anything yet. Tap Report to start." />}
      </Section>
    </HomeFrame>
  );
}

// ---------------------------------------------------------------- technician

export function TechnicianHome() {
  const { user } = useAuth();
  const quickAction = useQuickAction();
  const mine: IncidentSearch = { status: OPEN_STATUSES, scope: 'MINE' };
  const counts = {
    open: useCount(mine),
    toAck: useCount({ status: ['ASSIGNED'], scope: 'MINE' }),
    atRisk: useCount({ ...mine, sla: ['AT_RISK'] }),
    breached: useCount({ ...mine, sla: ['BREACHED'] }),
  };
  const queue = useQuery({
    queryKey: ['incidents', 'my-queue'],
    queryFn: () => api.incidents.search(mine, 0, 8, 'priority.level,asc'),
  });
  const unassigned = useQuery({
    queryKey: ['incidents', 'unassigned'],
    queryFn: () => api.incidents.search({ status: ['NEW', 'CLASSIFIED'], unassigned: true, scope: 'ALL' }, 0, 5, 'priority.level,asc'),
  });

  return (
    <HomeFrame title={`Hi, ${user?.fullName.split(' ')[0]}`}>
      <Stats title="Your queue">
        <Stat label="My open" value={counts.open} icon="briefcase-outline" color={hue.blue} preset="open" />
        <Stat label="To acknowledge" value={counts.toAck} icon="hand-left-outline" color={hue.violet} preset="open" />
        <Stat label="SLA at risk" value={counts.atRisk} icon="time-outline" color={hue.orange} preset="atRisk" />
        <Stat label="Breached" value={counts.breached} icon="warning-outline" color={hue.red} preset="breached" />
      </Stats>
      <ServiceStatusRow />

      <Section title="MY QUEUE · HIGHEST PRIORITY FIRST">
        {queue.isPending ? (
          <Loading />
        ) : queue.data?.content.length ? (
          queue.data.content.map((i) => <IncidentRow key={i.id} incident={i} swipe={quickAction(i)} />)
        ) : (
          <Empty stamp="Queue clear" text="Your queue is empty." />
        )}
      </Section>

      {(unassigned.data?.content.length ?? 0) > 0 && (
        <Section title="UNASSIGNED · SWIPE RIGHT TO TAKE">
          {unassigned.data!.content.map((i) => (
            <IncidentRow key={i.id} incident={i} swipe={quickAction(i)} />
          ))}
        </Section>
      )}

      <Correlations />
    </HomeFrame>
  );
}

// ---------------------------------------------------------------- administrator

const fmtMinutes = (m: number | null | undefined) => (m == null ? '—' : m < 60 ? `${Math.round(m)}m` : m < 1440 ? `${(m / 60).toFixed(1)}h` : `${(m / 1440).toFixed(1)}d`);

export function AdminOverview() {
  const c = useColors();
  const overview = useQuery({ queryKey: ['reports', 'overview'], queryFn: api.reports.overview });
  const unassigned = useQuery({
    queryKey: ['incidents', 'unassigned'],
    queryFn: () => api.incidents.search({ status: ['NEW', 'CLASSIFIED'], unassigned: true, scope: 'ALL' }, 0, 5, 'priority.level,asc'),
  });
  const violations = useQuery({ queryKey: ['admin', 'sla-violations', 'latest'], queryFn: () => api.admin.slaViolations(0, 1) });
  const t = overview.data?.totals;
  const compliance = t?.slaCompliancePercent;

  return (
    <HomeFrame title="Operations">
      <Stats title="Service desk">
        <Stat label="Open now" value={t?.openNow} icon="ticket-outline" color={hue.blue} preset="open" />
        <Stat label="SLA breached" value={t?.slaBreached} icon="warning-outline" color={hue.red} preset="breached" />
        <Stat label="SLA compliance" value={compliance == null ? '—' : `${Math.round(compliance)}%`} icon="shield-checkmark-outline" color={hue.teal} />
        <Stat label="Satisfaction" value={t?.avgSatisfaction == null ? '—' : `${t.avgSatisfaction.toFixed(1)}★`} icon="star-outline" color={hue.yellow} />
        <Stat label="Avg response" value={fmtMinutes(t?.avgResponseMinutes)} icon="flash-outline" color={hue.violet} />
        <Stat label="Avg resolution" value={fmtMinutes(t?.avgResolutionMinutes)} icon="hourglass-outline" color={hue.grape} />
      </Stats>
      <ServiceStatusRow />

      {overview.data && (
        <Section title="OPEN BY PRIORITY">
          {overview.data.byPriority.map((p) => {
            const max = Math.max(1, ...overview.data!.byPriority.map((x) => x.open));
            return (
              <View key={p.priority} style={{ paddingHorizontal: 16, paddingVertical: 10, gap: 6 }}>
                <Row style={{ justifyContent: 'space-between' }}>
                  <T v="small" style={{ fontWeight: '600' }}>
                    {p.priority}
                  </T>
                  <T v="small" muted>
                    {p.open} open · {p.breached} breached
                  </T>
                </Row>
                <ProgressBar value={(p.open / max) * 100} color={priorityHue[p.priority]} />
              </View>
            );
          })}
        </Section>
      )}

      <Section title="NEEDS ASSIGNMENT">
        {unassigned.isPending ? (
          <Loading />
        ) : unassigned.data?.content.length ? (
          unassigned.data.content.map((i) => <IncidentRow key={i.id} incident={i} />)
        ) : (
          <Empty stamp="All assigned" text="Every open incident has an owner." />
        )}
      </Section>

      <Section title="MONITORING">
        <ListRow
          icon="alert-circle-outline"
          iconColor={c.danger}
          title="SLA violations"
          subtitle={violations.data ? `${violations.data.totalElements} recorded` : undefined}
          onPress={() => router.push('/sla-violations')}
          chevron
        />
        <ListRow icon="pulse-outline" title="Activity log" subtitle="Every action across all incidents" onPress={() => router.push('/activity')} chevron />
        <ListRow icon="people-outline" title="Team workload" subtitle="Availability and load per technician" onPress={() => router.navigate('/team')} chevron />
      </Section>

      <Correlations />

      {overview.data && (
        <T v="small" muted style={{ textAlign: 'center' }}>
          Reporting period {overview.data.period.from.slice(0, 10)} to {overview.data.period.to.slice(0, 10)}
        </T>
      )}
    </HomeFrame>
  );
}

const styles = StyleSheet.create({
  stat: { flexGrow: 1, flexBasis: '45%', borderRadius: 16, padding: 14, gap: 4, overflow: 'hidden' },
  statIcon: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center', marginBottom: 4 },
});
