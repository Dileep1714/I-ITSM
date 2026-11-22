import { useInfiniteQuery } from '@tanstack/react-query';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { FlatList, RefreshControl, View, type ViewStyle } from 'react-native';
import { api } from '@/api/client';
import type { IncidentSearch } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { errorMessage, OPEN_STATUSES } from '@/lib/format';
import { useQuickAction } from '@/lib/quickActions';
import { IncidentRow } from '@/ui/IncidentRow';
import { Banner, Chips, Empty, Fab, Loading, Segmented, T } from '@/ui/kit';
import { useColors, type Palette } from '@/ui/theme';

const PRESETS = {
  open: { label: 'Open', filters: { status: OPEN_STATUSES } },
  critical: { label: 'P1 / P2', filters: { status: OPEN_STATUSES, priority: ['P1', 'P2'] } },
  atRisk: { label: 'SLA at risk', filters: { status: OPEN_STATUSES, sla: ['AT_RISK'] } },
  breached: { label: 'Breached', filters: { status: OPEN_STATUSES, sla: ['BREACHED'] } },
  unassigned: { label: 'Unassigned', filters: { status: ['NEW', 'CLASSIFIED'], unassigned: true } },
  resolved: { label: 'Resolved', filters: { status: ['RESOLVED', 'VERIFIED'] } },
  all: { label: 'All', filters: {} },
} satisfies Record<string, { label: string; filters: IncidentSearch }>;
type Preset = keyof typeof PRESETS;

const PAGE_SIZE = 20;

/** Rows of one rounded group: only the first and last row have rounded corners. */
function grouped(index: number, count: number, c: Palette): ViewStyle {
  return {
    backgroundColor: c.surface,
    marginHorizontal: 16,
    overflow: 'hidden',
    borderTopLeftRadius: index === 0 ? 16 : 0,
    borderTopRightRadius: index === 0 ? 16 : 0,
    borderBottomLeftRadius: index === count - 1 ? 16 : 0,
    borderBottomRightRadius: index === count - 1 ? 16 : 0,
  };
}

export default function IncidentsScreen() {
  const c = useColors();
  const { user } = useAuth();
  const quickAction = useQuickAction();
  const params = useLocalSearchParams<{ preset?: string }>();
  const role = user?.role;
  const [preset, setPreset] = useState<Preset>('open');
  const [scope, setScope] = useState<'MINE' | 'ALL'>(role === 'ADMINISTRATOR' ? 'ALL' : 'MINE');
  const [text, setText] = useState('');
  const [q, setQ] = useState('');

  useEffect(() => {
    if (params.preset && params.preset in PRESETS) setPreset(params.preset as Preset);
  }, [params.preset]);

  useEffect(() => {
    const t = setTimeout(() => setQ(text.trim()), 350);
    return () => clearTimeout(t);
  }, [text]);

  // "Unassigned" only makes sense across everyone's incidents.
  const effectiveScope = preset === 'unassigned' ? 'ALL' : scope;
  const filters: IncidentSearch = useMemo(() => ({ ...PRESETS[preset].filters, q: q || undefined, scope: effectiveScope }), [preset, q, effectiveScope]);

  const incidents = useInfiniteQuery({
    queryKey: ['incidents', 'feed', filters],
    queryFn: ({ pageParam }) => api.incidents.search(filters, pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
  const items = incidents.data?.pages.flatMap((p) => p.content) ?? [];
  const total = incidents.data?.pages[0]?.totalElements;
  const presetKeys = (Object.keys(PRESETS) as Preset[]).filter((k) => role !== 'EMPLOYEE' || k !== 'unassigned');

  const title = role === 'TECHNICIAN' ? 'Queue' : role === 'EMPLOYEE' ? 'My incidents' : 'Incidents';

  return (
    <View style={{ flex: 1 }}>
      <Stack.Screen options={{ title }} />
      {/* Native search field in the header (pull down to reveal it on iOS). */}
      <Stack.SearchBar placeholder="Search number, title, description" onChangeText={(e) => setText(e.nativeEvent.text)} onCancelButtonPress={() => setText('')} />
      <FlatList
        contentInsetAdjustmentBehavior="automatic"
        data={items}
        keyExtractor={(i) => String(i.id)}
        renderItem={({ item, index }) => (
          <View style={grouped(index, items.length, c)}>
            {index > 0 && <View style={{ height: 1, backgroundColor: c.divider, marginLeft: 70 }} />}
            <IncidentRow incident={item} swipe={quickAction(item)} />
          </View>
        )}
        ListHeaderComponent={
          <View style={{ paddingVertical: 12, gap: 12 }}>
            {role !== 'EMPLOYEE' && (
              <View style={{ paddingHorizontal: 16 }}>
                <Segmented
                  value={scope}
                  onChange={setScope}
                  options={[
                    { value: 'MINE', label: role === 'TECHNICIAN' ? 'Assigned to me' : 'Mine' },
                    { value: 'ALL', label: 'Everyone' },
                  ]}
                />
              </View>
            )}
            <View style={{ paddingLeft: 16 }}>
              <Chips scroll options={presetKeys.map((k) => ({ value: k, label: PRESETS[k].label }))} value={preset} onChange={setPreset} />
            </View>
            {total !== undefined && (
              <T v="small" muted style={{ paddingHorizontal: 20 }}>
                {total} incident{total === 1 ? '' : 's'}
                {role === 'TECHNICIAN' ? ' · swipe right for the next step' : ''}
              </T>
            )}
            {incidents.error && (
              <View style={{ paddingHorizontal: 16 }}>
                <Banner tone="danger" title="Could not load incidents">
                  {errorMessage(incidents.error)}
                </Banner>
              </View>
            )}
          </View>
        }
        ListEmptyComponent={incidents.isPending ? <Loading /> : <Empty stamp={q ? 'No match' : 'No dockets'} text={q ? 'No incidents match your search' : 'Nothing here yet'} />}
        ListFooterComponent={incidents.isFetchingNextPage ? <Loading /> : <View style={{ height: role === 'EMPLOYEE' ? 96 : 32 }} />}
        onEndReached={() => incidents.hasNextPage && !incidents.isFetchingNextPage && void incidents.fetchNextPage()}
        onEndReachedThreshold={0.4}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl
            refreshing={incidents.isRefetching && !incidents.isFetchingNextPage}
            onRefresh={() => void incidents.refetch()}
            tintColor={c.primary}
            colors={[c.primary]}
          />
        }
      />
      {role === 'EMPLOYEE' && <Fab label="Report" icon="add" onPress={() => router.push('/report')} />}
    </View>
  );
}
