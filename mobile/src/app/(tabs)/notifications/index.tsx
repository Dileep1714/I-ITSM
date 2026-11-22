import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, Stack } from 'expo-router';
import { useRef } from 'react';
import { FlatList, Platform, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import ReanimatedSwipeable, { type SwipeableMethods } from 'react-native-gesture-handler/ReanimatedSwipeable';
import { api } from '@/api/client';
import type { IncidentEventType, NotificationView } from '@/api/types';
import { fromNow } from '@/lib/format';
import { Empty, Loading, Row, T, type IconName } from '@/ui/kit';
import { hue, useColors } from '@/ui/theme';

const ICON: Partial<Record<IncidentEventType, [IconName, string]>> = {
  SLA_BREACHED: ['warning', hue.red],
  SLA_WARNING: ['time', hue.orange],
  ESCALATED: ['trending-up', hue.red],
  RESOLVED: ['checkmark-circle', hue.teal],
  ASSIGNED: ['person-add', hue.indigo],
  REASSIGNED: ['swap-horizontal', hue.indigo],
  INFO_REQUESTED: ['help-circle', hue.yellow],
  CLOSED: ['lock-closed', hue.gray],
  PRIORITY_CHANGED: ['flag', hue.orange],
};

function NotificationItem({ n, onOpen, onRead }: { n: NotificationView; onOpen: () => void; onRead: () => void }) {
  const c = useColors();
  const ref = useRef<SwipeableMethods>(null);
  const [icon, color] = ICON[n.type] ?? (['notifications', hue.blue] as [IconName, string]);
  const row = (
    <Pressable
      onPress={onOpen}
      android_ripple={{ color: c.divider }}
      style={({ pressed }) => [styles.row, { backgroundColor: Platform.OS === 'ios' && pressed ? c.surfaceAlt : n.read ? c.bg : c.surface }]}
    >
      <View style={[styles.icon, { backgroundColor: color + '22' }]}>
        <Ionicons name={icon} size={20} color={color} />
      </View>
      <View style={{ flex: 1, gap: 2 }}>
        <Row style={{ justifyContent: 'space-between' }}>
          <T v="small" style={{ fontWeight: n.read ? '400' : '700', flex: 1 }} lines={1}>
            {n.title}
          </T>
          <T v="small" muted>
            {fromNow(n.createdAt).replace(' ago', '')}
          </T>
        </Row>
        <T v="small" muted lines={2}>
          {n.message}
        </T>
      </View>
      {!n.read && <View style={[styles.dot, { backgroundColor: c.primary }]} />}
    </Pressable>
  );
  if (n.read) return row;
  return (
    <ReanimatedSwipeable
      ref={ref}
      friction={1.6}
      rightThreshold={70}
      overshootRight={false}
      onSwipeableOpen={() => {
        onRead();
        ref.current?.close();
      }}
      renderRightActions={() => (
        <View style={[styles.action, { backgroundColor: c.primary }]}>
          <Ionicons name="checkmark-done" size={22} color="#fff" />
          <Text style={{ color: '#fff', fontWeight: '600' }}>Read</Text>
        </View>
      )}
    >
      {row}
    </ReanimatedSwipeable>
  );
}

export default function NotificationsScreen() {
  const c = useColors();
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ['notifications', 'list'], queryFn: () => api.notifications.list(0, 50) });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });
  const markRead = useMutation({ mutationFn: api.notifications.markRead, onSuccess: refresh });
  const markAll = useMutation({ mutationFn: api.notifications.markAllRead, onSuccess: refresh });
  const hasUnread = list.data?.content.some((n) => !n.read);

  return (
    <>
      <Stack.Screen
        options={{
          title: 'Alerts',
          headerRight: () =>
            hasUnread ? (
              <Pressable onPress={() => markAll.mutate()} hitSlop={10}>
                <T v="small" color={c.primary} style={{ fontWeight: '600' }}>
                  Read all
                </T>
              </Pressable>
            ) : null,
        }}
      />
      <FlatList
        contentInsetAdjustmentBehavior="automatic"
        data={list.data?.content ?? []}
        keyExtractor={(n) => String(n.id)}
        ItemSeparatorComponent={() => <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: c.divider, marginLeft: 72 }} />}
        ListHeaderComponent={
          hasUnread ? (
            <T v="small" muted style={{ padding: 16, paddingBottom: 8 }}>
              Swipe left to mark as read
            </T>
          ) : undefined
        }
        ListEmptyComponent={list.isPending ? <Loading /> : <Empty stamp="All caught up" text="No new notifications." />}
        refreshControl={<RefreshControl refreshing={list.isRefetching} onRefresh={refresh} tintColor={c.primary} colors={[c.primary]} />}
        renderItem={({ item: n }) => (
          <NotificationItem
            n={n}
            onRead={() => markRead.mutate(n.id)}
            onOpen={() => {
              if (!n.read) markRead.mutate(n.id);
              if (n.incidentId) router.push(`/incident/${n.incidentId}`);
            }}
          />
        )}
      />
    </>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 14, paddingHorizontal: 16, paddingVertical: 14, alignItems: 'center' },
  icon: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  dot: { width: 10, height: 10, borderRadius: 5 },
  action: { width: 120, flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: 8, paddingRight: 20 },
});
