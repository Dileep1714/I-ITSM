import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Stack, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Image, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, Switch, View } from 'react-native';
import { api } from '@/api/client';
import type { IncidentDetail, IncidentOperation, ResolutionCode, Role } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { errorMessage, formatDateTime, fromNow, humanize } from '@/lib/format';
import { buzz } from '@/lib/haptics';
import { pickPhoto } from '@/lib/photos';
import { useActionMenu, type MenuItem } from '@/ui/actionMenu';
import {
  Avatar,
  Badge,
  Banner,
  Button,
  Card,
  Chips,
  Empty,
  Input,
  ListRow,
  Loading,
  PriorityDot,
  ProgressBar,
  Row,
  Section,
  Segmented,
  Sheet,
  SlaBadge,
  Stars,
  StatusBadge,
  T,
  type IconName,
} from '@/ui/kit';
import { AiFixCard, SlaForecastCard } from '@/ui/IncidentInsights';
import { StampStrip } from '@/ui/StampStrip';
import { hue, useColors } from '@/ui/theme';
import { useToast } from '@/ui/toast';

type ActionId = IncidentOperation | 'TAKE_OR_ASSIGN_ME';
interface ActionDef {
  label: string;
  icon: IconName;
  color?: string;
  destructive?: boolean;
}

const ACTION: Partial<Record<ActionId, ActionDef>> = {
  TAKE_OR_ASSIGN_ME: { label: 'Take it', icon: 'person-add-outline' },
  ACKNOWLEDGE: { label: 'Acknowledge', icon: 'hand-left-outline' },
  START_WORK: { label: 'Start work', icon: 'play-outline' },
  RESOLVE: { label: 'Resolve', icon: 'construct-outline', color: hue.teal },
  ACCEPT_RESOLUTION: { label: 'Confirm it is fixed', icon: 'thumbs-up-outline', color: hue.green },
  REJECT_RESOLUTION: { label: 'Still broken', icon: 'thumbs-down-outline', destructive: true },
  PROVIDE_INFO: { label: 'Answer the question', icon: 'chatbubble-outline' },
  REQUEST_INFO: { label: 'Ask the reporter', icon: 'help-circle-outline' },
  ASSIGN: { label: 'Assign technician', icon: 'people-outline' },
  CLASSIFY: { label: 'Classify', icon: 'pricetag-outline' },
  CLOSE: { label: 'Close incident', icon: 'lock-closed-outline' },
  CANCEL: { label: 'Cancel incident', icon: 'close-circle-outline', destructive: true },
};

/** Which action is the big button, by role: the next step in that person's workflow. */
const PRIMARY_ORDER: Record<Role, ActionId[]> = {
  EMPLOYEE: ['PROVIDE_INFO', 'ACCEPT_RESOLUTION'],
  TECHNICIAN: ['TAKE_OR_ASSIGN_ME', 'ACKNOWLEDGE', 'START_WORK', 'RESOLVE'],
  ADMINISTRATOR: ['ASSIGN', 'CLASSIFY', 'CLOSE'],
};

const DIRECT: Partial<Record<IncidentOperation, { call: (id: number) => Promise<IncidentDetail>; confirm: string; done: string }>> = {
  ACKNOWLEDGE: { call: api.incidents.acknowledge, confirm: 'This records your response time.', done: 'Acknowledged' },
  START_WORK: { call: api.incidents.start, confirm: 'Start working on this incident?', done: 'Work started' },
  CLOSE: { call: api.incidents.close, confirm: 'It cannot be reopened.', done: 'Incident closed' },
};

const TEXT_ACTIONS = {
  REQUEST_INFO: { title: 'Ask the reporter', label: 'Question', button: 'Send question', done: 'Question sent', call: api.incidents.requestInfo },
  PROVIDE_INFO: { title: 'Answer the technician', label: 'Your answer', button: 'Send answer', done: 'Answer sent', call: api.incidents.provideInfo },
  REJECT_RESOLUTION: { title: 'What is still wrong?', label: 'Describe the problem', button: 'Reject and reopen', done: 'Resolution rejected', call: api.incidents.reject },
  CANCEL: { title: 'Cancel this incident', label: 'Reason', button: 'Cancel incident', done: 'Incident cancelled', call: api.incidents.cancel },
} as const;
type TextOp = keyof typeof TEXT_ACTIONS;
type SheetOp = TextOp | 'RESOLVE' | 'ACCEPT_RESOLUTION' | 'CLASSIFY' | 'ASSIGN';

const RESOLUTION_CODES: ResolutionCode[] = ['PERMANENT_FIX', 'WORKAROUND', 'CONFIGURATION_CHANGE', 'HARDWARE_REPLACED', 'USER_EDUCATION', 'THIRD_PARTY_FIX', 'NOT_REPRODUCIBLE'];

export default function IncidentScreen() {
  const id = Number(useLocalSearchParams<{ id: string }>().id);
  const c = useColors();
  const toast = useToast();
  const menu = useActionMenu();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const incident = useQuery({ queryKey: ['incidents', id], queryFn: () => api.incidents.get(id) });
  const history = useQuery({ queryKey: ['incidents', id, 'history'], queryFn: () => api.incidents.history(id) });
  const attachments = useQuery({ queryKey: ['incidents', id, 'attachments'], queryFn: () => api.attachments.list(id) });
  const [sheet, setSheet] = useState<SheetOp | null>(null);
  const [tab, setTab] = useState<'notes' | 'questions' | 'history'>('notes');
  const [viewing, setViewing] = useState<number | null>(null);

  const applyUpdate = (updated: IncidentDetail) => {
    queryClient.setQueryData(['incidents', id], updated);
    queryClient.invalidateQueries({ queryKey: ['incidents'] });
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };
  const run = async (call: () => Promise<IncidentDetail>, done: string) => {
    applyUpdate(await call());
    buzz('success');
    toast({ tone: 'success', title: done });
  };
  const runOrToast = (call: () => Promise<IncidentDetail>, done: string) =>
    run(call, done).catch((e) => toast({ tone: 'error', title: 'Not possible', message: errorMessage(e) }));

  const upload = useMutation({
    mutationFn: (photo: Parameters<typeof api.attachments.upload>[1]) => api.attachments.upload(id, photo),
    onSuccess: () => {
      buzz('success');
      queryClient.invalidateQueries({ queryKey: ['incidents', id, 'attachments'] });
      toast({ tone: 'success', title: 'Photo attached' });
    },
    onError: (e) => toast({ tone: 'error', title: 'Upload failed', message: errorMessage(e) }),
  });

  const i = incident.data;
  if (!i || !user) {
    return (
      <View style={{ flex: 1, padding: 16 }}>
        {incident.error ? <Banner tone="danger" title="Could not load the incident">{errorMessage(incident.error)}</Banner> : <Loading />}
      </View>
    );
  }

  // ----- which actions this user has here (the server's allowedOperations decides) -----
  const canTakeOrAssignMe =
    user.role === 'TECHNICIAN' && i.assignee?.id !== user.id && (i.allowedOperations.includes('TAKE') || i.allowedOperations.includes('ASSIGN'));
  const available: ActionId[] = [
    ...(canTakeOrAssignMe ? (['TAKE_OR_ASSIGN_ME'] as ActionId[]) : []),
    ...(Object.keys(ACTION) as ActionId[]).filter((op) => op !== 'TAKE_OR_ASSIGN_ME' && i.allowedOperations.includes(op as IncidentOperation)),
  ];
  const primary = PRIMARY_ORDER[user.role].find((op) => available.includes(op));
  const secondary = available.filter((op) => op !== primary);

  const perform = (op: ActionId) => {
    if (op === 'TAKE_OR_ASSIGN_ME') {
      return void runOrToast(() => (i.allowedOperations.includes('TAKE') ? api.incidents.take(id) : api.incidents.assign(id, user.id)), 'Assigned to you');
    }
    const direct = DIRECT[op];
    if (direct) {
      return Alert.alert(ACTION[op]?.label ?? humanize(op), direct.confirm, [
        { text: 'Back', style: 'cancel' },
        { text: 'Confirm', onPress: () => void runOrToast(() => direct.call(id), direct.done) },
      ]);
    }
    setSheet(op as SheetOp);
  };

  const closed = i.status === 'CLOSED' || i.status === 'CANCELLED';
  const addPhoto = () =>
    menu('Attach a photo', [
      { label: 'Take photo', icon: 'camera-outline', onPress: () => void pickPhoto('camera').then((p) => p && upload.mutate(p)) },
      { label: 'Choose from gallery', icon: 'images-outline', onPress: () => void pickPhoto('library').then((p) => p && upload.mutate(p)) },
    ]);

  const moreItems: MenuItem[] = [
    ...secondary.map((op) => ({ label: ACTION[op]!.label, icon: ACTION[op]!.icon, destructive: ACTION[op]!.destructive, onPress: () => perform(op) })),
    ...(!closed ? [{ label: 'Attach a photo', icon: 'camera-outline' as IconName, onPress: addPhoto }] : []),
  ];

  const pendingQuestion = i.infoRequests.find((r) => !r.answer);
  const sla = i.sla;
  const slaPercent = Math.min(100, Math.round(sla?.resolutionElapsedPercent ?? 0));

  return (
    <>
      <Stack.Screen
        options={{
          title: i.number,
          headerRight: () =>
            moreItems.length ? (
              <Pressable onPress={() => menu('More actions', moreItems)} hitSlop={12} accessibilityLabel="More actions">
                <Ionicons name="ellipsis-vertical" size={22} color={c.text} />
              </Pressable>
            ) : null,
        }}
      />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={{ padding: 16, gap: 16 }}
        refreshControl={
          <RefreshControl
            refreshing={incident.isRefetching}
            onRefresh={() => {
              void incident.refetch();
              void history.refetch();
              void attachments.refetch();
            }}
            tintColor={c.primary}
            colors={[c.primary]}
          />
        }
      >
        {/* Hero */}
        <View style={{ gap: 10 }}>
          <Row gap={12} style={{ alignItems: 'flex-start' }}>
            <PriorityDot priority={i.priority?.level} />
            <View style={{ flex: 1, gap: 4 }}>
              <T v="mono" color={c.primary} style={{ fontSize: 17 }}>
                {i.number}
              </T>
              <T v="title">{i.title}</T>
              <T v="small" muted>
                {i.reporter.fullName} · {fromNow(i.createdAt)}
              </T>
            </View>
          </Row>
          <Row wrap gap={6}>
            <StatusBadge status={i.status} />
            {sla && <SlaBadge status={sla.status} />}
            {i.priority && <Badge label={i.priority.label} color={c.muted} variant="outline" />}
          </Row>
          <StampStrip status={i.status} createdAt={i.createdAt} history={history.data ?? []} />
        </View>

        {primary && (
          <Button
            label={primary === 'TAKE_OR_ASSIGN_ME' && i.allowedOperations.includes('ASSIGN') ? 'Assign to me' : ACTION[primary]!.label}
            icon={ACTION[primary]!.icon}
            color={ACTION[primary]!.color}
            onPress={() => perform(primary)}
            style={{ height: 52 }}
          />
        )}

        {i.duplicateOfNumber && <Banner title="Duplicate">{`Closed as a duplicate of ${i.duplicateOfNumber}. Follow that incident for updates.`}</Banner>}
        {pendingQuestion && <Banner title={`${pendingQuestion.askedBy.fullName} needs more information`}>{pendingQuestion.question}</Banner>}
        {i.cancellationReason && !i.duplicateOfNumber && <Banner tone="neutral" title="Cancelled">{i.cancellationReason}</Banner>}

        <Card style={{ gap: 6 }}>
          <T v="label" color={c.primary}>
            DESCRIPTION
          </T>
          <T>{i.description}</T>
        </Card>

        {user.role !== 'EMPLOYEE' && <SlaForecastCard incidentId={i.id} />}
        {user.role !== 'EMPLOYEE' && !['RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED'].includes(i.status) && <AiFixCard incidentId={i.id} />}

        {i.resolution && (
          <Card style={{ gap: 8, borderWidth: 1.5, borderColor: c.primary }}>
            <Row style={{ justifyContent: 'space-between' }}>
              <T v="label" color={hue.teal}>
                RESOLUTION
              </T>
              <Badge label={humanize(i.resolution.code)} color={hue.teal} />
            </Row>
            <T>{i.resolution.summary}</T>
            {i.resolution.rootCause && (
              <T v="small">
                <T v="small" style={{ fontWeight: '700' }}>
                  Root cause:{' '}
                </T>
                {i.resolution.rootCause}
              </T>
            )}
            <T v="small" muted>
              {i.resolution.resolvedBy.fullName} · {formatDateTime(i.resolution.resolvedAt)}
            </T>
            {i.feedback && (
              <Row wrap gap={8}>
                <Badge label={i.feedback.accepted ? 'Accepted' : 'Rejected'} color={i.feedback.accepted ? hue.green : hue.red} variant="filled" />
                {i.feedback.rating ? <Stars value={i.feedback.rating} size={16} /> : null}
                {i.feedback.comment && (
                  <T v="small" muted>
                    “{i.feedback.comment}”
                  </T>
                )}
              </Row>
            )}
          </Card>
        )}

        {/* Photos */}
        {((attachments.data?.length ?? 0) > 0 || !closed) && (
          <View style={{ gap: 8 }}>
            <T v="label" color={c.primary} style={{ paddingHorizontal: 4 }}>
              PHOTOS
            </T>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 10 }}>
              {attachments.data?.map((a) => (
                <Pressable key={a.id} onPress={() => setViewing(a.id)} accessibilityLabel={`Photo ${a.fileName} by ${a.uploadedBy.fullName}`}>
                  <Image source={api.attachments.source(a.id)} style={[styles.thumb, { backgroundColor: c.surfaceAlt }]} />
                </Pressable>
              ))}
              {!closed && (
                <Pressable onPress={addPhoto} style={[styles.thumb, styles.addThumb, { borderColor: c.border }]} accessibilityLabel="Attach a photo">
                  {upload.isPending ? <Loading /> : <Ionicons name="camera-outline" size={26} color={c.primary} />}
                </Pressable>
              )}
            </ScrollView>
          </View>
        )}

        <Section title="DETAILS">
          <ListRow icon="server-outline" title="Service" subtitle={i.service.name} />
          <ListRow icon="pricetag-outline" title="Category" subtitle={i.category?.name ?? 'Not classified'} />
          <ListRow
            leading={i.assignee ? <Avatar name={i.assignee.fullName} size={36} /> : undefined}
            icon={i.assignee ? undefined : 'person-outline'}
            title="Assignee"
            subtitle={i.assignee?.fullName ?? 'Unassigned'}
          />
          <ListRow icon="pulse-outline" title="Impact · urgency" subtitle={`${humanize(i.impact)} · ${humanize(i.urgency)} · ${i.affectedUsers} user(s)`} />
          {i.priority?.rationale && <ListRow icon="calculator-outline" title={`Priority score ${i.priority.score?.toFixed(1)}`} subtitle={i.priority.rationale} />}
        </Section>

        {sla && (
          <Section title="SERVICE LEVEL">
            <ListRow icon="flash-outline" title="Response due" subtitle={formatDateTime(sla.responseDue)} trailing={sla.respondedAt ? <Badge label={sla.responseBreached ? 'Late' : 'Met'} color={sla.responseBreached ? hue.red : hue.green} /> : undefined} />
            <ListRow icon="hourglass-outline" title="Resolution due" subtitle={formatDateTime(sla.resolutionDue)} trailing={sla.resolutionBreached ? <Badge label="Breached" color={hue.red} /> : undefined} />
            {!sla.resolvedAt && sla.resolutionDue && (
              <View style={{ padding: 16, gap: 6 }}>
                <T v="small" muted>
                  {slaPercent}% of the time used · due {fromNow(sla.resolutionDue)}
                </T>
                <ProgressBar value={slaPercent} color={slaPercent >= 100 ? hue.red : slaPercent >= 75 ? hue.orange : hue.teal} />
              </View>
            )}
          </Section>
        )}

        <Segmented
          value={tab}
          onChange={setTab}
          options={[
            { value: 'notes', label: `Notes ${i.notes.length}` },
            { value: 'questions', label: `Q&A ${i.infoRequests.length}` },
            { value: 'history', label: 'History' },
          ]}
        />
        {tab === 'notes' && <Notes incident={i} isStaff={user.role !== 'EMPLOYEE'} onUpdated={applyUpdate} />}
        {tab === 'questions' &&
          (i.infoRequests.length === 0 ? (
            <Empty icon="help-circle-outline" text="No questions asked." />
          ) : (
            i.infoRequests.map((r) => (
              <Card key={r.id} style={{ gap: 4 }}>
                <T v="small" muted>
                  {r.askedBy.fullName} · {formatDateTime(r.askedAt)}
                </T>
                <T style={{ fontWeight: '600' }}>{r.question}</T>
                {r.answer ? <T v="small">↳ {r.answer}</T> : <Badge label="Awaiting answer" color={hue.yellow} />}
              </Card>
            ))
          ))}
        {tab === 'history' &&
          (history.data ?? []).map((h) => (
            <Row key={h.id} gap={12} style={{ alignItems: 'flex-start' }}>
              <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: c.primary, marginTop: 6 }} />
              <View style={{ flex: 1 }}>
                <T v="small" style={{ fontWeight: '600' }}>
                  {humanize(h.action)}
                </T>
                <T v="small">{h.description}</T>
                <T v="small" muted>
                  {h.actorName ?? 'System'} · {formatDateTime(h.occurredAt)}
                </T>
              </View>
            </Row>
          ))}
        <View style={{ height: 32 }} />
      </ScrollView>

      {/* Full-screen photo viewer */}
      <Modal visible={viewing !== null} transparent animationType="fade" onRequestClose={() => setViewing(null)} statusBarTranslucent>
        <Pressable style={{ flex: 1, backgroundColor: '#000', justifyContent: 'center' }} onPress={() => setViewing(null)}>
          {viewing !== null && <Image source={api.attachments.source(viewing)} style={{ width: '100%', height: '80%' }} resizeMode="contain" />}
          <View style={{ position: 'absolute', top: 48, right: 20 }}>
            <Ionicons name="close" size={28} color="#fff" />
          </View>
        </Pressable>
      </Modal>

      <ActionSheets incident={i} op={sheet} onClose={() => setSheet(null)} run={run} />
    </>
  );
}

function Notes({ incident, isStaff, onUpdated }: { incident: IncidentDetail; isStaff: boolean; onUpdated: (i: IncidentDetail) => void }) {
  const c = useColors();
  const [text, setText] = useState('');
  const [internal, setInternal] = useState(isStaff);
  const add = useMutation({
    mutationFn: () => api.incidents.addNote(incident.id, text.trim(), isStaff && internal),
    onSuccess: (updated) => {
      setText('');
      onUpdated(updated);
    },
  });
  const closed = incident.status === 'CLOSED' || incident.status === 'CANCELLED';

  return (
    <View style={{ gap: 10 }}>
      {incident.notes.length === 0 && <Empty icon="chatbubbles-outline" text="No notes yet." />}
      {incident.notes.map((n) => (
        <Row key={n.id} gap={10} style={{ alignItems: 'flex-start' }}>
          <Avatar name={n.author.fullName} size={32} />
          <View style={[styles.bubble, { backgroundColor: n.internal ? c.internalBg : c.surface }]}>
            <Row gap={6}>
              <T v="small" style={{ fontWeight: '600' }}>
                {n.author.fullName}
              </T>
              {n.internal && <Ionicons name="lock-closed" size={12} color={hue.yellow} />}
              <T v="small" muted>
                {fromNow(n.createdAt)}
              </T>
            </Row>
            <T v="small">{n.text}</T>
          </View>
        </Row>
      ))}
      {!closed && (
        <View style={{ gap: 8 }}>
          {add.error && <Banner tone="danger" title={errorMessage(add.error)} />}
          <Input value={text} onChangeText={setText} placeholder={isStaff ? 'Investigation note or comment…' : 'Add a comment…'} multiline />
          <Row style={{ justifyContent: 'space-between' }}>
            {isStaff ? (
              <Row gap={6}>
                <Switch value={internal} onValueChange={setInternal} />
                <T v="small">Internal only</T>
              </Row>
            ) : (
              <View />
            )}
            <Button label="Post" icon="send" variant="tonal" onPress={() => add.mutate()} disabled={!text.trim()} loading={add.isPending} />
          </Row>
        </View>
      )}
    </View>
  );
}

function ActionSheets({
  incident,
  op,
  onClose,
  run,
}: {
  incident: IncidentDetail;
  op: SheetOp | null;
  onClose: () => void;
  run: (call: () => Promise<IncidentDetail>, done: string) => Promise<void>;
}) {
  const c = useColors();
  const id = incident.id;
  const [text, setText] = useState('');
  const [summary, setSummary] = useState('');
  const [rootCause, setRootCause] = useState('');
  const [code, setCode] = useState<ResolutionCode>('PERMANENT_FIX');
  const [rating, setRating] = useState(5);
  const [categoryId, setCategoryId] = useState<number | null>(incident.category?.id ?? null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const categories = useQuery({ queryKey: ['categories'], queryFn: api.catalog.categories, enabled: op === 'CLASSIFY' });
  const recommendations = useQuery({ queryKey: ['recommendations', id], queryFn: () => api.incidents.recommendations(id), enabled: op === 'ASSIGN' });

  const close = () => {
    setText('');
    setSummary('');
    setRootCause('');
    setError(null);
    onClose();
  };
  const submit = async (call: () => Promise<IncidentDetail>, done: string) => {
    setError(null);
    setBusy(true);
    try {
      await run(call, done);
      close();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  const errorBanner = error && <Banner tone="danger" title={error} />;

  if (op && op in TEXT_ACTIONS) {
    const cfg = TEXT_ACTIONS[op as TextOp];
    const pendingQuestion = incident.infoRequests.find((r) => !r.answer);
    const danger = op === 'CANCEL' || op === 'REJECT_RESOLUTION';
    return (
      <Sheet visible title={cfg.title} onClose={close}>
        {errorBanner}
        {op === 'PROVIDE_INFO' && pendingQuestion && (
          <Card style={{ backgroundColor: c.surfaceAlt }}>
            <T v="small" muted>
              {pendingQuestion.askedBy.fullName} asked:
            </T>
            <T>{pendingQuestion.question}</T>
          </Card>
        )}
        <Input label={cfg.label} value={text} onChangeText={setText} multiline autoFocus />
        <Button label={cfg.button} color={danger ? hue.red : undefined} disabled={!text.trim()} loading={busy} onPress={() => submit(() => cfg.call(id, text.trim()), cfg.done)} />
      </Sheet>
    );
  }

  return (
    <>
      <Sheet visible={op === 'RESOLVE'} title="Resolve incident" onClose={close}>
        {errorBanner}
        <Input label="What fixed it?" value={summary} onChangeText={setSummary} multiline />
        <Input label="Root cause (optional)" value={rootCause} onChangeText={setRootCause} />
        <Chips label="Resolution type" options={RESOLUTION_CODES.map((x) => ({ value: x, label: humanize(x) }))} value={code} onChange={setCode} />
        <Button
          label="Resolve"
          icon="checkmark"
          color={hue.teal}
          disabled={!summary.trim()}
          loading={busy}
          onPress={() => submit(() => api.incidents.resolve(id, { summary: summary.trim(), rootCause: rootCause.trim() || undefined, code }), 'Incident resolved')}
        />
      </Sheet>

      <Sheet visible={op === 'ACCEPT_RESOLUTION'} title="How did we do?" onClose={close}>
        {errorBanner}
        <T muted>Rate how this incident was handled.</T>
        <View style={{ alignItems: 'center' }}>
          <Stars value={rating} onChange={setRating} size={40} />
        </View>
        <Input label="Comment (optional)" value={text} onChangeText={setText} multiline />
        <Button label="Confirm it is fixed" color={hue.green} loading={busy} onPress={() => submit(() => api.incidents.accept(id, rating, text.trim() || undefined), 'Thanks for your feedback')} />
      </Sheet>

      <Sheet visible={op === 'CLASSIFY'} title="Classify incident" onClose={close}>
        {errorBanner}
        <T v="small" muted>
          The category sets priority and SLA deadlines, and may assign a technician automatically.
        </T>
        {categories.isPending ? (
          <Loading />
        ) : (
          <Chips options={(categories.data ?? []).filter((x) => x.active).map((x) => ({ value: x.id, label: x.name }))} value={categoryId} onChange={setCategoryId} />
        )}
        <Button label="Classify" disabled={!categoryId} loading={busy} onPress={() => submit(() => api.incidents.classify(id, categoryId!), 'Incident classified')} />
      </Sheet>

      {/* Intelligent assignment: technicians ranked by skill, workload and level, with the reasons. */}
      <Sheet visible={op === 'ASSIGN'} title="Assign a technician" onClose={close}>
        {errorBanner}
        {recommendations.isPending && <Loading />}
        {recommendations.data?.length === 0 && <Empty text="No available technicians." />}
        {recommendations.data?.map((r, index) => {
          const load = r.maxActiveIncidents > 0 ? (r.activeIncidents / r.maxActiveIncidents) * 100 : 0;
          const current = incident.assignee?.id === r.technicianId;
          return (
            <Card key={r.technicianId} style={{ gap: 8, backgroundColor: index === 0 ? c.primaryContainer : c.surfaceAlt }}>
              <Row gap={10}>
                <Avatar name={r.fullName} size={36} />
                <View style={{ flex: 1 }}>
                  <T style={{ fontWeight: '600' }}>{r.fullName}</T>
                  <T v="small" muted>
                    score {r.score.toFixed(2)} · {r.activeIncidents}/{r.maxActiveIncidents} active
                  </T>
                </View>
                {index === 0 && <Badge label="Best match" color={hue.green} variant="filled" />}
                {current && <Badge label="Current" color={c.muted} variant="outline" />}
              </Row>
              <ProgressBar value={load} color={load >= 100 ? hue.red : load >= 75 ? hue.orange : hue.teal} />
              {r.reasons.map((reason) => (
                <T key={reason} v="small">
                  • {reason}
                </T>
              ))}
              <Button
                label={current ? 'Currently assigned' : `Assign ${r.fullName.split(' ')[0]}`}
                variant={index === 0 ? 'filled' : 'tonal'}
                disabled={current}
                loading={busy}
                onPress={() => submit(() => api.incidents.assign(id, r.technicianId), `Assigned to ${r.fullName}`)}
              />
            </Card>
          );
        })}
      </Sheet>
    </>
  );
}

const styles = StyleSheet.create({
  thumb: { width: 96, height: 96, borderRadius: 14 },
  addThumb: { borderWidth: 1.5, borderStyle: 'dashed', alignItems: 'center', justifyContent: 'center' },
  bubble: { flex: 1, borderRadius: 14, borderTopLeftRadius: 4, padding: 12, gap: 4 },
});
