import { ActionIcon, Affix, Button, Group, List, Modal, Notification, Text, Tooltip } from '@mantine/core';
import { useDisclosure, useNetwork } from '@mantine/hooks';
import { IconDownload, IconRefresh, IconShare2, IconSquarePlus, IconWifiOff } from '@tabler/icons-react';
import { useRegisterSW } from 'virtual:pwa-register/react';
import { useInstallPrompt } from './installPrompt';

/** Header button: installs the app (Chrome/Edge/Android) or explains Add to Home Screen (iOS). Hidden otherwise. */
export function InstallButton() {
  const { canInstall, needsIosInstructions, install } = useInstallPrompt();
  const [opened, { open, close }] = useDisclosure(false);
  if (!canInstall && !needsIosInstructions) return null;

  return (
    <>
      <Tooltip label="Install I-ITSM as an app">
        <ActionIcon
          variant="subtle"
          color="gray"
          size="lg"
          aria-label="Install app"
          onClick={() => (canInstall ? void install() : open())}
        >
          <IconDownload size={18} stroke={1.6} />
        </ActionIcon>
      </Tooltip>
      <Modal opened={opened} onClose={close} title="Install I-ITSM on your iPhone or iPad" centered>
        <List type="ordered" spacing="sm">
          <List.Item icon={<IconShare2 size={16} />}>
            Tap the <b>Share</b> button in Safari's toolbar.
          </List.Item>
          <List.Item icon={<IconSquarePlus size={16} />}>
            Choose <b>Add to Home Screen</b>, then <b>Add</b>.
          </List.Item>
        </List>
        <Text size="sm" c="dimmed" mt="md">
          I-ITSM then opens full-screen from its own icon, like a native app.
        </Text>
      </Modal>
    </>
  );
}

/**
 * Registers the service worker and offers a new version when one is deployed. The user chooses when to reload,
 * so a half-filled incident form is never lost to an automatic update.
 */
export function UpdatePrompt() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_url, registration) {
      // Long-lived installed apps: look for a new version every hour.
      if (registration) setInterval(() => void registration.update(), 60 * 60 * 1000);
    },
  });

  if (!needRefresh) return null;
  return (
    <Affix position={{ bottom: 20, right: 20 }} zIndex={400}>
      <Notification
        icon={<IconRefresh size={18} />}
        title="A new version of I-ITSM is available"
        onClose={() => setNeedRefresh(false)}
        withBorder
        maw={360}
      >
        <Group justify="flex-end" mt="xs">
          <Button size="xs" onClick={() => void updateServiceWorker(true)}>
            Reload
          </Button>
        </Group>
      </Notification>
    </Affix>
  );
}

/** Thin banner while the device has no connection: the app shell still runs, but data needs the server. */
export function OfflineBanner() {
  const { online } = useNetwork();
  if (online) return null;
  return (
    <Group
      gap={8}
      justify="center"
      py={6}
      px="md"
      role="status"
      style={{ background: 'var(--mantine-color-yellow-light)', borderBottom: '1px solid var(--mantine-color-yellow-outline)' }}
    >
      <IconWifiOff size={16} />
      <Text size="sm">You are offline. Changes can't be saved until the connection returns.</Text>
    </Group>
  );
}
