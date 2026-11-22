import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Constants from 'expo-constants';
import { router, Stack } from 'expo-router';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { api } from '@/api/client';
import type { Availability, IncidentSearch, IncidentStatus, Role, SupportLevel } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { useBiometricLock } from '@/auth/BiometricLock';
import { SERVER_URL } from '@/lib/config';
import { errorMessage, formatDateTime, humanize, OPEN_STATUSES } from '@/lib/format';
import { Avatar, ListRow, Row, Section, T, type IconName } from '@/ui/kit';
import { LiveChip } from '@/ui/LiveChip';
import { FIGURE, useColors } from '@/ui/theme';
import { useToast } from '@/ui/toast';

const AVAILABILITY: { value: Availability; icon: IconName }[] = [
  { value: 'AVAILABLE', icon: 'checkmark-circle-outline' },
  { value: 'BUSY', icon: 'time-outline' },
  { value: 'OFF_DUTY', icon: 'moon-outline' },
  { value: 'ON_LEAVE', icon: 'airplane-outline' },
];

const ROLE_TEXT: Record<Role, string> = {
  EMPLOYEE: 'Reports incidents, answers technicians’ questions and confirms when a fix really works.',
  TECHNICIAN: 'Works the queue by priority: acknowledges, investigates and resolves. Team leads and above assign work.',
  ADMINISTRATOR: 'Runs the desk: users, catalogue, SLA policies, assignment strategies and the audit log.',
};

const LADDER: { level: SupportLevel; short: string; name: string; text: string }[] = [
  { level: 'L1_TECHNICIAN', short: 'L1', name: 'Technician', text: 'First response' },
  { level: 'L2_TEAM_LEAD', short: 'L2', name: 'Team lead', text: 'Assigns work; first escalation' },
  { level: 'L3_SYSTEM_ADMIN', short: 'L3', name: 'System admin', text: 'Deep fixes; second escalation' },
  { level: 'L4_IT_MANAGER', short: 'L4', name: 'IT manager', text: 'Final escalation' },
];

const DONE: IncidentStatus[] = ['RESOLVED', 'VERIFIED', 'CLOSED'];

function useCount(filters: IncidentSearch) {
  return useQuery({
    queryKey: ['incidents', 'count', filters],
    queryFn: () => api.incidents.search(filters, 0, 1),
    select: (page) => page.totalElements,
  }).data;
}

/**
 * The signed-in person's record, in the same world as the web profile: name, their role, their incident
 * record and, for technicians, the escalation ladder, workload slots, availability and skills. Security, the
 * real-time connection and sign-out follow as grouped native sections.
 */
export default function ProfileScreen() {
  const c = useColors();
  const { user, logout } = useAuth();
  const lock = useBiometricLock();
  const toast = useToast();
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ['me', 'profile'], queryFn: api.me.profile });
  const p = profile.data;
  const counts = { open: useCount({ status: OPEN_STATUSES }), done: useCount({ status: DONE }), total: useCount({}) };

  const availability = useMutation({
    mutationFn: api.me.setAvailability,
    onSuccess: (updated) => {
      queryClient.setQueryData(['me', 'profile'], updated);
      toast({ tone: 'success', title: updated.availability === 'AVAILABLE' ? 'You will receive new assignments' : `Marked ${humanize(updated.availability).toLowerCase()}` });
    },
    onError: (e) => toast({ tone: 'error', title: 'Not possible', message: errorMessage(e) }),
  });

  if (!user) return null;
  const isTech = user.role === 'TECHNICIAN';
  const levelIndex = LADDER.findIndex((l) => l.level === p?.supportLevel);
  const max = p?.maxActiveIncidents ?? 0;
  const active = p?.activeIncidents ?? 0;
  const current = p?.availability ?? 'AVAILABLE';
  const recordNoun = user.role === 'EMPLOYEE' ? 'reported by you' : isTech ? 'assigned to you' : 'across the desk';

  return (
    <>
      <Stack.Screen options={{ title: 'Profile' }} />
      <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={{ padding: 16, gap: 24 }}>
        {/* Record card */}
        <View style={[styles.sheet, { backgroundColor: c.surface, borderColor: c.border }]}>
          <View style={{ padding: 16, gap: 12 }}>
            <Row gap={12}>
              <Avatar name={user.fullName} size={52} />
              <View style={{ flex: 1, gap: 2 }}>
                <Text style={[styles.cap, { color: c.accent }]}>{humanize(user.role)}</Text>
                {isTech && levelIndex >= 0 && (
                  <Text style={{ fontFamily: FIGURE, fontSize: 14, color: c.text }}>
                    {LADDER[levelIndex].short} · {LADDER[levelIndex].name}
                  </Text>
                )}
              </View>
            </Row>
            <View style={{ gap: 8 }}>
              <Text style={{ fontSize: 32, lineHeight: 36, fontWeight: '800', letterSpacing: -0.6, color: c.text }} accessibilityRole="header">
                {user.fullName}
              </Text>
              <View style={{ width: 44, height: 4, backgroundColor: c.accent }} />
            </View>
            <Text style={{ fontFamily: FIGURE, fontSize: 14, color: c.primary }} selectable>
              {user.email}
            </Text>
          </View>
          <View style={[styles.fields, { borderTopColor: c.divider }]}>
            {p?.department ? <Field label="Department" value={p.department} /> : null}
            {p?.location ? <Field label="Location" value={p.location} /> : null}
            {isTech && p?.experienceYears != null ? <Field label="Experience" value={`${p.experienceYears} ${p.experienceYears === 1 ? 'year' : 'years'}`} /> : null}
            {p ? <Field label="Member since" value={formatDateTime(p.createdAt)} /> : null}
          </View>
        </View>

        {/* Your role */}
        <View style={{ gap: 8 }}>
          <T v="heading">Your role</T>
          <View style={[styles.sheet, { backgroundColor: c.surface, borderColor: c.accent, borderWidth: 2, padding: 16, gap: 6 }]}>
            <Row gap={10}>
              <View style={{ width: 14, height: 14, backgroundColor: c.accent }} />
              <T v="title">{humanize(user.role)}</T>
            </Row>
            <T v="small">{ROLE_TEXT[user.role]}</T>
          </View>
        </View>

        {/* Incident record */}
        <View style={{ gap: 8 }}>
          <T v="heading">Incident record</T>
          <T v="small" muted>
            Incidents {recordNoun}.
          </T>
          <View style={[styles.sheet, styles.counts, { backgroundColor: c.surface, borderColor: c.border }]}>
            {[
              { label: 'Open', value: counts.open },
              { label: 'Resolved', value: counts.done },
              { label: 'All time', value: counts.total },
            ].map((x, index) => (
              <Pressable
                key={x.label}
                onPress={() => router.navigate('/incidents')}
                android_ripple={{ color: c.divider }}
                style={[styles.count, index > 0 && { borderLeftWidth: StyleSheet.hairlineWidth, borderLeftColor: c.border }]}
                accessibilityRole="button"
                accessibilityLabel={`${x.label}: ${x.value ?? 'loading'}`}
              >
                <Text style={[styles.cap, { color: c.primary }]}>{x.label}</Text>
                <Text style={{ fontFamily: FIGURE, fontSize: 30, color: c.primary }}>{x.value ?? '–'}</Text>
              </Pressable>
            ))}
          </View>
        </View>

        {isTech && p && (
          <>
            {/* Escalation ladder */}
            <View style={{ gap: 8 }}>
              <T v="heading">Escalation ladder</T>
              <T v="small" muted>
                When an SLA is at risk, the incident climbs one rung at a time until someone with the authority to act has it.
              </T>
              <View style={[styles.ladder, { borderColor: c.border }]}>
                {LADDER.map((l, index) => {
                  const here = index === levelIndex;
                  return (
                    <View
                      key={l.level}
                      style={[
                        styles.rung,
                        { borderColor: c.border, backgroundColor: here ? c.primary : index < levelIndex ? c.bg : c.surface },
                      ]}
                      accessibilityLabel={`${l.short} ${l.name}${here ? ', you are here' : ''}`}
                    >
                      <Text style={{ fontFamily: FIGURE, fontSize: 22, color: here ? c.onPrimary : c.text }}>{l.short}</Text>
                      <Text style={{ fontWeight: '700', color: here ? c.onPrimary : c.text }}>{l.name}</Text>
                      <Text style={{ fontSize: 12, color: here ? c.onPrimary : c.muted }}>{l.text}</Text>
                      {here && <Text style={[styles.cap, { color: c.onPrimary, marginTop: 4 }]}>You are here</Text>}
                    </View>
                  );
                })}
              </View>
            </View>

            {/* Workload */}
            {max > 0 && (
              <View style={{ gap: 8 }}>
                <T v="heading">Workload</T>
                <T v="small" muted>
                  {active} of {max} slots in use. {active >= max ? 'You are at capacity: new assignments go to someone else.' : `${max - active} free for new assignments.`}
                </T>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }} accessible accessibilityLabel={`${active} of ${max} incident slots in use`}>
                  {Array.from({ length: Math.max(max, active) }, (_, i) => (
                    <View
                      key={i}
                      style={[
                        styles.slot,
                        i < Math.min(active, max)
                          ? { backgroundColor: c.primary, borderColor: c.primary }
                          : i >= max
                            ? { borderColor: c.danger, borderWidth: 2.5 }
                            : { borderColor: c.border, borderStyle: 'dashed', backgroundColor: c.surface },
                      ]}
                    />
                  ))}
                </View>
              </View>
            )}

            {/* Availability: four real buttons; the current one is filled with a check */}
            <View style={{ gap: 8 }}>
              <T v="heading">Availability</T>
              <T v="small" muted>
                Only available technicians receive new automatic assignments. Incidents you already own stay with you.
              </T>
              <View style={styles.availability} accessibilityRole="radiogroup">
                {AVAILABILITY.map((a) => {
                  const selected = current === a.value;
                  const busy = availability.isPending && availability.variables === a.value;
                  return (
                    <Pressable
                      key={a.value}
                      onPress={() => !selected && availability.mutate(a.value)}
                      disabled={availability.isPending}
                      android_ripple={{ color: (selected ? c.onPrimary : c.primary) + '33' }}
                      accessibilityRole="radio"
                      accessibilityState={{ checked: selected, busy, disabled: availability.isPending }}
                      style={({ pressed }) => [
                        styles.availButton,
                        selected ? { backgroundColor: c.primary, borderColor: c.primary } : { borderColor: c.primary, backgroundColor: c.surface },
                        pressed && { opacity: 0.85 },
                      ]}
                    >
                      {busy ? (
                        <ActivityIndicator size="small" color={selected ? c.onPrimary : c.primary} />
                      ) : (
                        <Ionicons name={selected ? 'checkmark' : a.icon} size={18} color={selected ? c.onPrimary : c.primary} />
                      )}
                      <Text style={{ fontWeight: '700', fontSize: 15, color: selected ? c.onPrimary : c.primary }}>{humanize(a.value)}</Text>
                    </Pressable>
                  );
                })}
              </View>
              <T v="small" muted>
                {current === 'AVAILABLE' ? 'You are receiving new assignments.' : `You are marked ${humanize(current).toLowerCase()}: no new automatic assignments.`}
              </T>
            </View>

            {/* Skills */}
            {p.skills.length > 0 && (
              <View style={{ gap: 8 }}>
                <T v="heading">Skills</T>
                <T v="small" muted>
                  Proficiency out of 5. Skill-based assignment ranks you higher for these categories.
                </T>
                <View style={[styles.sheet, { backgroundColor: c.surface, borderColor: c.border }]}>
                  {[...p.skills]
                    .sort((a, b) => b.proficiency - a.proficiency)
                    .map((s, index) => (
                      <View
                        key={s.categoryId}
                        style={[styles.skill, index > 0 && { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: c.divider }]}
                        accessibilityLabel={`${s.categoryName}, ${s.proficiency} out of 5`}
                      >
                        <T>{s.categoryName}</T>
                        <Row gap={4}>
                          {[1, 2, 3, 4, 5].map((n) => (
                            <View key={n} style={[styles.pip, { borderColor: c.primary }, n <= s.proficiency && { backgroundColor: c.primary }]} />
                          ))}
                        </Row>
                      </View>
                    ))}
                </View>
              </View>
            )}
          </>
        )}

        <Section title="SECURITY">
          {lock.available ? (
            <ListRow
              icon="finger-print"
              title="Unlock with biometrics"
              subtitle="Fingerprint or face when opening the app"
              trailing={<Switch value={lock.enabled} onValueChange={(on) => void lock.setEnabled(on)} trackColor={{ true: c.primary }} />}
            />
          ) : (
            <ListRow icon="finger-print" title="Biometric unlock" subtitle="No fingerprint or face enrolled on this device" />
          )}
          <ListRow icon="key-outline" title="Session" subtitle="Stored in the device's encrypted keystore" />
        </Section>

        {user.role === 'ADMINISTRATOR' && (
          <Section title="ADMINISTRATION">
            <ListRow icon="desktop-outline" title="Users, catalogue, SLA policies" subtitle="Configured from the web app on a computer" />
          </Section>
        )}

        <Section title="ABOUT">
          <ListRow icon="pulse-outline" title="Real-time updates" trailing={<LiveChip />} />
          <ListRow icon="server-outline" title="Server" subtitle={SERVER_URL} />
          <ListRow icon="information-circle-outline" title="Version" subtitle={Constants.expoConfig?.version ?? '—'} />
        </Section>

        <Section>
          <ListRow
            icon="log-out-outline"
            title="Sign out"
            destructive
            onPress={() =>
              Alert.alert('Sign out?', undefined, [
                { text: 'Cancel', style: 'cancel' },
                { text: 'Sign out', style: 'destructive', onPress: () => void logout() },
              ])
            }
          />
        </Section>
      </ScrollView>
    </>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  const c = useColors();
  return (
    <View style={[styles.field, { borderColor: c.divider }]}>
      <Text style={[styles.cap, { color: c.primary }]}>{label}</Text>
      <T v="small" style={{ fontWeight: '500' }}>
        {value}
      </T>
    </View>
  );
}

const styles = StyleSheet.create({
  sheet: { borderRadius: 14, borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  cap: { fontSize: 11, fontWeight: '800', letterSpacing: 1.3, textTransform: 'uppercase' },
  fields: { flexDirection: 'row', flexWrap: 'wrap', borderTopWidth: StyleSheet.hairlineWidth },
  field: { width: '50%', paddingHorizontal: 16, paddingVertical: 10, gap: 2, borderTopWidth: StyleSheet.hairlineWidth, marginTop: -StyleSheet.hairlineWidth },
  counts: { flexDirection: 'row' },
  count: { flex: 1, paddingHorizontal: 14, paddingVertical: 12, gap: 2 },
  ladder: { flexDirection: 'row', flexWrap: 'wrap', borderRadius: 14, borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  rung: { width: '50%', padding: 14, gap: 2, minHeight: 112, borderWidth: StyleSheet.hairlineWidth, margin: -StyleSheet.hairlineWidth / 2 },
  slot: { width: 40, height: 52, borderRadius: 4, borderWidth: 1 },
  availability: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  availButton: {
    flexBasis: '47%',
    flexGrow: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 50,
    borderRadius: 25,
    borderWidth: 1.5,
    overflow: 'hidden',
  },
  skill: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 12, minHeight: 48 },
  pip: { width: 14, height: 14, borderWidth: 1.5, borderRadius: 2 },
});
