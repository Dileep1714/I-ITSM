import { Anchor, Badge, Button, Card, Group, Stack, Text, Tooltip } from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { IconBulb, IconCopy, IconLink } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { IncidentDetail } from '../../api/types';
import { StatusBadge } from '../../components/Badges';
import { errorMessage, humanize } from '../../lib/format';

/**
 * Support staff only: likely duplicates (link + cancel in one click) and similar past incidents with the fix that
 * worked then.
 */
export function RelatedIncidents({ incident, onUpdated }: { incident: IncidentDetail; onUpdated: (i: IncidentDetail) => void }) {
  const related = useQuery({
    queryKey: ['incidents', incident.id, 'similar'],
    queryFn: () => api.incidents.similar(incident.id),
  });
  const canMark = incident.allowedOperations.includes('MARK_DUPLICATE');

  const markDuplicate = (originalId: number, originalNumber: string) =>
    modals.openConfirmModal({
      title: 'Mark as duplicate',
      children: (
        <Text size="sm">
          Cancel {incident.number} as a duplicate of {originalNumber}? The reporter is notified and the two incidents stay linked.
        </Text>
      ),
      labels: { confirm: 'Mark duplicate', cancel: 'Back' },
      confirmProps: { color: 'orange' },
      onConfirm: () =>
        api.incidents
          .markDuplicate(incident.id, originalId)
          .then((updated) => {
            onUpdated(updated);
            notifications.show({ color: 'green', message: `Linked to ${originalNumber} and cancelled` });
          })
          .catch((e) => notifications.show({ color: 'red', title: 'Not possible', message: errorMessage(e) })),
    });

  if (related.isPending || related.isError || related.data.length === 0) {
    return null;
  }

  return (
    <Card withBorder radius="md">
      <Group gap="xs" mb="xs">
        <IconCopy size={18} />
        <Text fw={600}>Related incidents</Text>
      </Group>
      <Stack gap="md">
        {related.data.map((r) => (
          <div key={r.id}>
            <Group justify="space-between" gap="xs" wrap="nowrap">
              <Anchor component={Link} to={`/incidents/${r.id}`} size="sm" ff="monospace" style={{ whiteSpace: 'nowrap' }}>
                {r.number}
              </Anchor>
              <Tooltip label={r.reasons.join(' · ')} multiline w={260}>
                <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                  {Math.round(r.score * 100)}% match
                </Text>
              </Tooltip>
            </Group>
            <Text size="sm" lineClamp={2}>
              {r.title}
            </Text>
            <Group gap={6} mt={4}>
              <Badge size="sm" color={r.relation === 'LIKELY_DUPLICATE' ? 'orange' : 'gray'}>
                {r.relation === 'LIKELY_DUPLICATE' ? 'Likely duplicate' : 'Similar'}
              </Badge>
              <StatusBadge status={r.status} />
            </Group>
            {r.resolutionSummary && (
              <Group gap={4} wrap="nowrap" align="flex-start" mt={2}>
                <IconBulb size={14} color="var(--app-ink-carbon)" style={{ flexShrink: 0, marginTop: 2 }} />
                <Text size="xs" c="dimmed">
                  <b>Fixed before ({humanize(r.resolutionCode)}):</b> {r.resolutionSummary}
                </Text>
              </Group>
            )}
            {canMark && r.relation === 'LIKELY_DUPLICATE' && (
              <Button
                size="compact-xs"
                variant="light"
                color="orange"
                mt={4}
                leftSection={<IconLink size={12} />}
                onClick={() => markDuplicate(r.id, r.number)}
              >
                Mark this incident as its duplicate
              </Button>
            )}
          </div>
        ))}
      </Stack>
    </Card>
  );
}
