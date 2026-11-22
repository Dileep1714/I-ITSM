import Constants from 'expo-constants';

/**
 * Where the I-ITSM server is.
 *
 * - EXPO_PUBLIC_API_URL (in mobile/.env) wins: set it to the deployed server, e.g. https://iitsm.example.com
 * - Otherwise, in development, the phone talks to the laptop that serves this app (the same IP Expo Go connected
 *   to), port 5173: the web frontend's dev server, which forwards /api and /ws to Spring Boot. Going through it means
 *   no extra firewall rule for Java and no CORS/WebSocket-origin changes on the backend.
 */
function developmentServer(): string {
  const host = Constants.expoConfig?.hostUri?.split(':')[0] ?? 'localhost';
  return `http://${host}:5173`;
}

export const SERVER_URL = (process.env.EXPO_PUBLIC_API_URL || developmentServer()).replace(/\/+$/, '');
export const API_URL = `${SERVER_URL}/api`;
export const WS_URL = `${SERVER_URL.replace(/^http/, 'ws')}/ws`;
