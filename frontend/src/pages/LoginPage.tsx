import { Alert, Anchor, Button, Code, Group, Paper, PasswordInput, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '../components/AuthLayout';
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
    <AuthLayout title="Sign in" subtitle="Use your work email and password.">
      <form onSubmit={submit}>
        <Stack>
          {error && (
            <Alert color="red" icon={<IconAlertCircle size={16} />}>
              {error}
            </Alert>
          )}
          <TextInput label="Email" placeholder="you@company.com" autoComplete="username" {...form.getInputProps('email')} />
          <PasswordInput label="Password" autoComplete="current-password" {...form.getInputProps('password')} />
          <Button type="submit" loading={submitting} fullWidth mt="xs">
            Sign in
          </Button>
          <Text size="sm" c="dimmed" ta="center">
            New employee?{' '}
            <Anchor component={Link} to="/register">
              Create an account
            </Anchor>
          </Text>
        </Stack>
      </form>

      {import.meta.env.DEV && (
        <Paper withBorder p="md" mt="xl">
          <Text size="xs" fw={600} tt="uppercase" c="dimmed" mb={6} style={{ letterSpacing: '0.04em' }}>
            Demo accounts · password{' '}
            <Code fz="xs" style={{ textTransform: 'none' }}>
              Password123
            </Code>
          </Text>
          {/* Role and email side by side when there is room, stacked on narrow phones. */}
          <Stack gap={0}>
            {DEMO_ACCOUNTS.map((a, index) => (
              <Group
                key={a.email}
                justify="space-between"
                gap={4}
                py={5}
                style={index ? { borderTop: '1px solid var(--app-border)' } : undefined}
              >
                <Text size="sm" c="dimmed">
                  {a.role}
                </Text>
                <Anchor
                  component="button"
                  type="button"
                  size="sm"
                  ff="monospace"
                  onClick={() => form.setValues({ email: a.email, password: 'Password123' })}
                >
                  {a.email}
                </Anchor>
              </Group>
            ))}
          </Stack>
        </Paper>
      )}
    </AuthLayout>
  );
}
