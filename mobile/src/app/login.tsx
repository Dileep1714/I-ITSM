import Ionicons from '@expo/vector-icons/Ionicons';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '@/auth/AuthContext';
import { SERVER_URL } from '@/lib/config';
import { errorMessage } from '@/lib/format';
import { Banner, BrandMark, Button, Input, T } from '@/ui/kit';
import { FIGURE, useColors } from '@/ui/theme';

/** Development builds only: never shown in a release build. */
const DEMO_ACCOUNTS = ['asha', 'ravi', 'kavya', 'admin'];

const HANDLES: [string, string][] = [
  ['Prioritised automatically', 'P1–P4'],
  ['Resolution deadlines tracked and forecast', 'SLA'],
  ['Updates the moment something changes', 'LIVE'],
];

/**
 * Sign-in as the front of the IT service desk (same design as the web app): the form first, then the help people
 * need when they arrive, then what the desk does.
 */
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
      setError('Enter your work email and password.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await login(email.trim(), password);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: c.surface }}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={{ flexGrow: 1 }} keyboardShouldPersistTaps="handled">
          <View style={[styles.band, { borderBottomColor: c.primary }]}>
            <BrandMark size={34} />
          </View>
          <Text style={[styles.ref, { color: c.primary, borderBottomColor: c.divider }]}>IT SERVICE DESK · FORM IT-01 · SIGN-IN</Text>

          {/* The form */}
          <View style={styles.block}>
            <T v="title" style={{ fontSize: 28, fontWeight: '800' }}>
              Sign in
            </T>
            <T muted v="small">
              Use your work email and password.
            </T>
            {error && <Banner tone="danger" title={error} />}
            <Input
              label="Work email"
              value={email}
              onChangeText={setEmail}
              placeholder="name@company.com"
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
            <Button label="Sign in" onPress={submit} loading={busy} style={{ height: 52 }} />

            {__DEV__ && (
              <View style={[styles.dev, { borderTopColor: c.border }]}>
                <Text style={[styles.label, { color: c.muted }]}>DEVELOPMENT ONLY · PASSWORD123</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 14 }}>
                  {DEMO_ACCOUNTS.map((a) => (
                    <Pressable
                      key={a}
                      onPress={() => {
                        setEmail(`${a}@iitsm.example.com`);
                        setPassword('Password123');
                      }}
                      hitSlop={8}
                    >
                      <Text style={{ fontFamily: FIGURE, color: c.primary, fontSize: 13 }}>{a}</Text>
                    </Pressable>
                  ))}
                </View>
              </View>
            )}
          </View>

          {/* Need help? */}
          <View style={[styles.block, styles.ruled, { borderTopColor: c.divider }]}>
            <Text style={[styles.label, { color: c.primary }]}>NEED HELP?</Text>
            <HelpRow title="Forgot your password?" text="Your IT administrator can reset it for you." />
            <HelpRow title="New employee?" text="Create your account with your work email on the I-ITSM website." />
            <HelpRow title="Something urgent?" text="Sign in and report it: critical incidents reach a technician first." last />
          </View>

          {/* What the desk does */}
          <View style={[styles.block, styles.ruled, { borderTopColor: c.divider }]}>
            <Text style={[styles.display, { color: c.text }]}>The IT service desk, open for your problem.</Text>
            <T muted>Report what is broken, follow it until it is fixed, and see every step stamped along the way.</T>
            <View style={{ borderTopWidth: 1, borderTopColor: c.primary, marginTop: 6 }}>
              {HANDLES.map(([text, tag]) => (
                <View key={tag} style={[styles.handle, { borderBottomColor: c.divider }]}>
                  <T v="small" style={{ flex: 1 }}>
                    {text}
                  </T>
                  <Text style={{ fontFamily: FIGURE, color: c.primary, fontSize: 13 }}>{tag}</Text>
                </View>
              ))}
            </View>
          </View>

          <View style={[styles.foot, { borderTopColor: c.primary }]}>
            <Text style={[styles.footText, { color: c.muted }]}>SERVICE MANAGEMENT · INCIDENT RESOLUTION</Text>
            {__DEV__ && <Text style={[styles.footText, { color: c.muted }]}>{SERVER_URL}</Text>}
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function HelpRow({ title, text, last }: { title: string; text: string; last?: boolean }) {
  const c = useColors();
  return (
    <View style={[{ paddingVertical: 12, gap: 2 }, !last && { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: c.divider }]}>
      <T style={{ fontWeight: '700' }}>{title}</T>
      <T v="small" muted>
        {text}
      </T>
    </View>
  );
}

const styles = StyleSheet.create({
  band: { paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1 },
  ref: { fontFamily: FIGURE, fontSize: 12, letterSpacing: 0.4, paddingHorizontal: 20, paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth },
  block: { paddingHorizontal: 20, paddingVertical: 24, gap: 14 },
  ruled: { borderTopWidth: StyleSheet.hairlineWidth },
  label: { fontSize: 11, fontWeight: '800', letterSpacing: 1.3 },
  dev: { borderTopWidth: 1, borderStyle: 'dashed', paddingTop: 12, gap: 8 },
  display: { fontSize: 30, lineHeight: 34, fontWeight: '800', letterSpacing: -0.6 },
  handle: { flexDirection: 'row', alignItems: 'center', paddingVertical: 11, borderBottomWidth: StyleSheet.hairlineWidth, gap: 10 },
  foot: { marginTop: 'auto', borderTopWidth: 1, paddingHorizontal: 20, paddingVertical: 12, gap: 4 },
  footText: { fontFamily: FIGURE, fontSize: 11, letterSpacing: 0.4 },
});
