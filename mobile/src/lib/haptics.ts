import * as Haptics from 'expo-haptics';
import { Vibration } from 'react-native';

/**
 * A short vibration after an action or a live alert. Purely cosmetic, so it must never fail: if the expo-haptics
 * native module is missing (some Expo Go builds) it falls back to React Native's core Vibration API, and any other
 * error is ignored.
 */
export function buzz(kind: 'success' | 'warning' = 'success') {
  Haptics.notificationAsync(
    kind === 'success' ? Haptics.NotificationFeedbackType.Success : Haptics.NotificationFeedbackType.Warning,
  ).catch(() => {
    try {
      Vibration.vibrate(kind === 'success' ? 40 : [0, 60, 80, 60]);
    } catch {
      // no vibration available: nothing to do
    }
  });
}
