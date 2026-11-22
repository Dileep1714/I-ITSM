import Ionicons from '@expo/vector-icons/Ionicons';
import * as LocalAuthentication from 'expo-local-authentication';
import * as SecureStore from 'expo-secure-store';
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, AppState, StyleSheet, View } from 'react-native';
import { Button, T } from '../ui/kit';
import { useColors } from '../ui/theme';
import { useAuth } from './AuthContext';

const SETTING_KEY = 'iitsm.biometricLock';
/** Lock again when the app has been in the background longer than this. */
const RELOCK_AFTER_MS = 60_000;

interface BiometricState {
  /** Device has a fingerprint / face sensor with an enrolled biometric. */
  available: boolean;
  enabled: boolean;
  setEnabled: (on: boolean) => Promise<void>;
}

const BiometricContext = createContext<BiometricState>({ available: false, enabled: false, setEnabled: async () => {} });
export const useBiometricLock = () => useContext(BiometricContext);

async function isAvailable() {
  try {
    return (await LocalAuthentication.hasHardwareAsync()) && (await LocalAuthentication.isEnrolledAsync());
  } catch {
    return false; // module unavailable: the feature simply does not appear
  }
}

/**
 * Optional app lock: when on, the signed-in app is covered until the user confirms with fingerprint / face
 * (or the device PIN), at start-up and after a minute in the background. The session token itself stays in the
 * device keystore either way.
 */
export function BiometricLockProvider({ children }: { children: ReactNode }) {
  const c = useColors();
  const { user, logout } = useAuth();
  const [available, setAvailable] = useState(false);
  const [enabled, setEnabledState] = useState(false);
  const [locked, setLocked] = useState(false);
  const [ready, setReady] = useState(false);
  const backgroundedAt = useRef<number | null>(null);
  const offered = useRef(false);

  const unlock = useCallback(async () => {
    try {
      const result = await LocalAuthentication.authenticateAsync({ promptMessage: 'Unlock I-ITSM', cancelLabel: 'Cancel' });
      if (result.success) setLocked(false);
    } catch {
      setLocked(false); // never trap the user behind a broken prompt
    }
  }, []);

  // Read the setting once; lock immediately at start-up if it is on.
  useEffect(() => {
    void (async () => {
      const [hasBiometrics, stored] = await Promise.all([isAvailable(), SecureStore.getItemAsync(SETTING_KEY).catch(() => null)]);
      setAvailable(hasBiometrics);
      const on = hasBiometrics && stored === 'on';
      setEnabledState(on);
      if (on && user) setLocked(true);
      offered.current = stored !== null;
      setReady(true);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- start-up only
  }, []);

  // After the first sign-in on a device with biometrics, offer to turn it on (once).
  useEffect(() => {
    if (!ready || !user || !available || offered.current) return;
    offered.current = true;
    Alert.alert('Unlock with fingerprint or face?', 'Protect I-ITSM with your device biometrics instead of signing in again.', [
      { text: 'Not now', style: 'cancel', onPress: () => void SecureStore.setItemAsync(SETTING_KEY, 'off') },
      { text: 'Turn on', onPress: () => void setEnabled(true) },
    ]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, user, available]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'background') backgroundedAt.current = Date.now();
      if (state === 'active' && backgroundedAt.current && enabled && user && Date.now() - backgroundedAt.current > RELOCK_AFTER_MS) {
        setLocked(true);
      }
    });
    return () => sub.remove();
  }, [enabled, user]);

  // Prompt as soon as the lock screen appears.
  useEffect(() => {
    if (locked) void unlock();
  }, [locked, unlock]);

  const setEnabled = useCallback(async (on: boolean) => {
    if (on) {
      const result = await LocalAuthentication.authenticateAsync({ promptMessage: 'Confirm to turn on the app lock' }).catch(() => null);
      if (!result?.success) return;
    }
    await SecureStore.setItemAsync(SETTING_KEY, on ? 'on' : 'off');
    setEnabledState(on);
  }, []);

  return (
    <BiometricContext.Provider value={{ available, enabled, setEnabled }}>
      {children}
      {locked && user && (
        <View style={[StyleSheet.absoluteFill, { backgroundColor: c.bg, alignItems: 'center', justifyContent: 'center', gap: 16, padding: 32 }]}>
          <View style={{ width: 84, height: 84, borderRadius: 42, backgroundColor: c.primaryContainer, alignItems: 'center', justifyContent: 'center' }}>
            <Ionicons name="finger-print" size={44} color={c.onPrimaryContainer} />
          </View>
          <T v="title">I-ITSM is locked</T>
          <T muted style={{ textAlign: 'center' }}>
            Signed in as {user.fullName}
          </T>
          <Button label="Unlock" icon="lock-open-outline" onPress={() => void unlock()} />
          <Button label="Sign out instead" variant="text" onPress={() => void logout().then(() => setLocked(false))} />
        </View>
      )}
    </BiometricContext.Provider>
  );
}
