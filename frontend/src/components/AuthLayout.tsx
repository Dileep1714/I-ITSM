import { Box, Group, Stack, Text, Title } from '@mantine/core';
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
        <Stack gap="xl" maw={440}>
          <Title order={1} c="white" fw={800} fz={36} lh={1.12} style={{ letterSpacing: '-0.02em' }}>
            IT service management for the whole organisation.
          </Title>
          <ul className={classes.capabilities}>
            {CAPABILITIES.map((c) => (
              <li key={c} className={classes.capability}>
                <span className={classes.tick} aria-hidden="true">
                  <IconCheck size={12} stroke={3} />
                </span>
                {c}
              </li>
            ))}
          </ul>
        </Stack>
        <Text size="xs" ff="var(--label-font)" c="var(--app-sidebar-muted)" style={{ letterSpacing: '0.04em' }}>
          CS F213 · Object-Oriented Programming · 2026–27
        </Text>
      </aside>

      <main className={classes.formSide}>
        <Box w="100%" maw={400}>
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
