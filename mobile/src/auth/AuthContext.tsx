import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokenStore } from '../api/client';
import type { UserView } from '../api/types';

interface AuthState {
  user: UserView | null;
  /** True while checking a stored token at start-up. */
  loading: boolean;
  login: (email: string, password: string) => Promise<UserView>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<UserView | null>(null);
  const [loading, setLoading] = useState(true);

  const logout = useCallback(async () => {
    await tokenStore.clear();
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(() => void logout());
    // Stay signed in across app launches: validate the stored token once.
    tokenStore
      .load()
      .then((token) => (token ? api.auth.me().then(setUser) : undefined))
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false));
  }, [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await api.auth.login(email, password);
    await tokenStore.set(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
