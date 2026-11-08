import { Badge, Card, Group, Progress, SegmentedControl, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Availability } from '../api/types';
import { QueryState } from '../components/QueryState';
import { errorMessage, formatDateTime, humanize } from '../lib/format';

const AVAILABILITY: Availability[] = ['AVAILABLE', 'BUSY', 'OFF_DUTY', 'ON_LEAVE'];

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
        {label}
      </Text>
      <Text size="sm">{value}</Text>
    </div>
  );
}

/** The logged-in user's own profile. Technicians also see their skills and workload and set their availability. */
export function ProfilePage() {
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ['me', 'profile'], queryFn: api.me.profile });
  const setAvailability = useMutation({
    mutationFn: api.me.setAvailability,
    onSuccess: (updated) => {
      queryClient.setQueryData(['me', 'profile'], updated);
      notifications.show({
        color: 'green',
        message: updated.availability === 'AVAILABLE'
          ? 'You will receive new assignments'
          : `Marked ${humanize(updated.availability)}: no new automatic assignments`,
      });
    },
    onError: (e) => notifications.show({ color: 'red', message: errorMessage(e) }),
  });

  return (
    <Stack maw={900}>
      <Title order={2}>My profile</Title>
      <QueryState query={profile}>
        {profile.data && (
          <>
            <Card withBorder radius="md">
              <SimpleGrid cols={{ base: 1, sm: 3 }}>
                <Field label="Name" value={profile.data.fullName} />
                <Field label="Email" value={profile.data.email} />
                <Field label="Role" value={humanize(profile.data.role)} />
                {profile.data.department && <Field label="Department" value={profile.data.department} />}
                {profile.data.location && <Field label="Location" value={profile.data.location} />}
                <Field label="Member since" value={formatDateTime(profile.data.createdAt)} />
              </SimpleGrid>
            </Card>

            {profile.data.role === 'TECHNICIAN' && (
              <>
                <Card withBorder radius="md">
                  <Text fw={600}>Availability</Text>
                  <Text size="sm" c="dimmed" mb="sm">
                    Only available technicians receive new automatic assignments. Incidents you already own stay with you.
                  </Text>
                  <SegmentedControl
                    data={AVAILABILITY.map((a) => ({ value: a, label: humanize(a) }))}
                    value={profile.data.availability ?? 'AVAILABLE'}
                    onChange={(v) => setAvailability.mutate(v as Availability)}
                    disabled={setAvailability.isPending}
                  />
                </Card>
                <SimpleGrid cols={{ base: 1, sm: 2 }}>
                  <Card withBorder radius="md">
                    <Text fw={600} mb="xs">
                      Workload
                    </Text>
                    <Group justify="space-between">
                      <Text size="sm">
                        {profile.data.activeIncidents} of {profile.data.maxActiveIncidents} active incidents
                      </Text>
                      <Text size="sm" c="dimmed">
                        {humanize(profile.data.supportLevel?.replace(/^L\d_/, ''))} · {profile.data.experienceYears} yrs
                      </Text>
                    </Group>
                    <Progress
                      mt="xs"
                      value={(100 * (profile.data.activeIncidents ?? 0)) / (profile.data.maxActiveIncidents ?? 1)}
                      aria-label="Workload"
                    />
                  </Card>
                  <Card withBorder radius="md">
                    <Text fw={600} mb="xs">
                      Skills
                    </Text>
                    <Group gap="xs">
                      {profile.data.skills.length === 0 && (
                        <Text size="sm" c="dimmed">
                          No skills recorded yet: ask an administrator.
                        </Text>
                      )}
                      {profile.data.skills.map((s) => (
                        <Badge key={s.categoryId} variant="light">
                          {s.categoryName} {s.proficiency}/5
                        </Badge>
                      ))}
                    </Group>
                  </Card>
                </SimpleGrid>
              </>
            )}
          </>
        )}
      </QueryState>
    </Stack>
  );
}
