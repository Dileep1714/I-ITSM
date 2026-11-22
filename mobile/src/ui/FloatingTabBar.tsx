import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import * as Haptics from 'expo-haptics';
import type { BottomTabBarProps } from 'expo-router/js-tabs';
import { type ComponentProps, useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, useColorScheme, View } from 'react-native';
import Animated, { useAnimatedStyle, useReducedMotion, useSharedValue, withSpring } from 'react-native-reanimated';
import { useColors } from './theme';

type McIcon = ComponentProps<typeof MaterialCommunityIcons>['name'];

export interface FloatingTab {
  /** Route name in the tabs folder. */
  name: string;
  label: string;
  icon: { default: McIcon; selected: McIcon };
  badge?: string;
}

/** Material 3 springs: quick and lightly damped, as in the expressive motion scheme. */
const SPRING = { damping: 22, stiffness: 260, mass: 0.9 };

/**
 * Android's navigation bar, shaped like the iOS 26 floating tab bar so both platforms navigate alike: a detached
 * capsule above the gesture area, with a capsule indicator that slides to the selected destination. Everything
 * else stays Material 3: tonal surface container, secondary-container indicator, filled icon when selected,
 * ripple, 48dp+ targets and a haptic tick. Under "Remove animations" the indicator jumps instead of sliding.
 */
export function FloatingTabBar({ state, descriptors, emitter, navigateToTab, insets, tabs }: BottomTabBarProps & { tabs: FloatingTab[] }) {
  const c = useColors();
  const scheme = useColorScheme();
  const reduceMotion = useReducedMotion();

  // Only the destinations this role has, in the order of the tabs config.
  const routes = state.routes.filter((r) => tabs.some((t) => t.name === r.name));
  const focusedKey = state.routes[state.index]?.key;
  const focusedIndex = Math.max(0, routes.findIndex((r) => r.key === focusedKey));

  const [layouts, setLayouts] = useState<Record<number, { x: number; width: number }>>({});
  const x = useSharedValue(0);
  const width = useSharedValue(0);

  useEffect(() => {
    const target = layouts[focusedIndex];
    if (!target) return;
    if (reduceMotion || width.value === 0) {
      x.value = target.x;
      width.value = target.width;
    } else {
      x.value = withSpring(target.x, SPRING);
      width.value = withSpring(target.width, SPRING);
    }
  }, [focusedIndex, layouts, reduceMotion, x, width]);

  const indicatorStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: x.value }],
    width: width.value,
    opacity: width.value === 0 ? 0 : 1,
  }));

  // Tonal surface container (M3 "surface container high") and secondary-container indicator, from carbon.
  const container = scheme === 'dark' ? '#262836' : '#ffffff';
  const indicator = c.primaryContainer;
  const onIndicator = c.onPrimaryContainer;
  const idle = c.muted;

  return (
    <View style={[styles.wrap, { paddingBottom: Math.max(insets.bottom, 8) + 6, backgroundColor: c.bg }]}>
      <View
        style={[styles.bar, { backgroundColor: container, borderColor: scheme === 'dark' ? '#363949' : '#e3e4ee' }]}
        accessibilityRole="tablist"
      >
        <Animated.View pointerEvents="none" style={[styles.indicator, { backgroundColor: indicator }, indicatorStyle]} />
        {routes.map((route, index) => {
          const tab = tabs.find((t) => t.name === route.name)!;
          const focused = index === focusedIndex;
          const options = descriptors[route.key]?.options;
          const label = tab.label ?? options?.title ?? route.name;
          const onPress = () => {
            const event = emitter.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
            if (!focused && !event.defaultPrevented) {
              Haptics.selectionAsync().catch(() => undefined);
              navigateToTab(route.key);
            }
          };
          return (
            <Pressable
              key={route.key}
              onPress={onPress}
              onLongPress={() => emitter.emit({ type: 'tabLongPress', target: route.key })}
              onLayout={(e: LayoutChangeEvent) => {
                const { x: lx, width: lw } = e.nativeEvent.layout;
                setLayouts((prev) => (prev[index]?.x === lx && prev[index]?.width === lw ? prev : { ...prev, [index]: { x: lx, width: lw } }));
              }}
              android_ripple={{ color: onIndicator + '22', borderless: true, radius: 36 }}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
              accessibilityLabel={tab.badge ? `${label}, ${tab.badge} unread` : label}
              style={styles.item}
            >
              <View>
                <MaterialCommunityIcons name={focused ? tab.icon.selected : tab.icon.default} size={24} color={focused ? onIndicator : idle} />
                {tab.badge ? (
                  <View style={[styles.badge, { backgroundColor: c.primary, borderColor: container }]}>
                    <Text style={[styles.badgeText, { color: c.onPrimary }]}>{tab.badge}</Text>
                  </View>
                ) : null}
              </View>
              <Text numberOfLines={1} style={[styles.label, { color: focused ? onIndicator : idle, fontWeight: focused ? '700' : '500' }]}>
                {label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingHorizontal: 16, paddingTop: 6 },
  bar: {
    flexDirection: 'row',
    height: 68,
    borderRadius: 34,
    padding: 4,
    borderWidth: StyleSheet.hairlineWidth,
    elevation: 6,
    shadowColor: '#171a4c',
    shadowOpacity: 0.16,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  indicator: { position: 'absolute', top: 4, bottom: 4, left: 0, borderRadius: 30 },
  item: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 2, borderRadius: 30, minHeight: 48 },
  label: { fontSize: 12, letterSpacing: 0.2 },
  badge: {
    position: 'absolute',
    top: -4,
    left: 14,
    minWidth: 18,
    height: 18,
    paddingHorizontal: 4,
    borderRadius: 9,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { fontSize: 10, fontWeight: '700' },
});
