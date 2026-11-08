import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  createTheme,
  Modal,
  NavLink,
  Paper,
  SegmentedControl,
  Table,
  Tabs,
  Tooltip,
} from '@mantine/core';

/**
 * The application's visual system. Every page inherits it, so the look changes here, not page by page.
 *
 * Direction: restrained enterprise software. Neutral typeface, tight corners, hairline borders instead of shadows,
 * sentence-case labels instead of uppercase pills, quiet table headers. The brand colour is still Mantine's indigo
 * (to be replaced by the project palette).
 */
export const theme = createTheme({
  primaryColor: 'indigo',
  primaryShade: { light: 7, dark: 5 },
  defaultRadius: 'sm',
  cursorType: 'pointer',

  fontFamily: '"IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif',
  fontFamilyMonospace: '"IBM Plex Mono", ui-monospace, "Cascadia Mono", Consolas, monospace',
  fontSizes: { xs: '0.75rem', sm: '0.8125rem', md: '0.875rem', lg: '1rem', xl: '1.125rem' },
  lineHeights: { xs: '1.4', sm: '1.45', md: '1.5', lg: '1.5', xl: '1.5' },
  headings: {
    fontFamily: '"IBM Plex Sans", system-ui, sans-serif',
    fontWeight: '600',
    sizes: {
      h1: { fontSize: '1.625rem', lineHeight: '1.25' },
      h2: { fontSize: '1.375rem', lineHeight: '1.3' },
      h3: { fontSize: '1.125rem', lineHeight: '1.35' },
      h4: { fontSize: '1rem', lineHeight: '1.4' },
    },
  },
  shadows: {
    xs: '0 1px 2px rgba(16, 24, 40, 0.04)',
    sm: '0 1px 3px rgba(16, 24, 40, 0.06), 0 1px 2px rgba(16, 24, 40, 0.04)',
    md: '0 4px 12px rgba(16, 24, 40, 0.08)',
  },

  components: {
    Button: Button.extend({ defaultProps: { size: 'sm' }, styles: { root: { fontWeight: 500 } } }),
    Badge: Badge.extend({
      defaultProps: { radius: 'sm', variant: 'light' },
      styles: { root: { textTransform: 'none', fontWeight: 500, letterSpacing: 0 } },
    }),
    Paper: Paper.extend({ defaultProps: { radius: 'md' } }),
    Card: Card.extend({ defaultProps: { radius: 'md', padding: 'lg' } }),
    Modal: Modal.extend({
      defaultProps: { radius: 'md', centered: true, overlayProps: { backgroundOpacity: 0.4, blur: 2 } },
      styles: { title: { fontWeight: 600 } },
    }),
    Alert: Alert.extend({ defaultProps: { variant: 'light', radius: 'sm' } }),
    Anchor: Anchor.extend({ defaultProps: { underline: 'hover' } }),
    Tooltip: Tooltip.extend({ defaultProps: { withArrow: true, openDelay: 200 } }),
    Tabs: Tabs.extend({ styles: { tab: { fontWeight: 500 } } }),
    SegmentedControl: SegmentedControl.extend({ defaultProps: { size: 'sm' } }),
    NavLink: NavLink.extend({ styles: { label: { fontWeight: 500 } } }),
    Table: Table.extend({
      defaultProps: { verticalSpacing: 'sm', horizontalSpacing: 'md', highlightOnHover: true },
      styles: {
        th: {
          fontSize: 'var(--mantine-font-size-xs)',
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
          color: 'var(--mantine-color-dimmed)',
          whiteSpace: 'nowrap',
        },
      },
    }),
  },
});
