import { Alert, Anchor, Button, Group, PasswordInput, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '../components/AuthLayout';
import { errorMessage } from '../lib/format';

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const form = useForm({
    initialValues: { fullName: '', email: '', password: '', confirm: '', department: '', location: '' },
    validate: {
      fullName: (v) => (v.trim() ? null : 'Enter your name'),
      email: (v) => (/^\S+@\S+\.\S+$/.test(v) ? null : 'Enter a valid email'),
      password: (v) => (v.length >= 8 ? null : 'At least 8 characters'),
      confirm: (v, values) => (v === values.password ? null : 'Passwords do not match'),
    },
  });

  if (user) return <Navigate to="/" replace />;

  const submit = form.onSubmit(async ({ confirm: _confirm, ...values }) => {
    setError(null);
    setSubmitting(true);
    try {
      await register({
        ...values,
        department: values.department || undefined,
        location: values.location || undefined,
      });
      navigate('/', { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <AuthLayout
      title="Create an employee account"
      subtitle="Report incidents and follow them to resolution. Support staff accounts are created by an administrator."
    >
      <form onSubmit={submit}>
        <Stack>
          {error && (
            <Alert color="red" icon={<IconAlertCircle size={16} />} style={{ whiteSpace: 'pre-line' }}>
              {error}
            </Alert>
          )}
          <TextInput label="Full name" required {...form.getInputProps('fullName')} />
          <TextInput
            label="Work email"
            placeholder="you@iitsm.example.com"
            description="Only organisation email addresses can register"
            required
            autoComplete="username"
            {...form.getInputProps('email')}
          />
          <Group grow>
            <TextInput label="Department" {...form.getInputProps('department')} />
            <TextInput label="Location" {...form.getInputProps('location')} />
          </Group>
          <PasswordInput
            label="Password"
            description="At least 8 characters, with a letter and a digit"
            required
            autoComplete="new-password"
            {...form.getInputProps('password')}
          />
          <PasswordInput label="Confirm password" required autoComplete="new-password" {...form.getInputProps('confirm')} />
          <Button type="submit" loading={submitting} fullWidth mt="xs">
            Create account
          </Button>
          <Text size="sm" c="dimmed" ta="center">
            Already have an account?{' '}
            <Anchor component={Link} to="/login">
              Sign in
            </Anchor>
          </Text>
        </Stack>
      </form>
    </AuthLayout>
  );
}
