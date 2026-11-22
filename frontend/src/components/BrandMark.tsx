import { Group, Text } from '@mantine/core';

/**
 * Product mark: a numbering-machine stamp (a square ruled box holding "IT" in typed figures) and the wordmark.
 * Used in the rail and on the sign-in screen.
 */
export function BrandMark({ inverted = false, size = 'md' }: { inverted?: boolean; size?: 'md' | 'lg' }) {
  const large = size === 'lg';
  const box = large ? 40 : 32;
  const ink = inverted ? '#ffffff' : 'var(--app-ink-carbon)';
  return (
    <Group gap={10} wrap="nowrap">
      <svg width={box} height={box} viewBox="0 0 32 32" aria-hidden="true">
        <rect x="1" y="1" width="30" height="30" fill="none" stroke={ink} strokeWidth="1.5" />
        <rect x="4" y="4" width="24" height="24" fill="none" stroke={ink} strokeWidth="0.75" />
        <text
          x="16"
          y="20.5"
          textAnchor="middle"
          fill={ink}
          style={{ font: '700 12px "Courier Prime", monospace' }}
        >
          IT
        </text>
      </svg>
      <div style={{ lineHeight: 1.1 }}>
        <Text fw={800} fz={large ? 22 : 19} c={inverted ? 'white' : 'var(--app-ink-carbon)'} style={{ letterSpacing: '-0.01em' }}>
          I-ITSM
        </Text>
        <Text
          fz={10}
          fw={600}
          ff='"Archivo Narrow", sans-serif'
          tt="uppercase"
          c={inverted ? 'var(--app-sidebar-muted)' : 'dimmed'}
          style={{ letterSpacing: '0.14em' }}
        >
          Service Management
        </Text>
      </div>
    </Group>
  );
}
