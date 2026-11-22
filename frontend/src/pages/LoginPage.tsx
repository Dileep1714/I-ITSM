import { Alert, Anchor, Button, Group, PasswordInput, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
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
            <Alert color="stamp" variant="outline" icon={<IconAlertCircle size={16} />}>
              {error}
            </Alert>
          )}
          <TextInput label="Work email" placeholder="name@company.com" autoComplete="username" {...form.getInputProps('email')} />
          <PasswordInput label="Password" autoComplete="current-password" {...form.getInputProps('password')} />
          <Button type="submit" loading={submitting} fullWidth size="md" mt="xs">
            Sign in
          </Button>
        </Stack>
      </form>

      {/* Development builds only: never shown in the deployed app. */}
      {import.meta.env.DEV && (
        <div style={{ marginTop: 28, paddingTop: 12, borderTop: '1px dashed var(--app-border)' }}>
          <Text size="xs" fw={700} c="dimmed" mb={6} ff="var(--label-font)" style={{ letterSpacing: '0.12em' }}>
            DEVELOPMENT ONLY · PASSWORD123
          </Text>
          <Group gap={10}>
            {DEMO_ACCOUNTS.map((a) => (
              <Anchor
                key={a.email}
                component="button"
                type="button"
                size="xs"
                ff="monospace"
                title={a.role}
                onClick={() => form.setValues({ email: a.email, password: 'Password123' })}
              >
                {a.email.split('@')[0]}
              </Anchor>
            ))}
          </Group>
        </div>
      )}
    </AuthLayout>
  );
}
