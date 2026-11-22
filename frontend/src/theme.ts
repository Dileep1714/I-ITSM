import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  createTheme,
  type MantineColorsTuple,
  Modal,
  NavLink,
  Notification,
  Paper,
  SegmentedControl,
  Table,
  Tabs,
  Tooltip,
} from '@mantine/core';

/**
 * The application's visual system: the service docket.
 *
 * Every incident is a numbered docket from a carbon triplicate job book. Ink is carbon blue, the page ground is the
 * reader's copy (white for employees, pink for technicians, canary for administrators; see app.css), rules and
 * field boxes are one pixel, corners are square, and nothing casts a shadow. Stamp red is reserved for P1 and
 * breaches. Archivo sets the interface, Archivo Narrow the tracked form labels, Courier Prime the typed figures
 * (docket numbers, times).
 */
const carbon: MantineColorsTuple = [
  '#eeeefa',
  '#d9daf1',
  '#b3b5e3',
  '#8b8fd3',
  '#6a6ec4',
  '#5257b8',
  '#3c41a3',
  '#262a7a',
  '#1f2263',
  '#171a4c',
];

const stamp: MantineColorsTuple = [
  '#fdeaec',
  '#f9ccd2',
  '#f19aa6',
  '#e8687a',
  '#df3f55',
  '#d6233c',
  '#c8102e',
  '#a60d26',
  '#850a1e',
  '#640717',
];

export const LABEL_FONT = '"Archivo Narrow", "Archivo", system-ui, sans-serif';

const labelStyle = {
  fontFamily: LABEL_FONT,
  fontWeight: 600,
  textTransform: 'uppercase' as const,
  letterSpacing: '0.1em',
};

export const theme = createTheme({
  // Success and warning hues from Mantine defaults (green toasts, teal/orange buttons) resolve to carbon, so
  // carbon, stamp red and the role accent stay the only inks.
  colors: { carbon, stamp, green: carbon, teal: carbon, orange: carbon },
  primaryColor: 'carbon',
  primaryShade: { light: 7, dark: 5 },
  defaultRadius: 0,
  radius: { xs: '0', sm: '0', md: '0', lg: '0', xl: '0' },
  cursorType: 'pointer',

  fontFamily: '"Archivo", system-ui, -apple-system, "Segoe UI", sans-serif',
  fontFamilyMonospace: '"Courier Prime", "Courier New", ui-monospace, monospace',
  fontSizes: { xs: '0.75rem', sm: '0.875rem', md: '0.9375rem', lg: '1.0625rem', xl: '1.1875rem' },
  lineHeights: { xs: '1.4', sm: '1.45', md: '1.5', lg: '1.5', xl: '1.5' },
  headings: {
    fontFamily: '"Archivo", system-ui, sans-serif',
    fontWeight: '800',
    sizes: {
      h1: { fontSize: '2rem', lineHeight: '1.15' },
      h2: { fontSize: '1.5rem', lineHeight: '1.25' },
      h3: { fontSize: '1.1875rem', lineHeight: '1.3' },
      h4: { fontSize: '1.0625rem', lineHeight: '1.35' },
    },
  },
  // Flat paper: no shadows anywhere. Depth comes from the copy tint against white sheets.
  shadows: { xs: 'none', sm: 'none', md: 'none', lg: 'none', xl: 'none' },

  components: {
    Button: Button.extend({ defaultProps: { size: 'sm' }, styles: { root: { fontWeight: 600 } } }),
    Badge: Badge.extend({
      defaultProps: { radius: 0, variant: 'outline' },
      styles: { root: { ...labelStyle, fontSize: '0.6875rem' } },
    }),
    Paper: Paper.extend({ defaultProps: { radius: 0 } }),
    Card: Card.extend({ defaultProps: { radius: 0, padding: 'lg' } }),
    Modal: Modal.extend({
      defaultProps: { radius: 0, centered: true, overlayProps: { backgroundOpacity: 0.45, color: '#171a4c' } },
      styles: { title: { fontWeight: 800, fontSize: '1.1875rem' } },
    }),
    Notification: Notification.extend({ defaultProps: { radius: 0 } }),
    Alert: Alert.extend({ defaultProps: { variant: 'light', radius: 0 } }),
    Anchor: Anchor.extend({ defaultProps: { underline: 'hover' } }),
    Tooltip: Tooltip.extend({ defaultProps: { withArrow: true, openDelay: 200, radius: 0, color: 'carbon.9' } }),
    Tabs: Tabs.extend({ styles: { tab: { ...labelStyle, fontSize: '0.75rem' } } }),
    SegmentedControl: SegmentedControl.extend({ defaultProps: { size: 'sm', radius: 0 } }),
    NavLink: NavLink.extend({ styles: { label: { fontWeight: 500 } } }),
    Table: Table.extend({
      defaultProps: { verticalSpacing: 'sm', horizontalSpacing: 'md', highlightOnHover: true },
      styles: {
        th: { ...labelStyle, fontSize: '0.6875rem', color: 'var(--app-ink-carbon)', whiteSpace: 'nowrap' },
      },
    }),
  },
});
