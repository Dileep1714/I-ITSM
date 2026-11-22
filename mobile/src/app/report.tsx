import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, Stack } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Image, KeyboardAvoidingView, Platform, Pressable, ScrollView, View } from 'react-native';
import { ApiError, api } from '@/api/client';
import type { Impact, Urgency } from '@/api/types';
import { errorMessage, humanize } from '@/lib/format';
import { buzz } from '@/lib/haptics';
import { pickPhoto, type PickedPhoto } from '@/lib/photos';
import { useActionMenu } from '@/ui/actionMenu';
import { Badge, Banner, Button, Card, Chips, Input, PriorityBadge, Row, Segmented, StatusBadge, T } from '@/ui/kit';
import { BeforeYouReport, ReportChat, useAiAvailable } from '@/ui/ReportAssist';
import type { ReportDraft } from '@/api/types';
import { hue, useColors } from '@/ui/theme';
import { useToast } from '@/ui/toast';

const IMPACTS: Impact[] = ['LOW', 'MEDIUM', 'HIGH', 'EXTENSIVE'];
const URGENCIES: Urgency[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const IMPACT_HELP: Record<Impact, string> = {
  LOW: 'Only me, minor inconvenience',
  MEDIUM: 'My team, or one function degraded',
  HIGH: 'A department, or a key function down',
  EXTENSIVE: 'Many departments or the whole organisation',
};

function useDebounced<T>(input: T, ms: number): T {
  const [value, setValue] = useState(input);
  const key = JSON.stringify(input);
  useEffect(() => {
    const t = setTimeout(() => setValue(input), ms);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- compare by content, not identity
  }, [key, ms]);
  return value;
}

/** Report an incident: opened as a modal from the floating "Report" button. */
export default function ReportScreen() {
  const c = useColors();
  const toast = useToast();
  const menu = useActionMenu();
  const queryClient = useQueryClient();
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.catalog.categories });

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [impact, setImpact] = useState<Impact>('LOW');
  const [urgency, setUrgency] = useState<Urgency>('MEDIUM');
  const [affectedUsers, setAffectedUsers] = useState('1');
  const [photos, setPhotos] = useState<PickedPhoto[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const aiAvailable = useAiAvailable();
  const [mode, setMode] = useState<'form' | 'chat'>('form');
  /** The assistant's draft fills the form; the employee reviews and submits it. */
  const useDraft = (d: ReportDraft) => {
    if (d.title) setTitle(d.title);
    if (d.description) setDescription(d.description);
    if (d.serviceId != null) setServiceId(d.serviceId);
    if (d.categoryId != null) setCategoryId(d.categoryId);
    setImpact(d.impact);
    setUrgency(d.urgency);
    setAffectedUsers(String(d.affectedUsers || 1));
    setMode('form');
  };

  const previewInput = useDebounced({ serviceId, impact, urgency, affectedUsers: Number(affectedUsers) || 1 }, 300);
  const preview = useQuery({
    queryKey: ['priority-preview', previewInput],
    queryFn: () => api.catalog.previewPriority({ ...previewInput, serviceId: previewInput.serviceId! }),
    enabled: previewInput.serviceId !== null,
  });

  // Intelligent features while typing: category suggestion and "already reported?".
  const draft = useDebounced({ title: title.trim(), description: description.trim(), serviceId }, 500);
  const analysis = useQuery({
    queryKey: ['draft-analysis', draft],
    queryFn: () => api.intelligence.analyze(draft),
    enabled: draft.title.length >= 4 || draft.description.length >= 10,
    placeholderData: (previous) => previous,
  });
  const suggestion = analysis.data?.categorySuggestions[0];
  const alreadyReported = analysis.data?.similarIncidents ?? [];

  const report = useMutation({
    mutationFn: async (allowDuplicate: boolean) => {
      const incident = await api.incidents.report({
        title: title.trim(),
        description: description.trim(),
        serviceId: serviceId!,
        categoryId,
        impact,
        urgency,
        affectedUsers: Number(affectedUsers) || 1,
        allowDuplicate,
      });
      // Photos go up after the incident exists; a failed upload does not lose the report.
      const failed = (await Promise.allSettled(photos.map((p) => api.attachments.upload(incident.id, p)))).filter((r) => r.status === 'rejected').length;
      return { incident, failed };
    },
    onSuccess: ({ incident, failed }) => {
      buzz('success');
      queryClient.invalidateQueries({ queryKey: ['incidents'] });
      toast({
        tone: failed ? 'error' : 'success',
        title: `Incident ${incident.number} reported`,
        message: failed ? `${failed} photo(s) could not be uploaded` : incident.title,
      });
      router.dismiss();
      router.push(`/incident/${incident.id}`);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'DUPLICATE_INCIDENT') {
        Alert.alert('Already reported?', e.message, [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Submit anyway', onPress: () => report.mutate(true) },
        ]);
      }
    },
  });

  const submit = () => {
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = 'Enter a short title';
    if (description.trim().length < 10) next.description = 'Describe the problem (at least 10 characters)';
    if (!serviceId) next.service = 'Choose the affected service';
    if (!(Number(affectedUsers) >= 1)) next.affectedUsers = 'At least 1';
    setErrors(next);
    if (Object.keys(next).length === 0) report.mutate(false);
  };

  const addPhoto = () =>
    menu('Add a photo', [
      { label: 'Take photo', icon: 'camera-outline', onPress: () => void pickPhoto('camera').then((p) => p && setPhotos((all) => [...all, p])) },
      { label: 'Choose from gallery', icon: 'images-outline', onPress: () => void pickPhoto('library').then((p) => p && setPhotos((all) => [...all, p])) },
    ]);

  const otherError = report.error && !(report.error instanceof ApiError && report.error.code === 'DUPLICATE_INCIDENT');

  return (
    <>
      <Stack.Screen
        options={{
          title: 'Report an incident',
          headerRight: () => (
            <Pressable onPress={submit} disabled={report.isPending} hitSlop={10}>
              <T color={c.primary} style={{ fontWeight: '700' }}>
                {report.isPending ? 'Sending…' : 'Submit'}
              </T>
            </Pressable>
          ),
        }}
      />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }} keyboardVerticalOffset={100}>
        <ScrollView contentContainerStyle={{ padding: 16, gap: 18 }} keyboardShouldPersistTaps="handled">
          {otherError && <Banner tone="danger" title="Could not report the incident">{errorMessage(report.error)}</Banner>}

          {aiAvailable && (
            <Segmented
              options={[
                { value: 'form', label: 'Fill in the form' },
                { value: 'chat', label: 'Chat with assistant' },
              ]}
              value={mode}
              onChange={setMode}
            />
          )}
          {mode === 'chat' && aiAvailable ? (
            <ReportChat onUseDraft={useDraft} />
          ) : (
          <>

          <Input label="What's wrong?" value={title} onChangeText={setTitle} placeholder="e.g. Cannot connect to VPN" error={errors.title} maxLength={150} autoFocus />
          <Input
            label="Details"
            value={description}
            onChangeText={setDescription}
            placeholder="What happened, when it started, any error messages…"
            multiline
            error={errors.description}
          />

          {/* Photos of the error screen */}
          <View style={{ gap: 8 }}>
            <T v="small" style={{ fontWeight: '600' }}>
              Photos (optional)
            </T>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 10 }}>
              {photos.map((p, index) => (
                <View key={p.uri}>
                  <Image source={{ uri: p.uri }} style={{ width: 84, height: 84, borderRadius: 12 }} />
                  <Pressable
                    onPress={() => setPhotos((all) => all.filter((_, n) => n !== index))}
                    hitSlop={8}
                    style={{ position: 'absolute', top: 4, right: 4, backgroundColor: 'rgba(0,0,0,0.55)', borderRadius: 12, padding: 2 }}
                    accessibilityLabel="Remove photo"
                  >
                    <Ionicons name="close" size={16} color="#fff" />
                  </Pressable>
                </View>
              ))}
              <Pressable
                onPress={addPhoto}
                style={{ width: 84, height: 84, borderRadius: 12, borderWidth: 1.5, borderStyle: 'dashed', borderColor: c.border, alignItems: 'center', justifyContent: 'center', gap: 4 }}
                accessibilityLabel="Add a photo"
              >
                <Ionicons name="camera-outline" size={24} color={c.primary} />
                <T v="small" color={c.primary}>
                  Add
                </T>
              </Pressable>
            </ScrollView>
          </View>

          {(suggestion || alreadyReported.length > 0) && (
            <Card style={{ gap: 12, backgroundColor: c.primaryContainer }}>
              <Row gap={8}>
                <Ionicons name="sparkles" size={18} color={c.onPrimaryContainer} />
                <T color={c.onPrimaryContainer} style={{ fontWeight: '600' }}>
                  Smart assistant
                </T>
              </Row>
              {suggestion && (
                <Row style={{ justifyContent: 'space-between' }} gap={10}>
                  <View style={{ flex: 1 }}>
                    <T v="small" color={c.onPrimaryContainer}>
                      Looks like <T v="small" color={c.onPrimaryContainer} style={{ fontWeight: '700' }}>{suggestion.name}</T> ({Math.round(suggestion.confidence * 100)}%)
                    </T>
                    <T v="small" color={c.onPrimaryContainer} lines={2}>
                      {suggestion.reasons.join(' · ')}
                    </T>
                  </View>
                  {categoryId !== suggestion.categoryId && <Button label="Use" variant="filled" onPress={() => setCategoryId(suggestion.categoryId)} />}
                </Row>
              )}
              {alreadyReported.length > 0 && (
                <View style={{ gap: 6 }}>
                  <T v="small" color={c.onPrimaryContainer} style={{ fontWeight: '600' }}>
                    Possibly already reported
                  </T>
                  {alreadyReported.slice(0, 3).map((r) => (
                    <Pressable key={r.id} onPress={() => router.push(`/incident/${r.id}`)}>
                      <Row gap={6}>
                        <StatusBadge status={r.status} />
                        <T v="small" color={c.onPrimaryContainer} style={{ flex: 1 }} lines={1}>
                          {r.number} {r.title}
                        </T>
                      </Row>
                    </Pressable>
                  ))}
                </View>
              )}
            </Card>
          )}

          <Chips
            label="Affected service"
            options={(services.data ?? []).map((s) => ({ value: s.id, label: s.name }))}
            value={serviceId}
            onChange={setServiceId}
            hint={errors.service ? <T v="small" color={c.danger}>{errors.service}</T> : undefined}
          />
          <BeforeYouReport title={title} description={description} serviceId={serviceId} onFixed={() => router.back()} />
          <Chips
            label="Category (optional)"
            options={(categories.data ?? []).filter((x) => x.active).map((x) => ({ value: x.id, label: x.name }))}
            value={categoryId}
            onChange={(v) => setCategoryId(v === categoryId ? null : v)}
            hint="Detected automatically if left empty."
          />
          <Chips label="Impact" options={IMPACTS.map((v) => ({ value: v, label: humanize(v) }))} value={impact} onChange={setImpact} hint={IMPACT_HELP[impact]} />
          <Chips label="Urgency" options={URGENCIES.map((v) => ({ value: v, label: humanize(v) }))} value={urgency} onChange={setUrgency} />
          <Input label="Users affected" value={affectedUsers} onChangeText={setAffectedUsers} keyboardType="number-pad" error={errors.affectedUsers} />

          {serviceId && preview.data && (
            <Card style={{ gap: 6 }}>
              <Row gap={8}>
                <Ionicons name="flash" size={18} color={hue.orange} />
                <T style={{ fontWeight: '600' }}>Calculated priority</T>
                <PriorityBadge priority={preview.data.level} />
                <Badge label={preview.data.label} color={c.muted} variant="outline" />
              </Row>
              <T v="small" muted>
                From impact, urgency, users affected and the service. Support may adjust it.
              </T>
            </Card>
          )}

          <Button label="Submit incident" icon="send" onPress={submit} loading={report.isPending} />
          </>
          )}
          <View style={{ height: 24 }} />
        </ScrollView>
      </KeyboardAvoidingView>
    </>
  );
}
