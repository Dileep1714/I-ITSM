import {
  ActionIcon,
  AppShell,
  Avatar,
  Badge,
  Burger,
  Group,
  Menu,
  NavLink,
  ScrollArea,
  Text,
  Tooltip,
  UnstyledButton,
  useComputedColorScheme,
  useMantineColorScheme,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconAlertTriangle,
  IconBell,
  IconCategory,
  IconChartBar,
  IconChevronDown,
  IconClockCog,
  IconHistory,
  IconLayoutDashboard,
  IconLogout,
  IconMail,
  IconMoon,
  IconPlus,
  IconSettings,
  IconSun,
  IconTicket,
  IconUser,
  IconUsers,
} from '@tabler/icons-react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import type { Role } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { humanize } from '../lib/format';
import { useLiveStatus } from '../realtime/liveStatus';
import classes from './AppLayout.module.css';
import { BrandMark } from './BrandMark';
import { NotificationBell } from './NotificationBell';

interface NavItem {
  label: string;
  to: string;
  icon: typeof IconTicket;
  roles: Role[];
}

interface NavSection {
  title: string;
  items: NavItem[];
}

const ALL: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR'];
const STAFF: Role[] = ['TECHNICIAN', 'ADMINISTRATOR'];
const ADMIN: Role[] = ['ADMINISTRATOR'];

const NAV: NavSection[] = [
  {
    title: 'Workspace',
    items: [
      { label: 'Dashboard', to: '/', icon: IconLayoutDashboard, roles: ALL },
      { label: 'Report incident', to: '/incidents/new', icon: IconPlus, roles: ['EMPLOYEE'] },
      { label: 'Incidents', to: '/incidents', icon: IconTicket, roles: ALL },
      { label: 'Notifications', to: '/notifications', icon: IconBell, roles: ALL },
      { label: 'Reports', to: '/reports', icon: IconChartBar, roles: STAFF },
    ],
  },
  {
    title: 'Monitoring',
    items: [
      { label: 'SLA violations', to: '/admin/sla-violations', icon: IconAlertTriangle, roles: ADMIN },
      { label: 'Audit log', to: '/admin/audit', icon: IconHistory, roles: ADMIN },
      { label: 'Outbox', to: '/admin/outbox', icon: IconMail, roles: ADMIN },
    ],
  },
  {
    title: 'Administration',
    items: [
      { label: 'Users', to: '/admin/users', icon: IconUsers, roles: ADMIN },
      { label: 'Catalogue', to: '/admin/catalog', icon: IconCategory, roles: ADMIN },
      { label: 'SLA policies', to: '/admin/sla-policies', icon: IconClockCog, roles: ADMIN },
      { label: 'Settings', to: '/admin/settings', icon: IconSettings, roles: ADMIN },
    ],
  },
];

const ROLE_LABEL: Record<Role, string> = { EMPLOYEE: 'Employee', TECHNICIAN: 'Technician', ADMINISTRATOR: 'Administrator' };

function initials(name: string) {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  const { user, logout } = useAuth();
  const location = useLocation();

  if (!user) return null;
  const sections = NAV.map((section) => ({
    ...section,
    items: section.items.filter((item) => item.roles.includes(user.role)),
  })).filter((section) => section.items.length > 0);

  const isActive = (to: string) =>
    to === '/'
      ? location.pathname === '/'
      : to === '/incidents'
        ? location.pathname === '/incidents' || /^\/incidents\/\d+/.test(location.pathname)
        : location.pathname.startsWith(to);

  return (
    <AppShell
      layout="alt"
      header={{ height: 56 }}
      navbar={{ width: 248, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="lg"
    >
      <AppShell.Header className={classes.header}>
        <Group h="100%" px="lg" justify="space-between" wrap="nowrap">
          <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label="Toggle navigation" />
          <span />
          <Group gap={6} wrap="nowrap">
            <LiveIndicator />
            <ColorSchemeToggle />
            <NotificationBell />
            <Menu position="bottom-end" width={220} withinPortal>
              <Menu.Target>
                <UnstyledButton className={classes.userButton} aria-label="Account menu">
                  <Group gap={10} wrap="nowrap">
                    <Avatar radius="sm" size={30} color="gray" variant="light">
                      {initials(user.fullName)}
                    </Avatar>
                    <div style={{ lineHeight: 1.15 }}>
                      <Text size="sm" fw={500} visibleFrom="xs">
                        {user.fullName}
                      </Text>
                      <Text size="xs" c="dimmed" visibleFrom="xs">
                        {ROLE_LABEL[user.role]}
                      </Text>
                    </div>
                    <IconChevronDown size={14} color="var(--mantine-color-dimmed)" />
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>{user.email}</Menu.Label>
                <Menu.Item component={Link} to="/profile" leftSection={<IconUser size={14} />}>
                  My profile
                </Menu.Item>
                <Menu.Divider />
                <Menu.Item leftSection={<IconLogout size={14} />} color="red" onClick={logout}>
                  Sign out
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar className={classes.navbar}>
        <AppShell.Section className={classes.brand}>
          <Group justify="space-between" wrap="nowrap">
            <BrandMark inverted />
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" color="white" aria-label="Close navigation" />
          </Group>
        </AppShell.Section>
        <AppShell.Section grow component={ScrollArea}>
          {sections.map((section) => (
            <div key={section.title}>
              <div className={classes.section}>{section.title}</div>
              {section.items.map((item) => (
                <NavLink
                  key={item.to}
                  component={Link}
                  to={item.to}
                  label={item.label}
                  leftSection={<item.icon size={17} stroke={1.6} className={classes.linkIcon} />}
                  active={isActive(item.to)}
                  onClick={close}
                  className={classes.link}
                />
              ))}
            </div>
          ))}
        </AppShell.Section>
        <AppShell.Section className={classes.footer}>Signed in as {humanize(user.role).toLowerCase()}</AppShell.Section>
      </AppShell.Navbar>

      <AppShell.Main className={classes.main}>
        <div className={classes.content}>
          <Outlet />
        </div>
      </AppShell.Main>
    </AppShell>
  );
}

/** Light / dark switch. Mantine remembers the choice in the browser. */
function ColorSchemeToggle() {
  const { setColorScheme } = useMantineColorScheme();
  const computed = useComputedColorScheme('light', { getInitialValueInEffect: true });
  const dark = computed === 'dark';
  return (
    <Tooltip label={dark ? 'Light theme' : 'Dark theme'}>
      <ActionIcon
        variant="subtle"
        color="gray"
        size="lg"
        aria-label="Toggle colour theme"
        onClick={() => setColorScheme(dark ? 'light' : 'dark')}
      >
        {dark ? <IconSun size={18} stroke={1.6} /> : <IconMoon size={18} stroke={1.6} />}
      </ActionIcon>
    </Tooltip>
  );
}

/** Green when real-time updates are flowing; the app still works (with manual refresh) when they are not. */
function LiveIndicator() {
  const status = useLiveStatus();
  const label = status === 'live' ? 'Live' : status === 'connecting' ? 'Connecting' : 'Offline';
  return (
    <Tooltip label={status === 'live' ? 'Real-time updates on' : 'Real-time updates unavailable: data refreshes when you navigate'}>
      <Badge variant="dot" color={status === 'live' ? 'green' : status === 'connecting' ? 'yellow' : 'gray'} visibleFrom="xs" mr={4}>
        {label}
      </Badge>
    </Tooltip>
  );
}
