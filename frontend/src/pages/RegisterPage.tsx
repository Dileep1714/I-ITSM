import { Alert, Anchor, Button, Center, Group, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
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
    <Center mih="100vh" p="md" bg="var(--mantine-color-gray-0)">
      <Paper withBorder shadow="sm" p="xl" radius="md" w={480} maw="100%">
        <Title order={3} mb="md">
          Create an employee account
        </Title>
        <form onSubmit={submit}>
          <Stack>
            {error && (
              <Alert color="red" icon={<IconAlertCircle size={16} />} style={{ whiteSpace: 'pre-line' }}>
                {error}
              </Alert>
            )}
            <TextInput label="Full name" required {...form.getInputProps('fullName')} />
            <TextInput label="Email" required autoComplete="username" {...form.getInputProps('email')} />
            <Group grow>
              <TextInput label="Department" {...form.getInputProps('department')} />
              <TextInput label="Location" {...form.getInputProps('location')} />
            </Group>
            <PasswordInput label="Password" required autoComplete="new-password" {...form.getInputProps('password')} />
            <PasswordInput label="Confirm password" required autoComplete="new-password" {...form.getInputProps('confirm')} />
            <Button type="submit" loading={submitting}>
              Register
            </Button>
            <Text size="sm" ta="center">
              Already registered?{' '}
              <Anchor component={Link} to="/login">
                Log in
              </Anchor>
            </Text>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
