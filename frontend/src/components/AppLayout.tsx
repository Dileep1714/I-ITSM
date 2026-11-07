import { AppShell, Badge, Burger, Group, Menu, NavLink, Text, ThemeIcon, UnstyledButton, Avatar } from '@mantine/core';
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
} from '@tabler/icons-react';
import { Link, Outlet, useLocation } from 'react-router-dom';
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
  { label: 'SLA violations', to: '/admin/sla-violations', icon: IconAlertTriangle, roles: ['ADMINISTRATOR'] },
  { label: 'Audit log', to: '/admin/audit', icon: IconHistory, roles: ['ADMINISTRATOR'] },
  { label: 'Outbox', to: '/admin/outbox', icon: IconMail, roles: ['ADMINISTRATOR'] },
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
