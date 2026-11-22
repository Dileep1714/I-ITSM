import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, Stack } from 'expo-router';
import { FlatList, Pressable, RefreshControl, View } from 'react-native';
import { api } from '@/api/client';
import { errorMessage, formatDateTime, fromNow, humanize } from '@/lib/format';
import { Empty, ListRow, Loading, PriorityDot, T } from '@/ui/kit';
import { useColors } from '@/ui/theme';
import { useToast } from '@/ui/toast';

/** Administrator: every recorded SLA breach, newest first, with a button to run the SLA check now. */
export default function SlaViolationsScreen() {
  const c = useColors();
  const toast = useToast();
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ['admin', 'sla-violations'], queryFn: () => api.admin.slaViolations(0, 100) });

  const runCheck = useMutation({
    mutationFn: api.admin.runSlaMonitor,
    onSuccess: (r) => {
      queryClient.invalidateQueries();
      toast({
        tone: 'success',
        title: `SLA check done: ${r.checked} incidents checked`,
        message: `${r.warnings} warnings · ${r.breaches} breaches · ${r.escalations} escalations · ${r.assigned} assigned`,
      });
    },
    onError: (e) => toast({ tone: 'error', title: 'SLA check failed', message: errorMessage(e) }),
  });

  return (
    <>
      <Stack.Screen
        options={{
          title: 'SLA violations',
          headerLargeTitle: false,
          headerRight: () => (
            <Pressable onPress={() => runCheck.mutate()} disabled={runCheck.isPending} hitSlop={10}>
              <T v="small" color={c.primary} style={{ fontWeight: '600' }}>
                {runCheck.isPending ? 'Checking…' : 'Run check'}
              </T>
            </Pressable>
          ),
        }}
      />
      <FlatList
        contentInsetAdjustmentBehavior="automatic"
        data={list.data?.content ?? []}
        keyExtractor={(v) => String(v.id)}
        contentContainerStyle={{ padding: 16 }}
        ItemSeparatorComponent={() => <View style={{ height: 1, backgroundColor: c.divider, marginLeft: 70 }} />}
        ListEmptyComponent={list.isPending ? <Loading /> : <Empty icon="shield-checkmark-outline" text="No SLA has been breached." />}
        refreshControl={<RefreshControl refreshing={list.isRefetching} onRefresh={() => void list.refetch()} tintColor={c.primary} colors={[c.primary]} />}
        renderItem={({ item: v, index }) => (
          <View
            style={{
              backgroundColor: c.surface,
              borderTopLeftRadius: index === 0 ? 16 : 0,
              borderTopRightRadius: index === 0 ? 16 : 0,
              borderBottomLeftRadius: index === (list.data?.content.length ?? 0) - 1 ? 16 : 0,
              borderBottomRightRadius: index === (list.data?.content.length ?? 0) - 1 ? 16 : 0,
              overflow: 'hidden',
            }}
          >
            <ListRow
              leading={<PriorityDot priority={v.priority} />}
              title={`${v.incidentNumber} · ${v.incidentTitle}`}
              subtitle={`${humanize(v.kind)} deadline missed · ${v.serviceName}\nDue ${formatDateTime(v.deadline)} · detected ${fromNow(v.detectedAt)}`}
              onPress={() => router.push(`/incident/${v.incidentId}`)}
              chevron
            />
          </View>
        )}
      />
    </>
  );
}
