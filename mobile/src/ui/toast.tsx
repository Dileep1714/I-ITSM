import Ionicons from '@expo/vector-icons/Ionicons';
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

type Tone = 'info' | 'success' | 'error';
interface ToastMessage {
  title: string;
  message?: string;
  tone: Tone;
  onPress?: () => void;
}

const ToastContext = createContext<(toast: ToastMessage) => void>(() => {});

const TONE: Record<Tone, { icon: 'information-circle' | 'checkmark-circle' | 'alert-circle'; color: string }> = {
  info: { icon: 'information-circle', color: '#4c6ef5' },
  success: { icon: 'checkmark-circle', color: '#40c057' },
  error: { icon: 'alert-circle', color: '#fa5252' },
};

/** In-app banner that drops in from the top (results of actions, and live notifications). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const insets = useSafeAreaInsets();
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const anim = useRef(new Animated.Value(0)).current;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const hide = useCallback(() => {
    Animated.timing(anim, { toValue: 0, duration: 180, useNativeDriver: true }).start(() => setToast(null));
  }, [anim]);

  const show = useCallback(
    (next: ToastMessage) => {
      if (timer.current) clearTimeout(timer.current);
      setToast(next);
      anim.setValue(0);
      Animated.spring(anim, { toValue: 1, useNativeDriver: true, bounciness: 4 }).start();
      timer.current = setTimeout(hide, next.tone === 'error' ? 5000 : 3500);
    },
    [anim, hide],
  );

  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && (
        <Animated.View
          pointerEvents="box-none"
          style={[
            styles.wrap,
            { top: insets.top + 8, opacity: anim, transform: [{ translateY: anim.interpolate({ inputRange: [0, 1], outputRange: [-20, 0] }) }] },
          ]}
        >
          <Pressable
            onPress={() => {
              toast.onPress?.();
              hide();
            }}
            style={styles.toast}
            accessibilityRole="alert"
          >
            <Ionicons name={TONE[toast.tone].icon} size={22} color={TONE[toast.tone].color} />
            <View style={{ flex: 1 }}>
              <Text style={styles.title}>{toast.title}</Text>
              {toast.message ? <Text style={styles.message}>{toast.message}</Text> : null}
            </View>
          </Pressable>
        </Animated.View>
      )}
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);

const styles = StyleSheet.create({
  wrap: { position: 'absolute', left: 12, right: 12, zIndex: 1000 },
  toast: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: '#1f2430',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    elevation: 6,
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
  },
  title: { color: '#fff', fontWeight: '600', fontSize: 14 },
  message: { color: '#c9ced8', fontSize: 13, marginTop: 2 },
});
