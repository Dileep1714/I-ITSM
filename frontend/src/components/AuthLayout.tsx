import { Box, Group, List, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconCheck } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import classes from './AuthLayout.module.css';
import { BrandMark } from './BrandMark';

const CAPABILITIES = [
  'Automatic prioritisation and SLA tracking for every incident',
  'Skill- and workload-aware assignment with escalation',
  'Duplicate detection and root-cause correlation',
  'Full audit trail and operational reporting',
];

/** Sign-in and registration frame: a product panel on the left (hidden on small screens), the form on the right. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className={classes.root}>
      <aside className={classes.panel}>
        <BrandMark inverted size="lg" />
        <Stack gap="lg" maw={420}>
          <Title order={1} c="white" fw={600} fz={30} lh={1.2}>
            IT service management for the whole organisation.
          </Title>
          <List
            spacing="sm"
            size="sm"
            c="var(--app-sidebar-text)"
            icon={
              <ThemeIcon size={18} radius="xl" variant="light">
                <IconCheck size={11} stroke={2.5} />
              </ThemeIcon>
            }
          >
            {CAPABILITIES.map((c) => (
              <List.Item key={c}>{c}</List.Item>
            ))}
          </List>
        </Stack>
        <Text size="xs" c="var(--app-sidebar-muted)">
          CS F213 · Object-Oriented Programming · 2026–27
        </Text>
      </aside>

      <main className={classes.formSide}>
        <Box w={400} maw="100%">
          <Group hiddenFrom="md" mb="xl">
            <BrandMark />
          </Group>
          <Title order={2} mb={4}>
            {title}
          </Title>
          {subtitle && (
            <Text size="sm" c="dimmed" mb="xl">
              {subtitle}
            </Text>
          )}
          {children}
        </Box>
      </main>
    </div>
  );
}
