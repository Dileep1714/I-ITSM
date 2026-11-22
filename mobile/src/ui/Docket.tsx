import dayjs from 'dayjs';
import { Children, type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { IncidentStatus } from '../api/types';
import { humanize } from '../lib/format';
import { FIGURE, useColors } from './theme';

/*
 * Docket devices from the web app, in native form: the queue ledger (counts as typed figures on one ruled sheet),
 * the miniature lifecycle strip for list rows, and stamped empty states.
 */

// ----- queue ledger -----------------------------------------------------------------------------------------------

/** One ruled sheet holding the counts, with a typed date line; two cells per row on a phone. */
export function Ledger({ title, children }: { title?: string; children: ReactNode }) {
  const c = useColors();
  const cells = Children.toArray(children).filter(Boolean);
  return (
    <View style={[styles.ledger, { backgroundColor: c.surface, borderColor: c.border }]}>
      <View style={[styles.ledgerHead, { borderBottomColor: c.border }]}>
        <Text style={[styles.typed, { color: c.primary }]} numberOfLines={1}>
          {(title ?? 'Queue').toUpperCase()}
        </Text>
        <Text style={[styles.typed, { color: c.primary }]}>{dayjs().format('ddd D MMM · HH:mm').toUpperCase()}</Text>
      </View>
      <View style={styles.cells}>
        {cells.map((cell, i) => (
          <View
            key={i}
            style={[
              styles.cellWrap,
              { borderColor: c.divider },
              i % 2 === 0 && { borderRightWidth: StyleSheet.hairlineWidth },
              i < cells.length - (cells.length % 2 === 0 ? 2 : 1) && { borderBottomWidth: StyleSheet.hairlineWidth },
            ]}
          >
            {cell}
          </View>
        ))}
      </View>
    </View>
  );
}

/**
 * One count in the ledger. Zeros recede; non-zero alarms turn stamp red with a corner stamp. Tapping opens the
 * matching list.
 */
export function LedgerCell({
  label,
  value,
  alarm,
  onPress,
}: {
  label: string;
  value: string | number | undefined;
  alarm?: 'risk' | 'breach';
  onPress?: () => void;
}) {
  const c = useColors();
  const numeric = typeof value === 'number';
  const zero = numeric && value === 0;
  const alarmed = !!alarm && numeric && value > 0;
  const shown = value === undefined ? '··' : numeric ? String(value).padStart(2, '0') : value;
  const ink = alarmed ? c.danger : zero ? c.muted : c.primary;
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      android_ripple={{ color: c.divider }}
      style={styles.cell}
      accessibilityRole={onPress ? 'button' : undefined}
      accessibilityLabel={`${label}: ${value ?? 'loading'}`}
    >
      <Text style={[styles.cellLabel, { color: c.primary }]} numberOfLines={1}>
        {label.toUpperCase()}
      </Text>
      {alarmed && (
        <Text style={[styles.corner, { color: c.danger, borderColor: c.danger, borderWidth: alarm === 'breach' ? 2.5 : 1.5 }]}>
          {alarm === 'breach' ? 'LATE' : 'ACT NOW'}
        </Text>
      )}
      <Text style={[styles.figure, { color: ink, opacity: zero ? 0.55 : 1 }]} adjustsFontSizeToFit numberOfLines={1}>
        {shown}
      </Text>
    </Pressable>
  );
}

// ----- miniature lifecycle strip -----------------------------------------------------------------------------------

const STEPS: IncidentStatus[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'CLOSED'];

/** The stamp strip in miniature: one tick per lifecycle step, the current one larger (dashed red while on hold). */
export function MiniStrip({ status }: { status: IncidentStatus }) {
  const c = useColors();
  const hold = status === 'ON_HOLD';
  const ended = status === 'CANCELLED';
  const current = hold ? STEPS.indexOf('IN_PROGRESS') : ended ? -1 : STEPS.indexOf(status);
  return (
    <View style={[styles.mini, ended && { opacity: 0.45 }]} accessible accessibilityLabel={`Lifecycle: ${humanize(status)}`}>
      {STEPS.map((step, i) => {
        const done = i < current || (status === 'CLOSED' && i === current);
        const now = i === current && !done;
        return (
          <View
            key={step}
            style={[
              styles.tick,
              { borderColor: c.border },
              done && { backgroundColor: c.primary, borderColor: c.primary },
              now && !hold && { width: 10, height: 10, backgroundColor: c.primary, borderColor: c.primary },
              now && hold && { width: 10, height: 10, borderColor: c.danger, borderStyle: 'dashed', borderWidth: 1.5 },
            ]}
          />
        );
      })}
    </View>
  );
}

// ----- stamped empty state -----------------------------------------------------------------------------------------

/** An empty list as a blank ruled area with a rubber stamp, e.g. ALL CLEAR or NO DOCKETS. */
export function StampedEmpty({ stamp, text, action }: { stamp: string; text?: string; action?: ReactNode }) {
  const c = useColors();
  return (
    <View style={[styles.empty, { borderColor: c.border }]}>
      <View style={[styles.stampOuter, { borderColor: c.primary }]}>
        <View style={[styles.stampInner, { borderColor: c.primary }]}>
          <Text style={[styles.stampText, { color: c.primary }]}>{stamp.toUpperCase()}</Text>
        </View>
      </View>
      {text ? <Text style={{ color: c.muted, fontSize: 14, textAlign: 'center' }}>{text}</Text> : null}
      {action}
    </View>
  );
}

const styles = StyleSheet.create({
  ledger: { borderWidth: StyleSheet.hairlineWidth, borderRadius: 14, overflow: 'hidden' },
  ledgerHead: { flexDirection: 'row', justifyContent: 'space-between', gap: 8, paddingHorizontal: 14, paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth },
  typed: { fontFamily: FIGURE, fontSize: 12, letterSpacing: 0.4 },
  cells: { flexDirection: 'row', flexWrap: 'wrap' },
  cellWrap: { width: '50%' },
  cell: { minHeight: 104, paddingHorizontal: 14, paddingTop: 12, paddingBottom: 10, justifyContent: 'space-between' },
  cellLabel: { fontSize: 11, fontWeight: '800', letterSpacing: 1.1, paddingRight: 56 },
  corner: { position: 'absolute', top: 10, right: 10, fontSize: 10, fontWeight: '800', letterSpacing: 1, paddingHorizontal: 5, paddingVertical: 1, transform: [{ rotate: '-4deg' }] },
  figure: { fontFamily: FIGURE, fontSize: 44, lineHeight: 48 },
  mini: { flexDirection: 'row', alignItems: 'center', gap: 3, marginTop: 4 },
  tick: { width: 7, height: 7, borderWidth: 1 },
  empty: { alignItems: 'center', gap: 10, paddingVertical: 26, paddingHorizontal: 16, borderWidth: 1, borderStyle: 'dashed', borderRadius: 12 },
  stampOuter: { borderWidth: 1.5, padding: 2, transform: [{ rotate: '-3deg' }] },
  stampInner: { borderWidth: 1.5, paddingHorizontal: 12, paddingVertical: 5 },
  stampText: { fontSize: 16, fontWeight: '800', letterSpacing: 2.2 },
});
