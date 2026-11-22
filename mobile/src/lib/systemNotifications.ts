import { router } from 'expo-router';
import { Platform } from 'react-native';

/**
 * Notifications in the phone's notification shade, for live incident events that arrive while the app is in
 * the background. Local notifications only (no push server needed), which also work in Expo Go.
 *
 * expo-notifications is loaded lazily and every call is guarded: if the module is unavailable the app keeps
 * working with in-app banners only.
 */
type NotificationsModule = typeof import('expo-notifications');
let module: NotificationsModule | null = null;
let ready = false;

function load(): NotificationsModule | null {
  if (module) return module;
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    module = require('expo-notifications') as NotificationsModule;
  } catch {
    module = null;
  }
  return module;
}

/** Ask for permission (Android 13+ / iOS), create the channel, and open the incident when a notification is tapped. */
export async function initSystemNotifications() {
  const N = load();
  if (!N || ready) return;
  try {
    N.setNotificationHandler({
      // In the foreground the app shows its own banner instead.
      handleNotification: async () => ({ shouldShowBanner: false, shouldShowList: true, shouldPlaySound: false, shouldSetBadge: false }),
    });
    if (Platform.OS === 'android') {
      await N.setNotificationChannelAsync('incidents', {
        name: 'Incident updates',
        importance: N.AndroidImportance.HIGH,
        vibrationPattern: [0, 200, 120, 200],
        lightColor: '#4263eb',
      });
    }
    const current = await N.getPermissionsAsync();
    if (!current.granted && current.canAskAgain) await N.requestPermissionsAsync();
    N.addNotificationResponseReceivedListener((response) => {
      const incidentId = response.notification.request.content.data?.incidentId;
      if (typeof incidentId === 'number') router.push(`/incident/${incidentId}`);
    });
    ready = true;
  } catch {
    // unavailable on this build: in-app banners only
  }
}

export async function showSystemNotification(title: string, body: string | undefined, incidentId: number | null) {
  const N = load();
  if (!N || !ready) return;
  try {
    await N.scheduleNotificationAsync({
      content: { title, body: body ?? '', data: incidentId ? { incidentId } : {}, ...(Platform.OS === 'android' ? { channelId: 'incidents' } : {}) },
      trigger: null,
    });
  } catch {
    // ignore: the event is still in the in-app notification list
  }
}
