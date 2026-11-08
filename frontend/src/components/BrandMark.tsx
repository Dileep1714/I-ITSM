import { Group, Text, ThemeIcon } from '@mantine/core';
import { IconStack2 } from '@tabler/icons-react';

/** Product mark: a plain square glyph and the wordmark. Used in the sidebar and on the sign-in screen. */
export function BrandMark({ inverted = false, size = 'md' }: { inverted?: boolean; size?: 'md' | 'lg' }) {
  const large = size === 'lg';
  return (
    <Group gap={10} wrap="nowrap">
      <ThemeIcon size={large ? 36 : 28} radius="sm" variant="filled">
        <IconStack2 size={large ? 20 : 16} stroke={1.8} />
      </ThemeIcon>
      <div style={{ lineHeight: 1.1 }}>
        <Text fw={600} fz={large ? 'lg' : 'md'} c={inverted ? 'white' : undefined}>
          I-ITSM
        </Text>
        <Text fz="xs" c={inverted ? 'var(--app-sidebar-muted)' : 'dimmed'}>
          Service Management
        </Text>
      </div>
    </Group>
  );
}
