import { useQuery } from '@tanstack/react-query';
import { Tabs } from 'expo-router';
import { NativeTabs } from 'expo-router/native-tabs';
import { Platform } from 'react-native';
import { api } from '@/api/client';
import type { Role } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { FloatingTabBar, type FloatingTab } from '@/ui/FloatingTabBar';
import { useColors } from '@/ui/theme';

interface TabSpec extends FloatingTab {
  /** SF Symbol (iOS). */
  sf: string | { default: string; selected: string };
  roles: Role[];
}

const ALL: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR'];

/** One list of destinations per role, rendered natively on each platform. */
function tabsFor(role: Role | undefined, unread: number | undefined): TabSpec[] {
  const admin = role === 'ADMINISTRATOR';
  const badge = unread ? (unread > 99 ? '99+' : String(unread)) : undefined;
  return [
    {
      name: '(home)',
      label: admin ? 'Overview' : 'Home',
      sf: admin ? 'square.grid.2x2' : { default: 'house', selected: 'house.fill' },
      icon: admin ? { default: 'view-dashboard-outline', selected: 'view-dashboard' } : { default: 'home-outline', selected: 'home' },
      roles: ALL,
    },
    {
      name: 'incidents',
      label: role === 'TECHNICIAN' ? 'Queue' : role === 'EMPLOYEE' ? 'My incidents' : 'Incidents',
      sf: 'list.bullet.rectangle',
      icon: { default: 'clipboard-text-outline', selected: 'clipboard-text' },
      roles: ALL,
    },
    {
      name: 'team',
      label: 'Team',
      sf: { default: 'person.3', selected: 'person.3.fill' },
      icon: { default: 'account-group-outline', selected: 'account-group' },
      roles: ['ADMINISTRATOR'],
    },
    {
      name: 'notifications',
      label: 'Alerts',
      sf: { default: 'bell', selected: 'bell.fill' },
      icon: { default: 'bell-outline', selected: 'bell' },
      badge,
      roles: ALL,
    },
    {
      name: 'profile',
      label: 'Profile',
      sf: { default: 'person.crop.circle', selected: 'person.crop.circle.fill' },
      icon: { default: 'account-circle-outline', selected: 'account-circle' },
      roles: ALL,
    },
  ];
}

/**
 * Navigation looks the same on both platforms (a floating capsule bar whose selection slides) while each stays
 * native:
 *   iOS:     the system tab bar (UITabBar). On iOS 26 that is the Liquid Glass bar, which also shrinks while you
 *            scroll down and returns when you scroll up.
 *   Android: a Material 3 floating navigation bar (FloatingTabBar) with a sliding indicator.
 * Each role gets its own set:
 *   Employee:      Home · My incidents · Alerts · Profile         (report via the floating button)
 *   Technician:    Home · Queue · Alerts · Profile
 *   Administrator: Overview · Incidents · Team · Alerts · Profile
 * The set is fixed while signed in (signing out unmounts the tabs).
 */
export default function TabLayout() {
  const c = useColors();
  const { user } = useAuth();
  const role = user?.role;
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: api.notifications.unreadCount,
    refetchInterval: 60_000,
    select: (r) => r.unread,
  });
  const tabs = tabsFor(role, unread.data);
  const visible = tabs.filter((t) => role && t.roles.includes(role));

  if (Platform.OS === 'ios') {
    return (
      <NativeTabs minimizeBehavior="onScrollDown" tintColor={c.primary} iconColor={c.muted} badgeBackgroundColor={c.primary}>
        {tabs.map((t) => (
          <NativeTabs.Trigger key={t.name} name={t.name} hidden={!visible.includes(t)}>
            <NativeTabs.Trigger.Label>{t.label}</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon sf={t.sf as never} />
            {t.badge ? <NativeTabs.Trigger.Badge>{t.badge}</NativeTabs.Trigger.Badge> : null}
          </NativeTabs.Trigger>
        ))}
      </NativeTabs>
    );
  }

  return (
    <Tabs
      screenOptions={{ headerShown: false, sceneStyle: { backgroundColor: c.bg } }}
      tabBar={(props) => <FloatingTabBar {...props} tabs={visible} />}
    >
      {tabs.map((t) => (
        <Tabs.Screen key={t.name} name={t.name} options={visible.includes(t) ? { title: t.label } : { href: null }} />
      ))}
    </Tabs>
  );
}
