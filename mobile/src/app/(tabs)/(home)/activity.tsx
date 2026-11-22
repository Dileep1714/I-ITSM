import { useInfiniteQuery } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { FlatList, RefreshControl, View } from 'react-native';
import { api } from '@/api/client';
import { formatDateTime, humanize } from '@/lib/format';
import { Empty, Loading, Row, T } from '@/ui/kit';
import { statusHue, useColors } from '@/ui/theme';

/** Administrator: the audit trail across all incidents, as a timeline. */
export default function ActivityScreen() {
  const c = useColors();
  const audit = useInfiniteQuery({
    queryKey: ['admin', 'audit'],
    queryFn: ({ pageParam }) => api.admin.audit(pageParam, 40),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
  const items = audit.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <>
      <Stack.Screen options={{ title: 'Activity log', headerLargeTitle: false }} />
      <FlatList
        contentInsetAdjustmentBehavior="automatic"
        data={items}
        keyExtractor={(a) => String(a.id)}
        contentContainerStyle={{ padding: 16 }}
        ListEmptyComponent={audit.isPending ? <Loading /> : <Empty icon="pulse-outline" text="No activity yet." />}
        ListFooterComponent={audit.isFetchingNextPage ? <Loading /> : undefined}
        onEndReached={() => audit.hasNextPage && !audit.isFetchingNextPage && void audit.fetchNextPage()}
        refreshControl={<RefreshControl refreshing={audit.isRefetching} onRefresh={() => void audit.refetch()} tintColor={c.primary} colors={[c.primary]} />}
        renderItem={({ item: a, index }) => {
          const dot = a.newStatus ? statusHue[a.newStatus] : c.primary;
          return (
            <Row gap={14} style={{ alignItems: 'stretch' }}>
              {/* timeline rail */}
              <View style={{ width: 14, alignItems: 'center' }}>
                <View style={{ width: 2, flex: 1, backgroundColor: index === 0 ? 'transparent' : c.border }} />
                <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: dot }} />
                <View style={{ width: 2, flex: 4, backgroundColor: index === items.length - 1 ? 'transparent' : c.border }} />
              </View>
              <View style={{ flex: 1, paddingVertical: 8, gap: 2 }}>
                <T v="small" style={{ fontWeight: '600' }}>
                  {a.incidentNumber} · {humanize(a.action)}
                </T>
                <T v="small">{a.description}</T>
                <T v="small" muted>
                  {a.actorName ?? 'System'} · {formatDateTime(a.occurredAt)}
                  {a.previousStatus && a.newStatus && a.previousStatus !== a.newStatus ? ` · ${humanize(a.previousStatus)} → ${humanize(a.newStatus)}` : ''}
                </T>
              </View>
            </Row>
          );
        }}
      />
    </>
  );
}
