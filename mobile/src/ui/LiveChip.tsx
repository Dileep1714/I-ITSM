import { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useLiveStatus } from '../realtime/LiveUpdates';
import { useColors } from './theme';

/**
 * Real-time connection chip, as on the web: green with a dot that sends out a ring every two seconds when live,
 * amber and blinking while connecting, grey and struck through when offline. Green appears nowhere else.
 * Under Reduce Motion / Remove animations the dot stays still.
 */
export function LiveChip() {
  const c = useColors();
  const status = useLiveStatus();
  const reduce = useReducedMotion();
  const pulse = useSharedValue(0);

  useEffect(() => {
    cancelAnimation(pulse);
    pulse.value = 0;
    if (reduce || status === 'offline') return;
    pulse.value = withRepeat(
      withTiming(1, { duration: status === 'live' ? 2000 : 1200, easing: status === 'live' ? Easing.out(Easing.exp) : Easing.inOut(Easing.ease) }),
      -1,
      status === 'connecting',
    );
  }, [status, reduce, pulse]);

  const ring = useAnimatedStyle(() => ({
    opacity: status === 'live' ? 0.9 * (1 - pulse.value) : 0,
    transform: [{ scale: 1 + 2.2 * pulse.value }],
  }));
  const dot = useAnimatedStyle(() => ({ opacity: status === 'connecting' ? 1 - 0.8 * pulse.value : 1 }));

  const ink = status === 'live' ? c.live : status === 'connecting' ? c.connecting : c.muted;
  const tint = status === 'live' ? c.liveTint : status === 'connecting' ? c.connectingTint : 'transparent';
  const label = status === 'live' ? 'Live' : status === 'connecting' ? 'Connecting' : 'Offline';

  return (
    <View
      style={[styles.chip, { borderColor: ink, backgroundColor: tint, borderStyle: status === 'connecting' ? 'dashed' : 'solid' }]}
      accessibilityRole="text"
      accessibilityLabel={status === 'live' ? 'Real-time updates on' : `Real-time updates ${label.toLowerCase()}`}
    >
      <View style={styles.dotBox}>
        <Animated.View style={[styles.ring, { borderColor: ink }, ring]} />
        <Animated.View style={[styles.dot, status === 'offline' ? { borderWidth: 1.5, borderColor: ink } : { backgroundColor: ink }, dot]} />
      </View>
      <Text style={[styles.text, { color: ink, textDecorationLine: status === 'offline' ? 'line-through' : 'none' }]}>{label.toUpperCase()}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: { flexDirection: 'row', alignItems: 'center', gap: 7, alignSelf: 'flex-start', borderWidth: 1, borderRadius: 2, paddingHorizontal: 9, paddingVertical: 4 },
  dotBox: { width: 8, height: 8, alignItems: 'center', justifyContent: 'center' },
  ring: { position: 'absolute', width: 8, height: 8, borderRadius: 4, borderWidth: 1.5 },
  dot: { width: 8, height: 8, borderRadius: 4 },
  text: { fontSize: 11, fontWeight: '800', letterSpacing: 1.3 },
});
