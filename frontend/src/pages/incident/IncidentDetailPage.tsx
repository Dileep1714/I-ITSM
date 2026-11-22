import {
  Alert,
  Anchor,
  Badge,
  Box,
  Button,
  Card,
  Checkbox,
  Grid,
  Group,
  Paper,
  Progress,
  Rating,
  SimpleGrid,
  Stack,
  Tabs,
  Text,
  Textarea,
  Timeline,
  Title,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import {
  IconArrowLeft,
  IconCheck,
  IconCircleX,
  IconHandStop,
  IconLock,
  IconMessageQuestion,
  IconPlayerPlay,
  IconTag,
  IconThumbDown,
  IconThumbUp,
  IconTool,
  IconUserCheck,
  IconUserPlus,
  IconX,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { AuditEntryView, IncidentDetail, IncidentOperation } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { PriorityBadge, SlaBadge, StatusBadge } from '../../components/Badges';
import { QueryState } from '../../components/QueryState';
import { errorMessage, formatDateTime, fromNow, humanize } from '../../lib/format';
import { ActionModals, type ModalOperation } from './ActionModals';
import { AttachmentsCard } from './AttachmentsCard';
import { RelatedIncidents } from './RelatedIncidents';
import { SlaForecastCard } from '../../components/SlaForecast';
import { AiFixSuggestion } from '../../components/AiCopilot';
import { StampStrip } from './StampStrip';

interface ActionButton {
  op: IncidentOperation;
  label: string;
  icon: ReactNode;
  color?: string;
  variant?: string;
}

const ACTIONS: ActionButton[] = [
  { op: 'CLASSIFY', label: 'Classify', icon: <IconTag size={16} /> },
  { op: 'ASSIGN', label: 'Assign', icon: <IconUserPlus size={16} /> },
  { op: 'ACKNOWLEDGE', label: 'Acknowledge', icon: <IconHandStop size={16} /> },
  { op: 'START_WORK', label: 'Start work', icon: <IconPlayerPlay size={16} /> },
  { op: 'REQUEST_INFO', label: 'Request info', icon: <IconMessageQuestion size={16} />, variant: 'light' },
  { op: 'PROVIDE_INFO', label: 'Provide info', icon: <IconMessageQuestion size={16} /> },
  { op: 'RESOLVE', label: 'Resolve', icon: <IconTool size={16} /> },
  { op: 'ACCEPT_RESOLUTION', label: 'Accept fix', icon: <IconThumbUp size={16} /> },
  { op: 'REJECT_RESOLUTION', label: 'Still broken', icon: <IconThumbDown size={16} />, color: 'stamp', variant: 'light' },
  { op: 'CLOSE', label: 'Close', icon: <IconLock size={16} />, variant: 'outline' },
  { op: 'CANCEL', label: 'Cancel incident', icon: <IconCircleX size={16} />, color: 'stamp', variant: 'subtle' },
];

const DIRECT: Partial<Record<IncidentOperation, { call: (id: number) => Promise<IncidentDetail>; confirm: string; done: string }>> = {
  ACKNOWLEDGE: { call: api.incidents.acknowledge, confirm: 'Acknowledge this incident? This records your response time.', done: 'Acknowledged' },
  START_WORK: { call: api.incidents.start, confirm: 'Start working on this incident?', done: 'Work started' },
  CLOSE: { call: api.incidents.close, confirm: 'Close this incident? It cannot be reopened.', done: 'Incident closed' },
};

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ borderTop: '1px solid var(--app-rule-faint)', paddingTop: 8 }}>
      <Text
        size="xs"
        c="var(--app-ink-carbon)"
        tt="uppercase"
        fw={600}
        ff="var(--label-font)"
        style={{ letterSpacing: '0.12em' }}
      >
        {label}
      </Text>
      <Text size="sm" fw={500} component="div" mt={2}>
        {children}
      </Text>
    </div>
  );
}

export function IncidentDetailPage() {
  const id = Number(useParams().id);
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const incidentQuery = useQuery({ queryKey: ['incidents', id], queryFn: () => api.incidents.get(id) });
  const history = useQuery({ queryKey: ['incidents', id, 'history'], queryFn: () => api.incidents.history(id) });
  const [modal, setModal] = useState<ModalOperation | null>(null);

  const applyUpdate = (updated: IncidentDetail) => {
    queryClient.setQueryData(['incidents', id], updated);
    queryClient.invalidateQueries({ queryKey: ['incidents'] });
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };

  const run = async (call: () => Promise<IncidentDetail>, message: string) => {
    const updated = await call();
    applyUpdate(updated);
    notifications.show({ color: 'green', icon: <IconCheck size={16} />, message });
  };

  const onAction = (op: IncidentOperation) => {
    const direct = DIRECT[op];
    if (!direct) {
      setModal(op as ModalOperation);
      return;
    }
    modals.openConfirmModal({
      title: humanize(op),
      children: <Text size="sm">{direct.confirm}</Text>,
      labels: { confirm: 'Confirm', cancel: 'Back' },
      onConfirm: () =>
        run(() => direct.call(id), direct.done).catch((e) =>
          notifications.show({ color: 'red', icon: <IconX size={16} />, title: 'Not possible', message: errorMessage(e) }),
        ),
    });
  };

  /**
   * The logged-in technician takes the incident: "take" for an unassigned one (any level), otherwise an
   * assignment to themselves (team lead and above, e.g. taking over from a colleague).
   */
  const assignToMe = () => {
    if (!user || !incidentQuery.data) return;
    const call = incidentQuery.data.allowedOperations.includes('TAKE')
      ? () => api.incidents.take(id)
      : () => api.incidents.assign(id, user.id);
    run(call, 'Assigned to you').catch((e) =>
      notifications.show({ color: 'red', icon: <IconX size={16} />, title: 'Not possible', message: errorMessage(e) }),
    );
  };

  return (
    <Stack>
      <Anchor component={Link} to="/incidents" size="sm">
        <Group gap={4}>
          <IconArrowLeft size={14} /> Back to incidents
        </Group>
      </Anchor>
      <QueryState query={incidentQuery}>
        {incidentQuery.data && (
          <IncidentView
            incident={incidentQuery.data}
            isStaff={user?.role !== 'EMPLOYEE'}
            onAction={onAction}
            onAssignToMe={
              // Only technicians can own incidents (an administrator is not one); hide it if it is already mine.
              user?.role === 'TECHNICIAN' &&
              incidentQuery.data.assignee?.id !== user.id &&
              (incidentQuery.data.allowedOperations.includes('TAKE') || incidentQuery.data.allowedOperations.includes('ASSIGN'))
                ? assignToMe
                : undefined
            }
            history={history.data ?? []}
            onUpdated={applyUpdate}
          />
        )}
        {incidentQuery.data && (
          <ActionModals incident={incidentQuery.data} operation={modal} onClose={() => setModal(null)} run={run} />
        )}
      </QueryState>
    </Stack>
  );
}

function IncidentView({
  incident: i,
  isStaff,
  onAction,
  onAssignToMe,
  history,
  onUpdated,
}: {
  incident: IncidentDetail;
  isStaff: boolean;
  onAction: (op: IncidentOperation) => void;
  onAssignToMe?: () => void;
  history: AuditEntryView[];
  onUpdated: (i: IncidentDetail) => void;
}) {
  const actions = ACTIONS.filter((a) => i.allowedOperations.includes(a.op));
  const pendingQuestion = i.infoRequests.find((r) => !r.answer);
  const sla = i.sla;
  const slaPercent = Math.min(100, Math.round(sla?.resolutionElapsedPercent ?? 0));

  return (
    <>
      <Paper withBorder p="lg" radius="md">
        <Group justify="space-between" align="flex-start">
          <div>
            <Group gap="xs" mb={4}>
              <Text ff="monospace" fw={700} fz={22} c="var(--app-ink-carbon)" lh={1.2}>
                {i.number}
              </Text>
              <PriorityBadge priority={i.priority?.level} />
              <StatusBadge status={i.status} />
              {sla && <SlaBadge status={sla.status} />}
            </Group>
            <Title order={2}>{i.title}</Title>
            <Text size="sm" c="dimmed">
              Reported by {i.reporter.fullName} {fromNow(i.createdAt)}
            </Text>
          </div>
        </Group>
        {(actions.length > 0 || onAssignToMe) && (
          <Group mt="md" gap="xs">
            {onAssignToMe && !i.allowedOperations.includes('ASSIGN') && (
              <Button leftSection={<IconUserCheck size={16} />} size="sm" onClick={onAssignToMe}>
                Take it
              </Button>
            )}
            {actions.map((a) => (
              <Fragment key={a.op}>
                <Button leftSection={a.icon} color={a.color} variant={a.variant ?? 'filled'} size="sm" onClick={() => onAction(a.op)}>
                  {a.label}
                </Button>
                {a.op === 'ASSIGN' && onAssignToMe && (
                  <Button leftSection={<IconUserCheck size={16} />} variant="light" size="sm" onClick={onAssignToMe}>
                    Assign to me
                  </Button>
                )}
              </Fragment>
            ))}
          </Group>
        )}
        <Box mt="lg">
          <StampStrip status={i.status} createdAt={i.createdAt} history={history} />
        </Box>
      </Paper>

      {i.duplicateOfNumber && (
        <Alert color="yellow" title="Duplicate">
          This incident was closed as a duplicate of {i.duplicateOfNumber}. Follow that incident for updates.
        </Alert>
      )}
      {pendingQuestion && (
        <Alert color="yellow" icon={<IconMessageQuestion size={16} />} title={`${pendingQuestion.askedBy.fullName} needs more information`}>
          {pendingQuestion.question}
        </Alert>
      )}
      {i.cancellationReason && !i.duplicateOfNumber && (
        <Alert color="gray" title="Cancelled">
          {i.cancellationReason}
        </Alert>
      )}

      <Grid>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Stack>
            <Card withBorder radius="md">
              <Text fw={600} mb="xs">
                Description
              </Text>
              <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
                {i.description}
              </Text>
            </Card>

            {i.resolution && (
              <Card withBorder radius="md" style={{ borderColor: 'var(--app-ink-carbon)', borderWidth: 2 }}>
                <Group justify="space-between" mb="xs">
                  <Text fw={600}>Resolution</Text>
                  <Badge color="carbon" variant="filled">
                    {humanize(i.resolution.code)}
                  </Badge>
                </Group>
                <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
                  {i.resolution.summary}
                </Text>
                {i.resolution.rootCause && (
                  <Text size="sm" mt="xs">
                    <b>Root cause:</b> {i.resolution.rootCause}
                  </Text>
                )}
                <Text size="xs" c="dimmed" mt="xs">
                  By {i.resolution.resolvedBy.fullName}, {formatDateTime(i.resolution.resolvedAt)}
                </Text>
                {i.feedback && (
                  <Group mt="sm" gap="xs">
                    <Badge color={i.feedback.accepted ? 'green' : 'red'}>{i.feedback.accepted ? 'Accepted' : 'Rejected'}</Badge>
                    {i.feedback.rating && <Rating value={i.feedback.rating} readOnly size="sm" />}
                    {i.feedback.comment && (
                      <Text size="sm" c="dimmed">
                        “{i.feedback.comment}”
                      </Text>
                    )}
                  </Group>
                )}
              </Card>
            )}

            <Card withBorder radius="md">
              <Tabs defaultValue="activity">
                <Tabs.List mb="md">
                  <Tabs.Tab value="activity">Notes ({i.notes.length})</Tabs.Tab>
                  <Tabs.Tab value="questions">Questions ({i.infoRequests.length})</Tabs.Tab>
                  <Tabs.Tab value="history">History ({history.length})</Tabs.Tab>
                </Tabs.List>
                <Tabs.Panel value="activity">
                  <NotesPanel incident={i} isStaff={isStaff} onUpdated={onUpdated} />
                </Tabs.Panel>
                <Tabs.Panel value="questions">
                  <Stack>
                    {i.infoRequests.length === 0 && <Text c="dimmed" size="sm">No questions asked.</Text>}
                    {i.infoRequests.map((r) => (
                      <Card key={r.id} withBorder p="sm">
                        <Text size="xs" c="dimmed">
                          {r.askedBy.fullName} · {formatDateTime(r.askedAt)}
                        </Text>
                        <Text size="sm" fw={500}>
                          {r.question}
                        </Text>
                        {r.answer ? (
                          <Text size="sm" mt={4}>
                            ↳ {r.answer}{' '}
                            <Text span size="xs" c="dimmed">
                              ({formatDateTime(r.answeredAt)})
                            </Text>
                          </Text>
                        ) : (
                          <Badge color="yellow" variant="light" mt={4}>
                            Awaiting answer
                          </Badge>
                        )}
                      </Card>
                    ))}
                  </Stack>
                </Tabs.Panel>
                <Tabs.Panel value="history">
                  <Timeline bulletSize={14} lineWidth={2}>
                    {history.map((h) => (
                      <Timeline.Item key={h.id} title={humanize(h.action)}>
                        <Text size="sm">{h.description}</Text>
                        <Text size="xs" c="dimmed">
                          {h.actorName ?? 'System'} · {formatDateTime(h.occurredAt)}
                          {h.previousStatus && h.newStatus && h.previousStatus !== h.newStatus &&
                            ` · ${humanize(h.previousStatus)} → ${humanize(h.newStatus)}`}
                        </Text>
                      </Timeline.Item>
                    ))}
                  </Timeline>
                </Tabs.Panel>
              </Tabs>
            </Card>
          </Stack>
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 4 }}>
          <Stack>
            <Card withBorder radius="md">
              <SimpleGrid cols={2} spacing="sm">
                <Field label="Service">{i.service.name}</Field>
                <Field label="Category">{i.category?.name ?? 'Not classified'}</Field>
                <Field label="Impact">{humanize(i.impact)}</Field>
                <Field label="Urgency">{humanize(i.urgency)}</Field>
                <Field label="Users affected">{i.affectedUsers}</Field>
                <Field label="Assignee">{i.assignee?.fullName ?? 'Unassigned'}</Field>
                <Field label="Created">{formatDateTime(i.createdAt)}</Field>
                <Field label="Updated">{formatDateTime(i.updatedAt)}</Field>
                {i.closedAt && <Field label="Closed">{formatDateTime(i.closedAt)}</Field>}
              </SimpleGrid>
            </Card>

            {isStaff && <SlaForecastCard incidentId={i.id} />}
            {isStaff && !['RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED'].includes(i.status) && <AiFixSuggestion incidentId={i.id} />}

            <AttachmentsCard incident={i} />

            {isStaff && <RelatedIncidents incident={i} onUpdated={onUpdated} />}

            {i.priority && (
              <Card withBorder radius="md">
                <Group gap="xs" mb="xs">
                  <Text fw={600}>Priority</Text>
                  <PriorityBadge priority={i.priority.level} />
                  <Text size="sm">{i.priority.label}</Text>
                </Group>
                {i.priority.rationale && (
                  <Text size="xs" c="dimmed">
                    Score {i.priority.score?.toFixed(1)}: {i.priority.rationale}
                  </Text>
                )}
              </Card>
            )}

            {sla && (
              <Card withBorder radius="md">
                <Group justify="space-between" mb="xs">
                  <Text fw={600}>SLA</Text>
                  <SlaBadge status={sla.status} />
                </Group>
                <Stack gap="xs">
                  <Field label="Response due">
                    {formatDateTime(sla.responseDue)}{' '}
                    {sla.respondedAt ? (
                      <Badge size="xs" color={sla.responseBreached ? 'red' : 'green'} variant="light">
                        {sla.responseBreached ? 'late' : 'met'}
                      </Badge>
                    ) : sla.responseBreached ? (
                      <Badge size="xs" color="red">breached</Badge>
                    ) : null}
                  </Field>
                  <Field label="Resolution due">
                    {formatDateTime(sla.resolutionDue)}{' '}
                    {sla.resolutionBreached && <Badge size="xs" color="red">breached</Badge>}
                  </Field>
                  {!sla.resolvedAt && sla.resolutionDue && (
                    <div>
                      <Group justify="space-between">
                        <Text size="xs" c="dimmed">
                          Time used
                        </Text>
                        <Text size="xs" c="dimmed">
                          {slaPercent}% · due {fromNow(sla.resolutionDue)}
                        </Text>
                      </Group>
                      <Progress value={slaPercent} color={slaPercent >= 75 ? 'stamp' : 'carbon'} radius={0} />
                    </div>
                  )}
                </Stack>
              </Card>
            )}
          </Stack>
        </Grid.Col>
      </Grid>
    </>
  );
}

function NotesPanel({ incident, isStaff, onUpdated }: { incident: IncidentDetail; isStaff: boolean; onUpdated: (i: IncidentDetail) => void }) {
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
    <Stack>
      {incident.notes.length === 0 && (
        <Text c="dimmed" size="sm">
          No notes yet.
        </Text>
      )}
      {incident.notes.map((n) => (
        <Card key={n.id} withBorder p="sm" bg={n.internal ? 'var(--mantine-color-yellow-light)' : undefined}>
          <Group gap="xs">
            <Text size="sm" fw={600}>
              {n.author.fullName}
            </Text>
            <Text size="xs" c="dimmed">
              {formatDateTime(n.createdAt)}
            </Text>
            {n.internal && (
              <Badge size="xs" color="yellow" leftSection={<IconLock size={10} />}>
                internal
              </Badge>
            )}
          </Group>
          <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
            {n.text}
          </Text>
        </Card>
      ))}
      {!closed && (
        <Stack gap="xs">
          {add.error && <Alert color="red">{errorMessage(add.error)}</Alert>}
          <Textarea
            placeholder={isStaff ? 'Investigation note or comment…' : 'Add a comment…'}
            autosize
            minRows={2}
            value={text}
            onChange={(e) => setText(e.currentTarget.value)}
          />
          <Group justify="space-between">
            {isStaff ? (
              <Checkbox label="Internal (hidden from the reporter)" checked={internal} onChange={(e) => setInternal(e.currentTarget.checked)} />
            ) : (
              <span />
            )}
            <Button size="xs" disabled={!text.trim()} loading={add.isPending} onClick={() => add.mutate()}>
              Add note
            </Button>
          </Group>
        </Stack>
      )}
    </Stack>
  );
}
