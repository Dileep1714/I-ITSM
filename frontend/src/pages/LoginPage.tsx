import { Alert, Anchor, Button, Center, Code, Paper, PasswordInput, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { errorMessage } from '../lib/format';

const DEMO_ACCOUNTS = [
  { role: 'Employee', email: 'asha@iitsm.example.com' },
  { role: 'Technician (L1)', email: 'ravi@iitsm.example.com' },
  { role: 'Team lead (L2)', email: 'kavya@iitsm.example.com' },
  { role: 'Administrator', email: 'admin@iitsm.example.com' },
];

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const form = useForm({
    initialValues: { email: '', password: '' },
    validate: {
      email: (v) => (/^\S+@\S+$/.test(v) ? null : 'Enter a valid email'),
      password: (v) => (v ? null : 'Enter your password'),
    },
  });

  const from = (location.state as { from?: string } | null)?.from ?? '/';
  if (user) return <Navigate to={from} replace />;

  const submit = form.onSubmit(async ({ email, password }) => {
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <Center mih="100vh" p="md" bg="var(--mantine-color-gray-0)">
      <Stack w={420} maw="100%">
        <div>
          <Title order={2} ta="center">
            I-ITSM
          </Title>
          <Text c="dimmed" ta="center" size="sm">
            Intelligent IT Service Management
          </Text>
        </div>
        <Paper withBorder shadow="sm" p="xl" radius="md">
          <form onSubmit={submit}>
            <Stack>
              {error && (
                <Alert color="red" icon={<IconAlertCircle size={16} />}>
                  {error}
                </Alert>
              )}
              <TextInput label="Email" placeholder="you@company.com" autoComplete="username" {...form.getInputProps('email')} />
              <PasswordInput label="Password" autoComplete="current-password" {...form.getInputProps('password')} />
              <Button type="submit" loading={submitting} fullWidth>
                Log in
              </Button>
              <Text size="sm" ta="center">
                No account?{' '}
                <Anchor component={Link} to="/register">
                  Register as an employee
                </Anchor>
              </Text>
            </Stack>
          </form>
        </Paper>
        {import.meta.env.DEV && (
          <Paper withBorder p="md" radius="md">
            <Text size="sm" fw={600} mb={4}>
              Demo accounts (password <Code>Password123</Code>)
            </Text>
            <Table verticalSpacing={4} fz="sm">
              <Table.Tbody>
                {DEMO_ACCOUNTS.map((a) => (
                  <Table.Tr key={a.email}>
                    <Table.Td c="dimmed">{a.role}</Table.Td>
                    <Table.Td>
                      <Anchor
                        component="button"
                        type="button"
                        size="sm"
                        onClick={() => form.setValues({ email: a.email, password: 'Password123' })}
                      >
                        {a.email}
                      </Anchor>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Paper>
        )}
      </Stack>
    </Center>
  );
}
