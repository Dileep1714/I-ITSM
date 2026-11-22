import Ionicons from '@expo/vector-icons/Ionicons';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '@/auth/AuthContext';
import { SERVER_URL } from '@/lib/config';
import { errorMessage } from '@/lib/format';
import { Banner, BrandMark, Button, Card, Input, T } from '@/ui/kit';
import { useColors } from '@/ui/theme';

const DEMO_ACCOUNTS = [
  { role: 'Employee', email: 'asha@iitsm.example.com' },
  { role: 'Technician (L1)', email: 'ravi@iitsm.example.com' },
  { role: 'Team lead (L2)', email: 'kavya@iitsm.example.com' },
  { role: 'Administrator', email: 'admin@iitsm.example.com' },
];

export default function LoginScreen() {
  const c = useColors();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!/^\S+@\S+$/.test(email.trim()) || !password) {
      setError('Enter your email and password.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await login(email.trim(), password);
      // The navigator switches to the signed-in screens on its own.
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: c.bg }}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={{ padding: 24, gap: 18, flexGrow: 1, justifyContent: 'center' }} keyboardShouldPersistTaps="handled">
          <BrandMark />

          <View>
            <T v="title">Sign in</T>
            <T muted v="small">
              Use your work email and password.
            </T>
          </View>

          {error && <Banner tone="danger" title={error} />}

          <Input
            label="Email"
            value={email}
            onChangeText={setEmail}
            placeholder="you@company.com"
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            textContentType="username"
          />
          <View>
            <Input
              label="Password"
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPassword}
              autoComplete="current-password"
              textContentType="password"
              onSubmitEditing={submit}
              style={{ paddingRight: 44 }}
            />
            <Pressable
              onPress={() => setShowPassword((s) => !s)}
              style={{ position: 'absolute', right: 12, bottom: 12 }}
              hitSlop={8}
              accessibilityLabel={showPassword ? 'Hide password' : 'Show password'}
            >
              <Ionicons name={showPassword ? 'eye-off-outline' : 'eye-outline'} size={20} color={c.muted} />
            </Pressable>
          </View>

          <Button label="Sign in" onPress={submit} loading={busy} />

          {__DEV__ && (
            <Card style={{ gap: 4 }}>
              <T v="label">Demo accounts · password Password123</T>
              {DEMO_ACCOUNTS.map((a) => (
                <Pressable
                  key={a.email}
                  onPress={() => {
                    setEmail(a.email);
                    setPassword('Password123');
                  }}
                  style={{ paddingVertical: 6 }}
                >
                  <T v="small" muted>
                    {a.role}
                  </T>
                  <T v="mono" color={c.primary}>
                    {a.email}
                  </T>
                </Pressable>
              ))}
            </Card>
          )}

          <T v="small" muted style={{ textAlign: 'center' }}>
            Server: {SERVER_URL}
          </T>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
