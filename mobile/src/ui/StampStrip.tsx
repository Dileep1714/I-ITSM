import dayjs from 'dayjs';
import { useEffect, useRef } from 'react';
import { ScrollView, type ScrollViewInstance, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, useReducedMotion, useSharedValue, withSpring } from 'react-native-reanimated';
import type { AuditEntryView, IncidentStatus } from '../api/types';
import { humanize } from '../lib/format';
import { FIGURE, useColors } from './theme';

const STEPS: IncidentStatus[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'CLOSED'];
const WIDTH = 108;
const GAP = 10;

function stampTime(iso: string) {
  const d = dayjs(iso);
  return d.isSame(dayjs(), 'day') ? d.format('HH:mm') : d.format('D MMM HH:mm');
}

type State = 'done' | 'now' | 'todo' | 'hold' | 'void';

/**
 * The incident's lifecycle as a row of rubber stamps, the same signature as the web app: passed steps are carbon
 * impressions with their time, the current step is stamped in red (and lands with a short press), steps to come
 * are dashed outlines. On a phone it is one horizontal strip that opens scrolled to the current step.
 */
export function StampStrip({ status, createdAt, history }: { status: IncidentStatus; createdAt: string; history: AuditEntryView[] }) {
  const c = useColors();
  const scroll = useRef<ScrollViewInstance>(null);

  const ordered = [...history].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));
  const reachedAt = new Map<IncidentStatus, string>([['NEW', createdAt]]);
  let lastMain: IncidentStatus = 'NEW';
  for (const h of ordered) {
    if (!h.newStatus) continue;
    if (!reachedAt.has(h.newStatus) || !STEPS.includes(h.newStatus)) reachedAt.set(h.newStatus, h.occurredAt);
    if (STEPS.includes(h.newStatus)) lastMain = h.newStatus;
  }
  const current = STEPS.includes(status) ? status : lastMain;
  const currentIndex = STEPS.indexOf(current);
  const interrupted = status === 'ON_HOLD' || status === 'CANCELLED';

  const stamps: { key: string; label: string; state: State; at?: string }[] = [];
  STEPS.forEach((step, index) => {
    const done = index < currentIndex || (index === currentIndex && (interrupted || step === 'CLOSED'));
    stamps.push({ key: step, label: humanize(step), state: done ? 'done' : index === currentIndex ? 'now' : 'todo', at: reachedAt.get(step) });
    if (interrupted && index === currentIndex) {
      stamps.push({ key: status, label: humanize(status), state: status === 'ON_HOLD' ? 'hold' : 'void', at: reachedAt.get(status) });
    }
  });
  const activeIndex = stamps.findIndex((s) => s.state === 'now' || s.state === 'hold' || s.state === 'void');

  useEffect(() => {
    if (activeIndex < 0) return;
    const t = setTimeout(() => scroll.current?.scrollTo({ x: Math.max(0, activeIndex * (WIDTH + GAP) - WIDTH), animated: false }), 0);
    return () => clearTimeout(t);
  }, [activeIndex]);

  return (
    <ScrollView
      ref={scroll}
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={{ gap: GAP, paddingVertical: 6, paddingHorizontal: 2 }}
      accessibilityLabel={`Lifecycle: ${humanize(status)}`}
    >
      {stamps.map((s, index) => (
        <Stamp key={s.key} label={s.label} state={s.state} at={s.at} tilt={index % 2 ? 1.2 : -1.5} colors={c} />
      ))}
    </ScrollView>
  );
}

function Stamp({ label, state, at, tilt, colors: c }: { label: string; state: State; at?: string; tilt: number; colors: ReturnType<typeof useColors> }) {
  const reduce = useReducedMotion();
  const alarm = state === 'now' || state === 'hold' || state === 'void';
  const scale = useSharedValue(alarm && !reduce ? 1.1 : 1);
  useEffect(() => {
    if (alarm && !reduce) scale.value = withSpring(1, { damping: 14, stiffness: 320 });
  }, [alarm, reduce, scale]);
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${state === 'todo' ? 0 : alarm ? -2.5 : tilt}deg` }, { scale: scale.value }] }));

  const ink = alarm ? c.danger : state === 'todo' ? c.muted : c.primary;
  const border =
    state === 'todo'
      ? { borderWidth: 1, borderStyle: 'dashed' as const, borderColor: c.border }
      : state === 'hold'
        ? { borderWidth: 2.5, borderStyle: 'dashed' as const, borderColor: ink }
        : state === 'void'
          ? { borderWidth: 3, borderColor: ink }
          : { borderWidth: alarm ? 2.5 : 1.5, borderColor: ink };

  return (
    <Animated.View
      style={[styles.stamp, border, pressStyle]}
      accessible
      accessibilityLabel={`${label}${at && state !== 'todo' ? `, ${stampTime(at)}` : ''}${state === 'now' ? ', current step' : ''}`}
    >
      <Text style={[styles.step, { color: ink }]} numberOfLines={1} adjustsFontSizeToFit>
        {label.toUpperCase()}
      </Text>
      {state !== 'todo' && at ? <Text style={[styles.time, { color: ink }]}>{stampTime(at)}</Text> : <View style={{ height: 16 }} />}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  stamp: { width: WIDTH, height: 58, borderRadius: 1, alignItems: 'center', justifyContent: 'center', gap: 3, paddingHorizontal: 4 },
  step: { fontSize: 10.5, fontWeight: '800', letterSpacing: 0.9 },
  time: { fontFamily: FIGURE, fontSize: 12.5 },
});
