import { useQuery } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { RefreshControl, ScrollView, View } from 'react-native';
import { api } from '@/api/client';
import type { Availability } from '@/api/types';
import { humanize } from '@/lib/format';
import { Avatar, Badge, Empty, Loading, ProgressBar, Row, Section, T } from '@/ui/kit';
import { hue, useColors } from '@/ui/theme';

const AVAILABILITY_HUE: Record<Availability, string> = { AVAILABLE: hue.green, BUSY: hue.orange, OFF_DUTY: hue.gray, ON_LEAVE: hue.violet };

/** Administrator: who is available, how loaded each technician is, and how they are doing on SLAs. */
export default function TeamScreen() {
  const c = useColors();
  const report = useQuery({ queryKey: ['reports', 'technicians'], queryFn: api.reports.technicians });
  const technicians = [...(report.data?.technicians ?? [])].filter((t) => t.active).sort((a, b) => b.utilizationPercent - a.utilizationPercent);
  const available = technicians.filter((t) => t.availability === 'AVAILABLE').length;

  return (
    <>
      <Stack.Screen options={{ title: 'Team' }} />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={{ padding: 16, gap: 16 }}
        refreshControl={<RefreshControl refreshing={report.isRefetching} onRefresh={() => void report.refetch()} tintColor={c.primary} colors={[c.primary]} />}
      >
        {report.data && (
          <T muted v="small">
            {available} of {technicians.length} technicians available · sorted by workload
          </T>
        )}
        {report.isPending ? (
          <Loading />
        ) : technicians.length === 0 ? (
          <Empty icon="people-outline" text="No technicians yet." />
        ) : (
          <Section>
            {technicians.map((t) => {
              const load = t.utilizationPercent;
              return (
                <View key={t.technicianId} style={{ flexDirection: 'row', gap: 14, padding: 16 }}>
                  <Avatar name={t.fullName} color={AVAILABILITY_HUE[t.availability]} />
                  <View style={{ flex: 1, gap: 6 }}>
                    <Row style={{ justifyContent: 'space-between' }}>
                      <T style={{ fontWeight: '600', flex: 1 }} lines={1}>
                        {t.fullName}
                      </T>
                      <Badge label={humanize(t.availability)} color={AVAILABILITY_HUE[t.availability]} />
                    </Row>
                    <T v="small" muted>
                      {humanize(t.supportLevel)}
                    </T>
                    <Row style={{ justifyContent: 'space-between' }}>
                      <T v="small" muted>
                        Workload {t.activeIncidents}/{t.maxActiveIncidents}
                      </T>
                      <T v="small" muted>
                        {Math.round(load)}%
                      </T>
                    </Row>
                    <ProgressBar value={load} color={load >= 100 ? hue.red : load >= 75 ? hue.orange : hue.teal} />
                    <T v="small" muted>
                      {t.resolvedInPeriod} resolved · {t.slaBreaches} SLA breaches
                      {t.avgSatisfaction != null ? ` · ${t.avgSatisfaction.toFixed(1)}★` : ''}
                    </T>
                  </View>
                </View>
              );
            })}
          </Section>
        )}
      </ScrollView>
    </>
  );
}
