import { Stack } from 'expo-router';
import { Platform } from 'react-native';
import { useColors } from './theme';

/**
 * The native stack used inside every tab: gives each tab its own header (large collapsing title on iOS,
 * Material top app bar on Android) and its own back history.
 */
export function TabStack() {
  const c = useColors();
  return (
    <Stack
      screenOptions={{
        headerLargeTitle: Platform.OS === 'ios',
        headerLargeTitleShadowVisible: false,
        headerShadowVisible: false,
        headerStyle: { backgroundColor: c.bg },
        headerTintColor: c.text,
        headerTitleStyle: { fontWeight: '600' },
        contentStyle: { backgroundColor: c.bg },
      }}
    />
  );
}
