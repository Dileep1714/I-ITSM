/**
 * Component kit following Material 3 / platform conventions: grouped list sections, tonal surfaces, pill buttons,
 * filter chips, segmented buttons, an extended floating action button and bottom sheets.
 */
import Ionicons from '@expo/vector-icons/Ionicons';
import { Children, type ComponentProps, type ReactNode } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextInputProps,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { IncidentStatus, PriorityLevel, SlaStatus } from '../api/types';
import { humanize } from '../lib/format';
import { FIGURE, useColors } from './theme';

export type IconName = ComponentProps<typeof Ionicons>['name'];

// ----- text -----

type Variant = 'display' | 'title' | 'heading' | 'body' | 'small' | 'label' | 'mono';
const VARIANT: Record<Variant, TextStyle> = {
  display: { fontSize: 32, fontWeight: '700', letterSpacing: -0.5 },
  title: { fontSize: 22, fontWeight: '600' },
  heading: { fontSize: 16, fontWeight: '600' },
  body: { fontSize: 16, lineHeight: 22 },
  small: { fontSize: 14, lineHeight: 19 },
  label: { fontSize: 12, fontWeight: '700', letterSpacing: 1.2, textTransform: 'uppercase' },
  mono: { fontSize: 14, fontFamily: FIGURE },
};

export function T({
  v = 'body',
  muted,
  color,
  style,
  children,
  lines,
}: {
  v?: Variant;
  muted?: boolean;
  color?: string;
  style?: StyleProp<TextStyle>;
  children: ReactNode;
  lines?: number;
}) {
  const c = useColors();
  return (
    <Text numberOfLines={lines} style={[VARIANT[v], { color: color ?? (muted ? c.muted : c.text) }, style]}>
      {children}
    </Text>
  );
}

// ----- layout -----

export function Row({ children, gap = 8, style, wrap }: { children: ReactNode; gap?: number; style?: StyleProp<ViewStyle>; wrap?: boolean }) {
  return <View style={[{ flexDirection: 'row', alignItems: 'center', gap, flexWrap: wrap ? 'wrap' : 'nowrap' }, style]}>{children}</View>;
}

/** A rounded tonal surface (Material "filled card"). */
export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const c = useColors();
  return <View style={[styles.card, { backgroundColor: c.surface }, style]}>{children}</View>;
}

/** Grouped list section, as in Android/iOS Settings: a small header above one rounded container of rows. */
export function Section({ title, action, children, style }: { title?: string; action?: ReactNode; children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const c = useColors();
  const rows = Children.toArray(children).filter(Boolean);
  return (
    <View style={[{ gap: 8 }, style]}>
      {(title || action) && (
        <Row style={{ justifyContent: 'space-between', paddingHorizontal: 4 }}>
          {title ? (
            <T v="label" color={c.primary}>
              {title}
            </T>
          ) : (
            <View />
          )}
          {action}
        </Row>
      )}
      <View style={[styles.section, { backgroundColor: c.surface }]}>
        {rows.map((row, index) => (
          <View key={index}>
            {index > 0 && <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: c.divider, marginLeft: 16 }} />}
            {row}
          </View>
        ))}
      </View>
    </View>
  );
}

/** One row of a Section: leading icon, title, supporting text, trailing content, optional chevron. */
export function ListRow({
  icon,
  iconColor,
  leading,
  title,
  subtitle,
  trailing,
  onPress,
  chevron,
  destructive,
}: {
  icon?: IconName;
  iconColor?: string;
  leading?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  trailing?: ReactNode;
  onPress?: () => void;
  chevron?: boolean;
  destructive?: boolean;
}) {
  const c = useColors();
  const tint = destructive ? c.danger : (iconColor ?? c.primary);
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      android_ripple={onPress ? { color: c.divider } : undefined}
      style={({ pressed }) => [styles.listRow, Platform.OS === 'ios' && pressed && { backgroundColor: c.surfaceAlt }]}
      accessibilityRole={onPress ? 'button' : undefined}
    >
      {leading ??
        (icon && (
          <View style={[styles.rowIcon, { backgroundColor: tint + '1f' }]}>
            <Ionicons name={icon} size={20} color={tint} />
          </View>
        ))}
      <View style={{ flex: 1, gap: 2 }}>
        {typeof title === 'string' ? (
          <T color={destructive ? c.danger : undefined} lines={2}>
            {title}
          </T>
        ) : (
          title
        )}
        {typeof subtitle === 'string' ? (
          <T v="small" muted lines={2}>
            {subtitle}
          </T>
        ) : (
          subtitle
        )}
      </View>
      {trailing}
      {chevron && <Ionicons name="chevron-forward" size={18} color={c.muted} />}
    </Pressable>
  );
}

/** Label above a value inside a card grid. */
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <View style={{ width: '50%', paddingVertical: 6, paddingRight: 8, gap: 2 }}>
      <T v="label" muted>
        {label}
      </T>
      {typeof children === 'string' || typeof children === 'number' ? <T v="small">{children}</T> : children}
    </View>
  );
}

export function Banner({ tone = 'warning', title, icon, children }: { tone?: 'warning' | 'neutral' | 'danger' | 'info'; title: string; icon?: IconName; children?: ReactNode }) {
  const c = useColors();
  const bg = tone === 'warning' ? c.warningBg : tone === 'danger' ? c.danger + '1f' : tone === 'info' ? c.primaryContainer : c.surfaceAlt;
  const fg = tone === 'info' ? c.onPrimaryContainer : c.text;
  const glyph: IconName = icon ?? (tone === 'danger' ? 'alert-circle' : tone === 'warning' ? 'help-circle' : 'information-circle');
  return (
    <View style={[styles.banner, { backgroundColor: bg }]}>
      <Ionicons name={glyph} size={20} color={tone === 'danger' ? c.danger : fg} />
      <View style={{ flex: 1, gap: 2 }}>
        <T v="small" color={fg} style={{ fontWeight: '600' }}>
          {title}
        </T>
        {typeof children === 'string' ? (
          <T v="small" color={fg}>
            {children}
          </T>
        ) : (
          children
        )}
      </View>
    </View>
  );
}

export function Loading() {
  const c = useColors();
  return (
    <View style={{ padding: 32, alignItems: 'center' }}>
      <ActivityIndicator color={c.primary} />
    </View>
  );
}

export function Empty({ icon = 'file-tray-outline', text }: { icon?: IconName; text: string }) {
  const c = useColors();
  return (
    <View style={{ alignItems: 'center', padding: 32, gap: 10 }}>
      <View style={[styles.emptyIcon, { backgroundColor: c.surfaceAlt }]}>
        <Ionicons name={icon} size={28} color={c.muted} />
      </View>
      <T muted v="small" style={{ textAlign: 'center' }}>
        {text}
      </T>
    </View>
  );
}

/** Initials box: a square filled with the signed-in role's accent (or a given colour). */
export function Avatar({ name, color, size = 40 }: { name: string; color?: string; size?: number }) {
  const c = useColors();
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p.charAt(0).toUpperCase())
    .join('');
  const fill = color ?? c.accent;
  return (
    <View style={{ width: size, height: size, borderRadius: 4, backgroundColor: fill, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ color: color ? '#fff' : c.onAccent, fontWeight: '700', fontSize: size * 0.36, letterSpacing: 0.5 }}>{initials}</Text>
    </View>
  );
}

/** Product mark: a double-ruled square holding "IT" in typed figures, beside the wordmark. */
export function BrandMark({ size = 40 }: { size?: number }) {
  const c = useColors();
  return (
    <Row gap={10}>
      <View style={{ width: size, height: size, borderWidth: 1.5, borderColor: c.primary, padding: 2.5 }}>
        <View style={{ flex: 1, borderWidth: 0.75, borderColor: c.primary, alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ fontFamily: FIGURE, fontSize: size * 0.34, color: c.primary }}>IT</Text>
        </View>
      </View>
      <View>
        <Text style={{ fontSize: 19, fontWeight: '800', color: c.primary, letterSpacing: -0.2 }}>I-ITSM</Text>
        <Text style={{ fontSize: 10, fontWeight: '700', color: c.muted, letterSpacing: 1.4 }}>SERVICE MANAGEMENT</Text>
      </View>
    </Row>
  );
}

// ----- badges -----

export function Badge({ label, color, variant = 'light' }: { label: string; color: string; variant?: 'light' | 'filled' | 'dot' | 'outline' }) {
  const c = useColors();
  if (variant === 'dot') {
    return (
      <View style={[styles.badge, { backgroundColor: c.surfaceAlt }]}>
        <View style={{ width: 7, height: 7, borderRadius: 4, backgroundColor: color }} />
        <Text style={[styles.badgeText, { color: c.text }]}>{label}</Text>
      </View>
    );
  }
  const filled = variant === 'filled';
  return (
    <View
      style={[
        styles.badge,
        { backgroundColor: filled ? color : variant === 'outline' ? 'transparent' : color + '24' },
        variant === 'outline' && { borderWidth: 1, borderColor: color },
      ]}
    >
      <Text style={[styles.badgeText, { color: filled ? '#fff' : color }]}>{label}</Text>
    </View>
  );
}

/*
 * Docket marks, the same grammar as the web app. Priority is a typed stamp on a fixed magnitude ramp (P1 largest,
 * double-ruled in stamp red; P2 heavy; P3 single; P4 dashed), so it never depends on colour alone. Status is
 * tracked caps in a ruled box (active work filled, on hold dashed, cancelled struck through). SLA is a line
 * glyph plus words: solid on track, dashed paused, red at risk, double red and struck through when breached.
 */
export function PriorityStamp({ priority, size = 'sm' }: { priority: PriorityLevel | null | undefined; size?: 'sm' | 'lg' }) {
  const c = useColors();
  const big = size === 'lg';
  if (!priority) {
    return (
      <View style={[styles.stamp, { borderStyle: 'dotted', borderColor: c.muted, borderWidth: 1 }, big && styles.stampLg]}>
        <Text style={{ color: c.muted, fontSize: big ? 11 : 10, fontWeight: '700', letterSpacing: 0.8 }}>{big ? '–' : 'UNSET'}</Text>
      </View>
    );
  }
  const p1 = priority === 'P1';
  const ink = p1 ? c.danger : priority === 'P4' ? c.muted : c.text;
  const inner = (
    <Text style={{ color: ink, fontFamily: FIGURE, fontSize: big ? (p1 ? 17 : 15) : p1 ? 14 : 13 }} accessibilityLabel={`Priority ${priority}`}>
      {priority}
    </Text>
  );
  if (p1) {
    // A double rule: an outer and an inner border in stamp red.
    return (
      <View style={[styles.stamp, { borderWidth: 1.5, borderColor: ink, padding: 1.5 }, big && styles.stampLg]}>
        <View style={{ borderWidth: 1.5, borderColor: ink, paddingHorizontal: big ? 4 : 3, alignItems: 'center', justifyContent: 'center', flex: big ? 1 : undefined, alignSelf: big ? 'stretch' : undefined }}>
          {inner}
        </View>
      </View>
    );
  }
  return (
    <View
      style={[
        styles.stamp,
        { paddingHorizontal: 6, paddingVertical: 1, borderColor: ink, borderWidth: priority === 'P2' ? 2 : 1, borderStyle: priority === 'P4' ? 'dashed' : 'solid' },
        big && styles.stampLg,
      ]}
    >
      {inner}
    </View>
  );
}

export const PriorityBadge = ({ priority }: { priority: PriorityLevel | null | undefined }) => <PriorityStamp priority={priority} />;

/** Priority as the leading stamp of a list row. */
export const PriorityDot = ({ priority }: { priority: PriorityLevel | null | undefined }) => <PriorityStamp priority={priority} size="lg" />;

export function StatusBadge({ status }: { status: IncidentStatus }) {
  const c = useColors();
  const active = status === 'IN_PROGRESS' || status === 'ACKNOWLEDGED';
  const quiet = status === 'CLOSED' || status === 'VERIFIED' || status === 'CANCELLED';
  const ink = quiet ? c.muted : c.primary;
  return (
    <View
      style={[
        styles.status,
        { borderColor: ink, borderStyle: status === 'ON_HOLD' ? 'dashed' : 'solid', backgroundColor: active ? ink : 'transparent' },
      ]}
    >
      <Text
        style={[
          styles.statusText,
          { color: active ? c.surface : ink, textDecorationLine: status === 'CANCELLED' ? 'line-through' : 'none' },
        ]}
      >
        {humanize(status).toUpperCase()}
      </Text>
    </View>
  );
}

export function SlaBadge({ status }: { status: SlaStatus | null | undefined }) {
  const c = useColors();
  if (!status) return null;
  const alarm = status === 'AT_RISK' || status === 'BREACHED';
  const ink = alarm ? c.danger : status === 'MET' || status === 'NOT_STARTED' ? c.muted : c.primary;
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, alignSelf: 'flex-start' }}>
      <View style={{ width: 16, gap: 2 }}>
        <View
          style={{
            height: 0,
            borderTopWidth: 2,
            borderColor: ink,
            borderStyle: status === 'PAUSED' ? 'dashed' : status === 'NOT_STARTED' ? 'dotted' : 'solid',
          }}
        />
        {status === 'BREACHED' && <View style={{ height: 0, borderTopWidth: 2, borderColor: ink }} />}
      </View>
      <Text
        style={{
          color: ink,
          fontSize: 12,
          fontWeight: alarm ? '700' : '500',
          textDecorationLine: status === 'BREACHED' ? 'line-through' : 'none',
        }}
      >
        SLA {humanize(status).toLowerCase()}
      </Text>
    </View>
  );
}

// ----- buttons -----

export function Button({
  label,
  onPress,
  icon,
  variant = 'filled',
  color,
  loading,
  disabled,
  style,
}: {
  label: string;
  onPress: () => void;
  icon?: IconName;
  variant?: 'filled' | 'tonal' | 'outlined' | 'text';
  color?: string;
  loading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const c = useColors();
  const tint = color ?? c.primary;
  const bg =
    variant === 'filled' ? tint : variant === 'tonal' ? (color ? color + '24' : c.primaryContainer) : 'transparent';
  const fg = variant === 'filled' ? (color ? '#fff' : c.onPrimary) : variant === 'tonal' && !color ? c.onPrimaryContainer : tint;
  const off = disabled || loading;
  return (
    <Pressable
      onPress={onPress}
      disabled={off}
      accessibilityRole="button"
      accessibilityState={{ disabled: !!off, busy: !!loading }}
      android_ripple={{ color: fg + '33', borderless: false }}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, opacity: disabled ? 0.4 : Platform.OS === 'ios' && pressed ? 0.75 : 1 },
        variant === 'outlined' && { borderWidth: 1, borderColor: c.border },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator size="small" color={fg} />
      ) : (
        <>
          {icon && <Ionicons name={icon} size={18} color={fg} />}
          <Text style={{ color: fg, fontWeight: '600', fontSize: 15 }}>{label}</Text>
        </>
      )}
    </Pressable>
  );
}

/** Extended floating action button, bottom-right: the main action of a screen (Material pattern). */
export function Fab({ label, icon, onPress }: { label: string; icon: IconName; onPress: () => void }) {
  const c = useColors();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      android_ripple={{ color: c.onPrimaryContainer + '22' }}
      style={({ pressed }) => [styles.fab, { backgroundColor: c.primaryContainer, opacity: Platform.OS === 'ios' && pressed ? 0.85 : 1 }]}
    >
      <Ionicons name={icon} size={22} color={c.onPrimaryContainer} />
      <Text style={{ color: c.onPrimaryContainer, fontWeight: '600', fontSize: 15 }}>{label}</Text>
    </Pressable>
  );
}

// ----- inputs -----

export function Input({ label, error, hint, style, ...props }: TextInputProps & { label?: string; error?: string | null; hint?: ReactNode }) {
  const c = useColors();
  return (
    <View style={{ gap: 6 }}>
      {label && (
        <T v="small" style={{ fontWeight: '600' }}>
          {label}
        </T>
      )}
      <TextInput
        placeholderTextColor={c.muted}
        selectionColor={c.primary}
        {...props}
        style={[
          styles.input,
          { backgroundColor: c.surfaceAlt, borderColor: error ? c.danger : 'transparent', color: c.text },
          props.multiline && { minHeight: 104, textAlignVertical: 'top', paddingTop: 12 },
          style,
        ]}
      />
      {error ? (
        <T v="small" color={c.danger}>
          {error}
        </T>
      ) : typeof hint === 'string' ? (
        <T v="small" muted>
          {hint}
        </T>
      ) : (
        hint
      )}
    </View>
  );
}

/** Material filter chips: pick one option (a check mark shows the selection). */
export function Chips<V extends string | number>({
  label,
  options,
  value,
  onChange,
  hint,
  scroll,
}: {
  label?: string;
  options: { value: V; label: string }[];
  value: V | null;
  onChange: (value: V) => void;
  hint?: ReactNode;
  /** One horizontally scrolling line instead of wrapping. */
  scroll?: boolean;
}) {
  const c = useColors();
  const chips = options.map((o) => {
    const on = value === o.value;
    return (
      <Pressable
        key={String(o.value)}
        onPress={() => onChange(o.value)}
        accessibilityRole="button"
        accessibilityState={{ selected: on }}
        style={[styles.chip, on ? { backgroundColor: c.primaryContainer, borderColor: c.primaryContainer } : { borderColor: c.border }]}
      >
        {on && <Ionicons name="checkmark" size={16} color={c.onPrimaryContainer} />}
        <Text style={{ color: on ? c.onPrimaryContainer : c.text, fontSize: 14, fontWeight: '500' }}>{o.label}</Text>
      </Pressable>
    );
  });
  return (
    <View style={{ gap: 8 }}>
      {label && (
        <T v="small" style={{ fontWeight: '600' }}>
          {label}
        </T>
      )}
      {scroll ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingRight: 16 }}>
          {chips}
        </ScrollView>
      ) : (
        <Row wrap gap={8}>
          {chips}
        </Row>
      )}
      {typeof hint === 'string' ? (
        <T v="small" muted>
          {hint}
        </T>
      ) : (
        hint
      )}
    </View>
  );
}

/** Material segmented buttons. */
export function Segmented<V extends string>({ options, value, onChange }: { options: { value: V; label: string }[]; value: V; onChange: (v: V) => void }) {
  const c = useColors();
  return (
    <View style={[styles.segmented, { borderColor: c.border }]}>
      {options.map((o, index) => {
        const on = o.value === value;
        return (
          <Pressable
            key={o.value}
            onPress={() => onChange(o.value)}
            accessibilityRole="tab"
            accessibilityState={{ selected: on }}
            style={[
              styles.segment,
              index > 0 && { borderLeftWidth: 1, borderLeftColor: c.border },
              on && { backgroundColor: c.primaryContainer },
            ]}
          >
            {on && <Ionicons name="checkmark" size={15} color={c.onPrimaryContainer} />}
            <Text numberOfLines={1} style={{ color: on ? c.onPrimaryContainer : c.text, fontWeight: '500', fontSize: 14 }}>
              {o.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function Stars({ value, onChange, size = 30 }: { value: number; onChange?: (v: number) => void; size?: number }) {
  return (
    <Row gap={4}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Pressable key={n} onPress={() => onChange?.(n)} disabled={!onChange} accessibilityLabel={`${n} star${n > 1 ? 's' : ''}`} hitSlop={4}>
          <Ionicons name={n <= value ? 'star' : 'star-outline'} size={size} color="#fab005" />
        </Pressable>
      ))}
    </Row>
  );
}

export function ProgressBar({ value, color }: { value: number; color: string }) {
  const c = useColors();
  return (
    <View style={{ height: 6, borderRadius: 3, backgroundColor: c.surfaceAlt, overflow: 'hidden' }}>
      <View style={{ width: `${Math.min(100, Math.max(0, value))}%`, height: '100%', borderRadius: 3, backgroundColor: color }} />
    </View>
  );
}

// ----- bottom sheet -----

/** Modal bottom sheet (actions such as Resolve or Request info). */
export function Sheet({ visible, title, onClose, children }: { visible: boolean; title?: string; onClose: () => void; children: ReactNode }) {
  const c = useColors();
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose} statusBarTranslucent navigationBarTranslucent>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={{ flex: 1 }}>
        <Pressable style={{ flex: 1, backgroundColor: c.scrim }} onPress={onClose} accessibilityLabel="Close" />
        <View style={[styles.sheet, { backgroundColor: c.surface, paddingBottom: insets.bottom + 16 }]}>
          <View style={[styles.grabber, { backgroundColor: c.border }]} />
          {title && (
            <T v="title" style={{ marginBottom: 12 }}>
              {title}
            </T>
          )}
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ gap: 16 }}>
            {children}
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: 16, padding: 16 },
  section: { borderRadius: 16, overflow: 'hidden' },
  listRow: { flexDirection: 'row', alignItems: 'center', gap: 14, paddingHorizontal: 16, paddingVertical: 12, minHeight: 56 },
  rowIcon: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  banner: { flexDirection: 'row', gap: 10, borderRadius: 14, padding: 14, alignItems: 'flex-start' },
  emptyIcon: { width: 56, height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  badge: { flexDirection: 'row', alignItems: 'center', gap: 5, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 4, alignSelf: 'flex-start' },
  stamp: { alignSelf: 'flex-start', alignItems: 'center', justifyContent: 'center', borderRadius: 1 },
  stampLg: { width: 44, height: 44, alignSelf: 'auto' },
  status: { alignSelf: 'flex-start', borderWidth: 1, borderRadius: 1, paddingHorizontal: 6, paddingVertical: 2 },
  statusText: { fontSize: 10.5, fontWeight: '700', letterSpacing: 1 },
  badgeText: { fontSize: 12, fontWeight: '600' },
  button: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, minHeight: 44, paddingHorizontal: 20, borderRadius: 22, overflow: 'hidden' },
  fab: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    height: 56,
    paddingHorizontal: 18,
    borderRadius: 16,
    overflow: 'hidden',
    elevation: 4,
    shadowColor: '#000',
    shadowOpacity: 0.18,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
  },
  input: { borderWidth: 1.5, borderRadius: 12, paddingHorizontal: 14, minHeight: 50, fontSize: 16 },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 34, paddingHorizontal: 12, borderRadius: 8, borderWidth: 1 },
  segmented: { flexDirection: 'row', borderRadius: 20, borderWidth: 1, overflow: 'hidden' },
  segment: { flex: 1, flexDirection: 'row', gap: 4, alignItems: 'center', justifyContent: 'center', height: 40, paddingHorizontal: 6 },
  sheet: { borderTopLeftRadius: 28, borderTopRightRadius: 28, paddingHorizontal: 20, paddingTop: 10, maxHeight: '90%' },
  grabber: { width: 32, height: 4, borderRadius: 2, alignSelf: 'center', marginBottom: 16 },
});
