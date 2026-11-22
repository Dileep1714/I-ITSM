import { CourierPrime_400Regular, CourierPrime_700Bold, useFonts } from '@expo-google-fonts/courier-prime';
import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, type ReactNode } from 'react';
import { AppState, useColorScheme, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { ApiError } from '@/api/client';
import { AuthProvider, useAuth } from '@/auth/AuthContext';
import { BiometricLockProvider } from '@/auth/BiometricLock';
import { LiveUpdatesProvider } from '@/realtime/LiveUpdates';
import { ActionMenuProvider } from '@/ui/actionMenu';
import { Loading } from '@/ui/kit';
import { RoleProvider, useColors } from '@/ui/theme';
import { ToastProvider } from '@/ui/toast';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10_000,
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
});

/** Refetch when the app returns to the foreground. */
function useAppFocusRefetch() {
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => focusManager.setFocused(state === 'active'));
    return () => sub.remove();
  }, []);
}

/** Makes the signed-in role available to useColors (page ground and role accent). */
function RoleBridge({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return <RoleProvider role={user?.role ?? null}>{children}</RoleProvider>;
}

function RootNavigator() {
  const { user, loading } = useAuth();
  const c = useColors();
  // Courier Prime sets the typed figures (incident numbers, times); it ships with the app, so this is quick.
  const [fontsLoaded, fontError] = useFonts({ CourierPrime_400Regular, CourierPrime_700Bold });
  if (loading || (!fontsLoaded && !fontError)) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', backgroundColor: c.bg }}>
        <Loading />
      </View>
    );
  }
  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: c.bg },
        headerTintColor: c.text,
        headerShadowVisible: false,
        headerTitleStyle: { fontWeight: '600' },
        contentStyle: { backgroundColor: c.bg },
      }}
    >
      {/* Signed-in screens exist only while signed in; signing out returns to login automatically. */}
      <Stack.Protected guard={!!user}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="incident/[id]" options={{ title: 'Incident', headerBackButtonDisplayMode: 'minimal' }} />
        <Stack.Protected guard={user?.role === 'EMPLOYEE'}>
          <Stack.Screen name="report" options={{ presentation: 'modal', title: 'Report an incident' }} />
        </Stack.Protected>
      </Stack.Protected>
      <Stack.Protected guard={!user}>
        <Stack.Screen name="login" options={{ headerShown: false }} />
      </Stack.Protected>
    </Stack>
  );
}

export default function RootLayout() {
  useAppFocusRefetch();
  const scheme = useColorScheme();
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
        <QueryClientProvider client={queryClient}>
          <ToastProvider>
            <ActionMenuProvider>
              <AuthProvider>
                <BiometricLockProvider>
                  <LiveUpdatesProvider>
                    <RoleBridge>
                      <StatusBar style="auto" />
                      <RootNavigator />
                    </RoleBridge>
                  </LiveUpdatesProvider>
                </BiometricLockProvider>
              </AuthProvider>
            </ActionMenuProvider>
          </ToastProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </GestureHandlerRootView>
  );
}
