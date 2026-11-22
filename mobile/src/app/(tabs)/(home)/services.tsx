import { useQuery } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { api } from '@/api/client';
import type { ServiceHealth, ServiceHealthLevel } from '@/api/types';
import { humanize } from '@/lib/format';
import { Loading, T } from '@/ui/kit';
import { FIGURE, useColors } from '@/ui/theme';

const LABEL: Record<ServiceHealthLevel, string> = {
  OPERATIONAL: 'Operational',
  MAINTENANCE: 'Maintenance',
  IMPACTED: 'Impacted',
  DEGRADED: 'Degraded',
  OUTAGE: 'Outage',
};

/** Health as a stamp: double red rule for an outage, single for degraded, dashed for impacted from upstream. */
function HealthMark({ health }: { health: ServiceHealthLevel }) {
  const c = useColors();
  const bad = health === 'OUTAGE' || health === 'DEGRADED' || health === 'IMPACTED';
  const ink = bad ? c.danger : c.primary;
  const outer = { borderColor: ink, borderWidth: health === 'DEGRADED' ? 2 : 1.25, borderStyle: health === 'IMPACTED' || health === 'MAINTENANCE' ? ('dashed' as const) : ('solid' as const) };
  const label = <Text style={[styles.mark, { color: ink }]}>{LABEL[health].toUpperCase()}</Text>;
  if (health === 'OUTAGE') {
    return (
      <View style={[styles.markBox, { borderColor: ink, borderWidth: 1.25, padding: 1.5 }]}>
        <View style={{ borderWidth: 1.25, borderColor: ink, paddingHorizontal: 6, paddingVertical: 1 }}>{label}</View>
      </View>
    );
  }
  return <View style={[styles.markBox, outer, { paddingHorizontal: 7, paddingVertical: 2 }]}>{label}</View>;
}

/**
 * Service status (the web app's service map, as a phone list): every IT service with its live health, why, what
 * it depends on and how many services depend on it. Known issues first.
 */
export default function ServicesScreen() {
  const c = useColors();
  const health = useQuery({ queryKey: ['catalog', 'health'], queryFn: api.catalog.health, refetchInterval: 30_000 });
  const services = health.data ?? [];
  const issues = services.filter((s) => s.health !== 'OPERATIONAL');
  const nameOf = (id: number) => services.find((s) => s.id === id)?.name ?? '';

  return (
    <>
      <Stack.Screen options={{ title: 'Service status' }} />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={{ padding: 16, gap: 16 }}
        refreshControl={<RefreshControl refreshing={health.isRefetching} onRefresh={() => void health.refetch()} tintColor={c.primary} colors={[c.primary]} />}
      >
        {health.isPending ? (
          <Loading />
        ) : (
          <>
            <View style={[styles.summary, { backgroundColor: c.surface, borderColor: issues.length ? c.danger : c.border, borderWidth: issues.length ? 2 : StyleSheet.hairlineWidth }]}>
              <T v="title">{issues.length ? `${issues.length} known ${issues.length === 1 ? 'issue' : 'issues'}` : 'All systems operational'}</T>
              <T v="small" muted>
                {issues.length ? 'IT is aware of these. No need to report them again.' : 'Every IT service is working normally.'}
              </T>
            </View>

            {services.map((s: ServiceHealth) => (
              <View key={s.id} style={[styles.card, { backgroundColor: c.surface, borderColor: c.border }]} accessible accessibilityLabel={`${s.name}: ${LABEL[s.health]}`}>
                <View style={styles.cardHead}>
                  <T v="heading" style={{ flex: 1 }}>
                    {s.name}
                  </T>
                  <HealthMark health={s.health} />
                </View>
                {s.reasons.length > 0 && (
                  <T v="small" muted>
                    {s.reasons.join(' · ')}
                  </T>
                )}
                <View style={[styles.facts, { borderTopColor: c.divider }]}>
                  <Fact label="Open" value={String(s.openIncidents).padStart(2, '0')} figure />
                  <Fact label="Depends on" value={s.dependsOn.length ? s.dependsOn.map(nameOf).join(', ') : 'Nothing'} />
                  <Fact label="Blast radius" value={s.blastRadius === 0 ? 'None' : `${s.blastRadius} ${s.blastRadius === 1 ? 'service' : 'services'}`} />
                </View>
                <T v="small" muted>
                  {humanize(s.criticality)} criticality
                </T>
              </View>
            ))}
          </>
        )}
      </ScrollView>
    </>
  );
}

function Fact({ label, value, figure }: { label: string; value: string; figure?: boolean }) {
  const c = useColors();
  return (
    <View style={{ flex: 1, gap: 2 }}>
      <Text style={[styles.factLabel, { color: c.primary }]}>{label.toUpperCase()}</Text>
      <Text style={figure ? { fontFamily: FIGURE, fontSize: 20, color: c.primary } : { fontSize: 13, color: c.text }} numberOfLines={2}>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  summary: { borderRadius: 14, padding: 16, gap: 4 },
  card: { borderRadius: 14, borderWidth: StyleSheet.hairlineWidth, padding: 16, gap: 8 },
  cardHead: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  markBox: { borderRadius: 1 },
  mark: { fontSize: 10.5, fontWeight: '800', letterSpacing: 1.1 },
  facts: { flexDirection: 'row', gap: 12, borderTopWidth: StyleSheet.hairlineWidth, paddingTop: 10, marginTop: 2 },
  factLabel: { fontSize: 10, fontWeight: '800', letterSpacing: 1 },
});
