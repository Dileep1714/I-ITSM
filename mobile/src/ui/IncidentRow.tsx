import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useRef } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import ReanimatedSwipeable, { type SwipeableMethods } from 'react-native-gesture-handler/ReanimatedSwipeable';
import type { IncidentSummary } from '../api/types';
import { fromNow } from '../lib/format';
import { PriorityDot, Row, SlaBadge, StatusBadge, T, type IconName } from './kit';
import { MiniStrip } from './Docket';
import { useColors } from './theme';

export interface SwipeAction {
  label: string;
  icon: IconName;
  color: string;
  onAction: () => void;
}

/**
 * One incident in a list: priority avatar, title, number and service, status. Optionally swipe right to run a
 * quick action (e.g. Take / Acknowledge), as in a mail app.
 */
export function IncidentRow({ incident: i, swipe }: { incident: IncidentSummary; swipe?: SwipeAction }) {
  const c = useColors();
  const ref = useRef<SwipeableMethods>(null);

  const row = (
    <Pressable
      onPress={() => router.push(`/incident/${i.id}`)}
      android_ripple={{ color: c.divider }}
      style={({ pressed }) => [styles.row, { backgroundColor: Platform.OS === 'ios' && pressed ? c.surfaceAlt : c.surface }]}
      accessibilityRole="button"
      accessibilityLabel={`${i.priority ?? 'No priority'}, ${i.title}, ${i.status}`}
      accessibilityHint={swipe ? `Swipe right to ${swipe.label.toLowerCase()}` : undefined}
    >
      <PriorityDot priority={i.priority} />
      <View style={{ flex: 1, gap: 3 }}>
        <Row style={{ justifyContent: 'space-between' }} gap={8}>
          <T style={{ fontWeight: '600', flex: 1 }} lines={1}>
            {i.title}
          </T>
          <T v="small" muted>
            {fromNow(i.createdAt).replace(' ago', '')}
          </T>
        </Row>
        <T v="small" muted lines={1}>
          {i.number} · {i.serviceName}
          {i.assigneeName ? ` · ${i.assigneeName}` : ''}
        </T>
        <Row gap={6} style={{ marginTop: 2 }}>
          <StatusBadge status={i.status} />
          {(i.slaStatus === 'AT_RISK' || i.slaStatus === 'BREACHED') && <SlaBadge status={i.slaStatus} />}
        </Row>
        <MiniStrip status={i.status} />
      </View>
    </Pressable>
  );

  if (!swipe) return row;
  return (
    <ReanimatedSwipeable
      ref={ref}
      friction={1.6}
      leftThreshold={70}
      overshootLeft={false}
      onSwipeableOpen={() => {
        swipe.onAction();
        ref.current?.close();
      }}
      renderLeftActions={() => (
        <View style={[styles.action, { backgroundColor: swipe.color }]}>
          <Ionicons name={swipe.icon} size={22} color="#fff" />
          <Text style={{ color: '#fff', fontWeight: '600' }}>{swipe.label}</Text>
        </View>
      )}
    >
      {row}
    </ReanimatedSwipeable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 14, paddingHorizontal: 16, paddingVertical: 12, alignItems: 'flex-start' },
  action: { width: 140, flexDirection: 'row', alignItems: 'center', gap: 8, paddingLeft: 20 },
});
