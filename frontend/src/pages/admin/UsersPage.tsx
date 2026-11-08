import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Paper,
  PasswordInput,
  Rating,
  SegmentedControl,
  Select,
  Stack,
  Switch,
  Table,
  Tabs,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconPencil, IconPlus, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Availability, Role, SkillRequest, SupportLevel, UserAdminView } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { QueryState } from '../../components/QueryState';
import { errorMessage, formatDateTime, humanize } from '../../lib/format';

const ROLES: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR'];
const LEVELS: SupportLevel[] = ['L1_TECHNICIAN', 'L2_TEAM_LEAD', 'L3_SYSTEM_ADMIN', 'L4_IT_MANAGER'];
const AVAILABILITY: Availability[] = ['AVAILABLE', 'BUSY', 'OFF_DUTY', 'ON_LEAVE'];
const ROLE_COLOR: Record<Role, string> = { EMPLOYEE: 'blue', TECHNICIAN: 'teal', ADMINISTRATOR: 'grape' };

const levelLabel = (l: SupportLevel | null) => humanize(l?.replace(/^L\d_/, ''));

/** Accounts of every role. Technician and administrator accounts can only be created here. */
export function UsersPage() {
  const [role, setRole] = useState<string>('ALL');
  const users = useQuery({
    queryKey: ['admin', 'users', role],
    queryFn: () => api.admin.users(role === 'ALL' ? undefined : (role as Role)),
  });
  const [editing, setEditing] = useState<UserAdminView | null>(null);
  const [creating, setCreating] = useState(false);

  return (
    <Stack>
      <Group justify="space-between" align="flex-end">
        <div>
          <Title order={2}>Users</Title>
          <Text size="sm" c="dimmed">
            Deactivated users are locked out immediately but stay in incident history.
          </Text>
        </div>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setCreating(true)}>
          New user
        </Button>
      </Group>
      <SegmentedControl
        w="fit-content"
        value={role}
        onChange={setRole}
        data={[{ value: 'ALL', label: 'All' }, ...ROLES.map((r) => ({ value: r, label: humanize(r) + 's' }))]}
      />
      <Paper withBorder radius="md" p="md">
        <QueryState query={users}>
          <Table.ScrollContainer minWidth={760}>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Name</Table.Th>
                  <Table.Th>Role</Table.Th>
                  <Table.Th>Details</Table.Th>
                  <Table.Th>Status</Table.Th>
                  <Table.Th>Since</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {users.data?.map((u) => (
                  <Table.Tr key={u.id} opacity={u.active ? 1 : 0.55}>
                    <Table.Td>
                      <Text size="sm" fw={500}>
                        {u.fullName}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {u.email}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Badge color={ROLE_COLOR[u.role]} variant="light">
                        {humanize(u.role)}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      {u.role === 'TECHNICIAN' ? (
                        <Text size="xs">
                          {levelLabel(u.supportLevel)} · {u.activeIncidents}/{u.maxActiveIncidents} active ·{' '}
                          {humanize(u.availability)}
                          <br />
                          <Text span c="dimmed" size="xs">
                            {u.skills.map((s) => `${s.categoryName} ${s.proficiency}`).join(', ') || 'no skills'}
                          </Text>
                        </Text>
                      ) : (
                        <Text size="xs" c="dimmed">
                          {[u.department, u.location].filter(Boolean).join(' · ') || '—'}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td>
                      <Badge color={u.active ? 'green' : 'gray'} variant="dot">
                        {u.active ? 'Active' : 'Deactivated'}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      <Text size="xs">{formatDateTime(u.createdAt)}</Text>
                    </Table.Td>
                    <Table.Td>
                      <Button size="xs" variant="subtle" leftSection={<IconPencil size={14} />} onClick={() => setEditing(u)}>
                        Edit
                      </Button>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </QueryState>
      </Paper>
      {creating && <CreateUserModal onClose={() => setCreating(false)} />}
      {editing && <EditUserModal user={editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function useInvalidateUsers() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
    queryClient.invalidateQueries({ queryKey: ['reports'] });
  };
}

/** Category + proficiency rows; each category at most once. */
function SkillsEditor({ value, onChange }: { value: SkillRequest[]; onChange: (skills: SkillRequest[]) => void }) {
  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: api.admin.categories });
  const used = new Set(value.map((s) => s.categoryId));
  const available = (categories.data ?? []).filter((c) => !used.has(c.id));
  const name = (id: number) => categories.data?.find((c) => c.id === id)?.name ?? `#${id}`;

  return (
    <Stack gap="xs">
      <Text size="sm" fw={500}>
        Skills (proficiency 1–5)
      </Text>
      {value.map((s, index) => (
        <Group key={s.categoryId} justify="space-between">
          <Text size="sm">{name(s.categoryId)}</Text>
          <Group gap="xs">
            <Rating
              value={s.proficiency}
              onChange={(p) => onChange(value.map((x, i) => (i === index ? { ...x, proficiency: p } : x)))}
            />
            <ActionIcon variant="subtle" color="red" aria-label="Remove skill" onClick={() => onChange(value.filter((_, i) => i !== index))}>
              <IconTrash size={14} />
            </ActionIcon>
          </Group>
        </Group>
      ))}
      {available.length > 0 && (
        <Select
          placeholder="Add a skill…"
          data={available.map((c) => ({ value: String(c.id), label: c.name }))}
          value={null}
          onChange={(id) => id && onChange([...value, { categoryId: Number(id), proficiency: 3 }])}
        />
      )}
    </Stack>
  );
}

function CreateUserModal({ onClose }: { onClose: () => void }) {
  const invalidate = useInvalidateUsers();
  const form = useForm({
    initialValues: {
      role: 'TECHNICIAN' as Role,
      fullName: '',
      email: '',
      password: '',
      department: '',
      location: '',
      supportLevel: 'L1_TECHNICIAN' as SupportLevel,
      experienceYears: 1 as number | string,
      maxActiveIncidents: 5 as number | string,
      skills: [] as SkillRequest[],
    },
    validate: {
      fullName: (v) => (v.trim() ? null : 'Enter a name'),
      email: (v) => (/^\S+@\S+\.\S+$/.test(v) ? null : 'Enter a valid email'),
      password: (v) =>
        v.length >= 8 && /[A-Za-z]/.test(v) && /\d/.test(v) ? null : 'At least 8 characters with a letter and a digit',
    },
  });
  const create = useMutation({
    mutationFn: (v: typeof form.values) =>
      api.admin.createUser({
        role: v.role,
        fullName: v.fullName.trim(),
        email: v.email.trim(),
        password: v.password,
        ...(v.role === 'EMPLOYEE' ? { department: v.department || undefined, location: v.location || undefined } : {}),
        ...(v.role === 'TECHNICIAN'
          ? {
              supportLevel: v.supportLevel,
              experienceYears: Number(v.experienceYears),
              maxActiveIncidents: Number(v.maxActiveIncidents),
              skills: v.skills,
            }
          : {}),
      }),
    onSuccess: (u) => {
      invalidate();
      notifications.show({ color: 'green', message: `Created ${humanize(u.role).toLowerCase()} ${u.fullName}` });
      onClose();
    },
    onError: (e) => notifications.show({ color: 'red', title: 'Not created', message: errorMessage(e) }),
  });
  const role = form.values.role;

  return (
    <Modal opened onClose={onClose} title="New user" size="lg">
      <form onSubmit={form.onSubmit((v) => create.mutate(v))}>
        <Stack>
          <SegmentedControl data={ROLES.map((r) => ({ value: r, label: humanize(r) }))} {...form.getInputProps('role')} />
          <Group grow>
            <TextInput label="Full name" required {...form.getInputProps('fullName')} />
            <TextInput label="Email" required {...form.getInputProps('email')} />
          </Group>
          <PasswordInput label="Initial password" required description="Share it securely; the user can't change it themselves yet." {...form.getInputProps('password')} />
          {role === 'EMPLOYEE' && (
            <Group grow>
              <TextInput label="Department" {...form.getInputProps('department')} />
              <TextInput label="Location" {...form.getInputProps('location')} />
            </Group>
          )}
          {role === 'TECHNICIAN' && (
            <>
              <Group grow>
                <Select label="Support level" allowDeselect={false} data={LEVELS.map((l) => ({ value: l, label: levelLabel(l) }))} {...form.getInputProps('supportLevel')} />
                <NumberInput label="Experience (years)" min={0} max={50} {...form.getInputProps('experienceYears')} />
                <NumberInput label="Max active incidents" min={1} max={50} {...form.getInputProps('maxActiveIncidents')} />
              </Group>
              <SkillsEditor value={form.values.skills} onChange={(s) => form.setFieldValue('skills', s)} />
            </>
          )}
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Create
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

function EditUserModal({ user, onClose }: { user: UserAdminView; onClose: () => void }) {
  const { user: me } = useAuth();
  const invalidate = useInvalidateUsers();
  const onError = (e: unknown) => notifications.show({ color: 'red', title: 'Not saved', message: errorMessage(e) });
  const done = (message: string) => {
    invalidate();
    notifications.show({ color: 'green', message });
    onClose();
  };

  const profile = useForm({
    initialValues: {
      fullName: user.fullName,
      email: user.email,
      active: user.active,
      department: user.department ?? '',
      location: user.location ?? '',
    },
  });
  const saveProfile = useMutation({
    mutationFn: (v: typeof profile.values) =>
      api.admin.updateUser(user.id, {
        fullName: v.fullName.trim(),
        email: v.email.trim(),
        active: v.active,
        ...(user.role === 'EMPLOYEE' ? { department: v.department, location: v.location } : {}),
      }),
    onSuccess: () => done('Profile saved'),
    onError,
  });

  const tech = useForm({
    initialValues: {
      supportLevel: (user.supportLevel ?? 'L1_TECHNICIAN') as SupportLevel,
      experienceYears: user.experienceYears ?? 0,
      maxActiveIncidents: user.maxActiveIncidents ?? 5,
      availability: (user.availability ?? 'AVAILABLE') as Availability,
      skills: user.skills.map((s) => ({ categoryId: s.categoryId, proficiency: s.proficiency })),
    },
  });
  const saveTech = useMutation({
    mutationFn: (v: typeof tech.values) =>
      api.admin.updateTechnician(user.id, {
        ...v,
        experienceYears: Number(v.experienceYears),
        maxActiveIncidents: Number(v.maxActiveIncidents),
      }),
    onSuccess: () => done('Technician settings saved'),
    onError,
  });

  const [password, setPassword] = useState('');
  const reset = useMutation({
    mutationFn: () => api.admin.resetPassword(user.id, password),
    onSuccess: () => done(`Password reset for ${user.fullName}`),
    onError,
  });

  return (
    <Modal opened onClose={onClose} title={user.fullName} size="lg">
      <Tabs defaultValue="profile">
        <Tabs.List mb="md">
          <Tabs.Tab value="profile">Profile</Tabs.Tab>
          {user.role === 'TECHNICIAN' && <Tabs.Tab value="technician">Technician</Tabs.Tab>}
          <Tabs.Tab value="password">Password</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="profile">
          <form onSubmit={profile.onSubmit((v) => saveProfile.mutate(v))}>
            <Stack>
              <Group grow>
                <TextInput label="Full name" {...profile.getInputProps('fullName')} />
                <TextInput label="Email" {...profile.getInputProps('email')} />
              </Group>
              {user.role === 'EMPLOYEE' && (
                <Group grow>
                  <TextInput label="Department" {...profile.getInputProps('department')} />
                  <TextInput label="Location" {...profile.getInputProps('location')} />
                </Group>
              )}
              <Switch
                label="Active (can log in)"
                disabled={user.id === me?.id}
                description={user.id === me?.id ? 'You cannot deactivate your own account' : undefined}
                {...profile.getInputProps('active', { type: 'checkbox' })}
              />
              <Group justify="flex-end">
                <Button type="submit" loading={saveProfile.isPending}>
                  Save profile
                </Button>
              </Group>
            </Stack>
          </form>
        </Tabs.Panel>

        {user.role === 'TECHNICIAN' && (
          <Tabs.Panel value="technician">
            <form onSubmit={tech.onSubmit((v) => saveTech.mutate(v))}>
              <Stack>
                <Group grow>
                  <Select label="Support level" description="Team lead and above can assign incidents" allowDeselect={false} data={LEVELS.map((l) => ({ value: l, label: levelLabel(l) }))} {...tech.getInputProps('supportLevel')} />
                  <Select label="Availability" allowDeselect={false} data={AVAILABILITY.map((a) => ({ value: a, label: humanize(a) }))} {...tech.getInputProps('availability')} />
                </Group>
                <Group grow>
                  <NumberInput label="Experience (years)" min={0} max={50} {...tech.getInputProps('experienceYears')} />
                  <NumberInput label="Max active incidents" description={`Currently ${user.activeIncidents} active`} min={1} max={50} {...tech.getInputProps('maxActiveIncidents')} />
                </Group>
                <SkillsEditor value={tech.values.skills} onChange={(s) => tech.setFieldValue('skills', s)} />
                <Group justify="flex-end">
                  <Button type="submit" loading={saveTech.isPending}>
                    Save technician settings
                  </Button>
                </Group>
              </Stack>
            </form>
          </Tabs.Panel>
        )}

        <Tabs.Panel value="password">
          <Stack>
            <PasswordInput
              label="New password"
              description="8–72 characters with at least one letter and one digit"
              value={password}
              onChange={(e) => setPassword(e.currentTarget.value)}
            />
            <Group justify="flex-end">
              <Button color="orange" disabled={password.length < 8} loading={reset.isPending} onClick={() => reset.mutate()}>
                Reset password
              </Button>
            </Group>
          </Stack>
        </Tabs.Panel>
      </Tabs>
    </Modal>
  );
}
