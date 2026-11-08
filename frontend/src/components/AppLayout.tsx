import { AppShell, Badge, Burger, Group, Menu, NavLink, Text, ThemeIcon, Tooltip, UnstyledButton, Avatar } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconAlertTriangle,
  IconChevronDown,
  IconHistory,
  IconLayoutDashboard,
  IconLogout,
  IconMail,
  IconPlus,
  IconSettings,
  IconTicket,
  IconBell,
  IconCategory,
  IconChartBar,
  IconClockCog,
  IconUser,
  IconUsers,
} from '@tabler/icons-react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useLiveStatus } from '../realtime/liveStatus';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../api/types';
import { humanize } from '../lib/format';
import { NotificationBell } from './NotificationBell';

interface NavItem {
  label: string;
  to: string;
  icon: typeof IconTicket;
  roles: Role[];
}

const NAV: NavItem[] = [
  { label: 'Dashboard', to: '/', icon: IconLayoutDashboard, roles: ['EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR'] },
  { label: 'Report incident', to: '/incidents/new', icon: IconPlus, roles: ['EMPLOYEE'] },
  { label: 'Incidents', to: '/incidents', icon: IconTicket, roles: ['EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR'] },
  { label: 'Notifications', to: '/notifications', icon: IconBell, roles: ['EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR'] },
  { label: 'Reports', to: '/reports', icon: IconChartBar, roles: ['TECHNICIAN', 'ADMINISTRATOR'] },
  { label: 'My profile', to: '/profile', icon: IconUser, roles: ['TECHNICIAN'] },
  { label: 'SLA violations', to: '/admin/sla-violations', icon: IconAlertTriangle, roles: ['ADMINISTRATOR'] },
  { label: 'Audit log', to: '/admin/audit', icon: IconHistory, roles: ['ADMINISTRATOR'] },
  { label: 'Outbox', to: '/admin/outbox', icon: IconMail, roles: ['ADMINISTRATOR'] },
  { label: 'Users', to: '/admin/users', icon: IconUsers, roles: ['ADMINISTRATOR'] },
  { label: 'Catalogue', to: '/admin/catalog', icon: IconCategory, roles: ['ADMINISTRATOR'] },
  { label: 'SLA policies', to: '/admin/sla-policies', icon: IconClockCog, roles: ['ADMINISTRATOR'] },
  { label: 'Settings', to: '/admin/settings', icon: IconSettings, roles: ['ADMINISTRATOR'] },
];

const ROLE_COLOR: Record<Role, string> = { EMPLOYEE: 'blue', TECHNICIAN: 'teal', ADMINISTRATOR: 'grape' };

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  const { user, logout } = useAuth();
  const location = useLocation();

  if (!user) return null;
  const items = NAV.filter((item) => item.roles.includes(user.role));
  const isActive = (to: string) =>
    to === '/' ? location.pathname === '/' : to === '/incidents'
      ? location.pathname === '/incidents' || /^\/incidents\/\d+/.test(location.pathname)
      : location.pathname.startsWith(to);

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <ThemeIcon variant="gradient" gradient={{ from: 'indigo', to: 'cyan' }} size="lg" radius="md">
              <IconTicket size={20} />
            </ThemeIcon>
            <Text fw={700} size="lg">
              I-ITSM
            </Text>
          </Group>
          <Group gap="sm">
            <LiveIndicator />
            <NotificationBell />
            <Menu position="bottom-end" withinPortal>
              <Menu.Target>
                <UnstyledButton>
                  <Group gap={8}>
                    <Avatar radius="xl" size="sm" color={ROLE_COLOR[user.role]}>
                      {user.fullName.charAt(0)}
                    </Avatar>
                    <Text size="sm" fw={500} visibleFrom="xs">
                      {user.fullName}
                    </Text>
                    <IconChevronDown size={14} />
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>{user.email}</Menu.Label>
                <Menu.Item disabled>
                  <Badge color={ROLE_COLOR[user.role]} variant="light">
                    {humanize(user.role)}
                  </Badge>
                </Menu.Item>
                <Menu.Divider />
                <Menu.Item component={Link} to="/profile" leftSection={<IconUser size={14} />}>
                  My profile
                </Menu.Item>
                <Menu.Item leftSection={<IconLogout size={14} />} color="red" onClick={logout}>
                  Log out
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm">
        {items.map((item) => (
          <NavLink
            key={item.to}
            component={Link}
            to={item.to}
            label={item.label}
            leftSection={<item.icon size={18} />}
            active={isActive(item.to)}
            onClick={close}
            style={{ borderRadius: 'var(--mantine-radius-md)' }}
          />
        ))}
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}

/** Green when real-time updates are flowing; the app still works (with manual refresh) when they are not. */
function LiveIndicator() {
  const status = useLiveStatus();
  const label = status === 'live' ? 'Live' : status === 'connecting' ? 'Connecting…' : 'Offline';
  return (
    <Tooltip label={status === 'live' ? 'Real-time updates on' : 'Real-time updates unavailable: data refreshes when you navigate'}>
      <Badge
        variant="dot"
        color={status === 'live' ? 'green' : status === 'connecting' ? 'yellow' : 'gray'}
        visibleFrom="xs"
      >
        {label}
      </Badge>
    </Tooltip>
  );
}
