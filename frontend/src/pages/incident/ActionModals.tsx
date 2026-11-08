import { Alert, Badge, Button, Card, Group, Loader, Modal, Progress, Rating, Select, Stack, Text, Textarea } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import type { IncidentDetail, IncidentOperation, ResolutionCode } from '../../api/types';
import { errorMessage, humanize } from '../../lib/format';

const RESOLUTION_CODES: ResolutionCode[] = [
  'PERMANENT_FIX',
  'WORKAROUND',
  'CONFIGURATION_CHANGE',
  'HARDWARE_REPLACED',
  'USER_EDUCATION',
  'THIRD_PARTY_FIX',
  'NOT_REPRODUCIBLE',
];

/** Operations that open a modal; the others run immediately after a confirmation. */
export type ModalOperation = Extract<
  IncidentOperation,
  'CLASSIFY' | 'ASSIGN' | 'REQUEST_INFO' | 'PROVIDE_INFO' | 'RESOLVE' | 'ACCEPT_RESOLUTION' | 'REJECT_RESOLUTION' | 'CANCEL'
>;

interface Props {
  incident: IncidentDetail;
  operation: ModalOperation | null;
  onClose: () => void;
  /** Runs the API call; resolves when done, rejects with the API error. */
  run: (call: () => Promise<IncidentDetail>, successMessage: string) => Promise<void>;
}

const TEXT_ACTIONS = {
  REQUEST_INFO: { title: 'Ask the reporter for information', label: 'Question', button: 'Send question', done: 'Question sent' },
  PROVIDE_INFO: { title: 'Answer the technician', label: 'Your answer', button: 'Send answer', done: 'Answer sent' },
  REJECT_RESOLUTION: { title: 'Reject the resolution', label: 'What is still wrong?', button: 'Reject and reopen', done: 'Resolution rejected' },
  CANCEL: { title: 'Cancel this incident', label: 'Reason', button: 'Cancel incident', done: 'Incident cancelled' },
} as const;

export function ActionModals({ incident, operation, onClose, run }: Props) {
  const [text, setText] = useState('');
  const [categoryId, setCategoryId] = useState<string | null>(incident.category ? String(incident.category.id) : null);
  const [summary, setSummary] = useState('');
  const [rootCause, setRootCause] = useState('');
  const [code, setCode] = useState<ResolutionCode>('PERMANENT_FIX');
  const [rating, setRating] = useState(5);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: api.catalog.categories,
    enabled: operation === 'CLASSIFY',
  });
  const recommendations = useQuery({
    queryKey: ['recommendations', incident.id],
    queryFn: () => api.incidents.recommendations(incident.id),
    enabled: operation === 'ASSIGN',
  });

  const close = () => {
    setText('');
    setSummary('');
    setRootCause('');
    setError(null);
    onClose();
  };

  const submit = async (call: () => Promise<IncidentDetail>, message: string) => {
    setError(null);
    setBusy(true);
    try {
      await run(call, message);
      close();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const id = incident.id;
  const errorAlert = error && (
    <Alert color="red" style={{ whiteSpace: 'pre-line' }}>
      {error}
    </Alert>
  );

  if (operation && operation in TEXT_ACTIONS) {
    const cfg = TEXT_ACTIONS[operation as keyof typeof TEXT_ACTIONS];
    const call = () => {
      const t = text.trim();
      switch (operation) {
        case 'REQUEST_INFO':
          return api.incidents.requestInfo(id, t);
        case 'PROVIDE_INFO':
          return api.incidents.provideInfo(id, t);
        case 'REJECT_RESOLUTION':
          return api.incidents.reject(id, t);
        default:
          return api.incidents.cancel(id, t);
      }
    };
    const pendingQuestion = incident.infoRequests.find((r) => !r.answer);
    return (
      <Modal opened onClose={close} title={cfg.title}>
        <Stack>
          {errorAlert}
          {operation === 'PROVIDE_INFO' && pendingQuestion && (
            <Card withBorder bg="var(--app-canvas)">
              <Text size="xs" c="dimmed">
                {pendingQuestion.askedBy.fullName} asked:
              </Text>
              <Text size="sm">{pendingQuestion.question}</Text>
            </Card>
          )}
          <Textarea label={cfg.label} autosize minRows={3} value={text} onChange={(e) => setText(e.currentTarget.value)} data-autofocus />
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              Back
            </Button>
            <Button
              color={operation === 'CANCEL' || operation === 'REJECT_RESOLUTION' ? 'red' : undefined}
              disabled={!text.trim()}
              loading={busy}
              onClick={() => submit(call, cfg.done)}
            >
              {cfg.button}
            </Button>
          </Group>
        </Stack>
      </Modal>
    );
  }

  return (
    <>
      <Modal opened={operation === 'CLASSIFY'} onClose={close} title="Classify incident">
        <Stack>
          {errorAlert}
          <Text size="sm" c="dimmed">
            Setting the category calculates priority and SLA deadlines, and may assign a technician automatically.
          </Text>
          <Select
            label="Category"
            data={(categories.data ?? []).filter((c) => c.active).map((c) => ({ value: String(c.id), label: c.name }))}
            value={categoryId}
            onChange={setCategoryId}
            searchable
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              Back
            </Button>
            <Button
              disabled={!categoryId}
              loading={busy}
              onClick={() => submit(() => api.incidents.classify(id, Number(categoryId)), 'Incident classified')}
            >
              Classify
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal opened={operation === 'ASSIGN'} onClose={close} title="Assign a technician" size="lg">
        <Stack>
          {errorAlert}
          {recommendations.isPending && <Loader mx="auto" />}
          {recommendations.error && <Alert color="red">{errorMessage(recommendations.error)}</Alert>}
          {recommendations.data?.length === 0 && <Text c="dimmed">No available technicians.</Text>}
          {recommendations.data?.map((r, index) => {
            const load = r.maxActiveIncidents > 0 ? (r.activeIncidents / r.maxActiveIncidents) * 100 : 0;
            return (
              <Card key={r.technicianId} withBorder>
                <Group justify="space-between" align="flex-start">
                  <Stack gap={4} style={{ flex: 1 }}>
                    <Group gap="xs">
                      <Text fw={600}>{r.fullName}</Text>
                      {index === 0 && <Badge color="green">Best match</Badge>}
                      {incident.assignee?.id === r.technicianId && <Badge variant="outline">Current</Badge>}
                      <Text size="sm" c="dimmed">
                        score {r.score.toFixed(2)}
                      </Text>
                    </Group>
                    <Group gap="xs">
                      <Text size="xs" c="dimmed">
                        Workload {r.activeIncidents}/{r.maxActiveIncidents}
                      </Text>
                      <Progress value={load} w={120} size="sm" color={load >= 100 ? 'red' : load >= 75 ? 'orange' : 'teal'} />
                    </Group>
                    {r.reasons.map((reason) => (
                      <Text key={reason} size="xs">
                        • {reason}
                      </Text>
                    ))}
                  </Stack>
                  <Button
                    size="xs"
                    loading={busy}
                    disabled={incident.assignee?.id === r.technicianId}
                    onClick={() => submit(() => api.incidents.assign(id, r.technicianId), `Assigned to ${r.fullName}`)}
                  >
                    Assign
                  </Button>
                </Group>
              </Card>
            );
          })}
        </Stack>
      </Modal>

      <Modal opened={operation === 'RESOLVE'} onClose={close} title="Resolve incident" size="lg">
        <Stack>
          {errorAlert}
          <Textarea label="Resolution summary" required autosize minRows={3} value={summary} onChange={(e) => setSummary(e.currentTarget.value)} />
          <Textarea label="Root cause" autosize minRows={2} value={rootCause} onChange={(e) => setRootCause(e.currentTarget.value)} />
          <Select
            label="Resolution type"
            allowDeselect={false}
            data={RESOLUTION_CODES.map((c) => ({ value: c, label: humanize(c) }))}
            value={code}
            onChange={(v) => v && setCode(v as ResolutionCode)}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              Back
            </Button>
            <Button
              color="teal"
              disabled={!summary.trim()}
              loading={busy}
              onClick={() =>
                submit(
                  () => api.incidents.resolve(id, { summary: summary.trim(), rootCause: rootCause.trim() || undefined, code }),
                  'Incident resolved',
                )
              }
            >
              Resolve
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal opened={operation === 'ACCEPT_RESOLUTION'} onClose={close} title="Confirm the fix">
        <Stack>
          {errorAlert}
          <Text size="sm">How satisfied are you with how this incident was handled?</Text>
          <Rating value={rating} onChange={setRating} size="lg" />
          <Textarea label="Comment (optional)" autosize minRows={2} value={text} onChange={(e) => setText(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              Back
            </Button>
            <Button
              color="green"
              loading={busy}
              onClick={() => submit(() => api.incidents.accept(id, rating, text.trim() || undefined), 'Thanks for your feedback')}
            >
              Accept resolution
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
