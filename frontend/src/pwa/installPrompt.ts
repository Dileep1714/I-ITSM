import { useSyncExternalStore } from 'react';

/** Chrome/Edge/Android event that lets the page show its own "Install app" button. Not in the DOM typings yet. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

// The browser fires `beforeinstallprompt` once, possibly before React has rendered, so it is captured at module
// load (this file is imported from main.tsx) and kept here until the user clicks "Install".
let deferred: BeforeInstallPromptEvent | null = null;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault(); // keep the browser's mini-infobar away; we offer our own button
  deferred = event as BeforeInstallPromptEvent;
  emit();
});
window.addEventListener('appinstalled', () => {
  deferred = null;
  emit();
});

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

/** True when already running as an installed app (own window / home-screen icon). */
export const isStandalone = () =>
  window.matchMedia('(display-mode: standalone)').matches ||
  (navigator as Navigator & { standalone?: boolean }).standalone === true;

/** iPhone/iPad Safari has no install event: users add the app from the Share menu instead. */
export const isIos = () =>
  /iphone|ipad|ipod/i.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

export function useInstallPrompt() {
  const event = useSyncExternalStore(subscribe, () => deferred);
  return {
    /** The browser is ready to install the app with one click. */
    canInstall: event !== null,
    /** iOS Safari, not yet installed: show "Share → Add to Home Screen" instructions. */
    needsIosInstructions: event === null && isIos() && !isStandalone(),
    install: async () => {
      if (!deferred) return;
      await deferred.prompt();
      await deferred.userChoice;
      deferred = null;
      emit();
    },
  };
}
