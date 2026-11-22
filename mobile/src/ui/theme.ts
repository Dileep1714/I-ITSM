import { createContext, createElement, useContext, type ReactNode } from 'react';
import { useColorScheme } from 'react-native';
import type { IncidentStatus, PriorityLevel, Role, SlaStatus } from '../api/types';

/**
 * The Service Docket world, translated to the platforms. Same inks as the web app (see DESIGN.md):
 * carbon blue drives every interactive element (the platform's one tint colour), stamp red is reserved for P1 and
 * breaches, each role has one muted accent, and green appears only on the Live connection chip. Native controls
 * keep their platform shapes (Material 3 on Android, UIKit on iOS); the world shows through colour, the typed
 * figures (Courier Prime) and the stamp marks.
 */
const light = {
  bg: '#eef2f1',
  surface: '#ffffff',
  surfaceAlt: '#ecedf3',
  border: '#d3d4e2',
  divider: '#e3e4ee',
  text: '#1d1d22',
  muted: '#4d4f74',
  primary: '#262a7a',
  onPrimary: '#ffffff',
  primaryContainer: '#e1e2f5',
  onPrimaryContainer: '#171a4c',
  danger: '#c8102e',
  success: '#262a7a',
  warningBg: '#f6eef0',
  warningBorder: '#c8102e',
  internalBg: '#eceaf6',
  scrim: 'rgba(23,26,76,0.45)',
  /** The signed-in role's accent (set from the role below). */
  accent: '#0d6463',
  onAccent: '#ffffff',
  /** Live connection chip only. */
  live: '#157a3e',
  liveTint: '#e3f3e8',
  connecting: '#8a5a00',
  connectingTint: '#fbf1dc',
};

const dark: typeof light = {
  bg: '#16191b',
  surface: '#1d1e26',
  surfaceAlt: '#272936',
  border: '#363949',
  divider: '#2a2c38',
  text: '#ecebf4',
  muted: '#a8a9c4',
  primary: '#b3b6f0',
  onPrimary: '#171a4c',
  primaryContainer: '#2f3480',
  onPrimaryContainer: '#e1e2f5',
  danger: '#ff6b7d',
  success: '#b3b6f0',
  warningBg: '#2c1d22',
  warningBorder: '#ff6b7d',
  internalBg: '#24233a',
  scrim: 'rgba(0,0,0,0.6)',
  accent: '#5fc2bf',
  onAccent: '#121316',
  live: '#4fd38a',
  liveTint: 'rgba(79,211,138,0.14)',
  connecting: '#f0b955',
  connectingTint: 'rgba(240,185,85,0.14)',
};

/** Per role: page ground (a faint hint of the accent) and the accent itself, light and dark. */
const ROLE: Record<Role, { light: [string, string]; dark: [string, string] }> = {
  EMPLOYEE: { light: ['#eef2f1', '#0d6463'], dark: ['#16191b', '#5fc2bf'] },
  TECHNICIAN: { light: ['#f2eff1', '#6a2c5b'], dark: ['#1a171a', '#d995c6'] },
  ADMINISTRATOR: { light: ['#f3f0ea', '#85561a'], dark: ['#1b1916', '#d6a65e'] },
};

export type Palette = typeof light;

const RoleContext = createContext<Role | null>(null);

/** Supplies the signed-in role to useColors (mounted inside the auth provider). */
export function RoleProvider({ role, children }: { role: Role | null; children: ReactNode }) {
  return createElement(RoleContext.Provider, { value: role }, children);
}

export function useColors(): Palette {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const role = useContext(RoleContext);
  const base = scheme === 'dark' ? dark : light;
  if (!role) return base;
  const [bg, accent] = ROLE[role][scheme];
  return { ...base, bg, accent };
}

/** The accent of a given role (for showing a role that is not necessarily the viewer's). */
export function useRoleAccent(role: Role): string {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  return ROLE[role][scheme][1];
}

/**
 * Fixed-tone inks for icons and marks that are not theme-aware. Mid tones that hold contrast on both the light and
 * dark surfaces. The old rainbow names are kept so every screen maps onto the docket palette: blues, violets, teals
 * and greens are carbon; reds and oranges are stamp red; greys stay grey.
 */
const CARBON = '#5a5fc4';
const CARBON_DEEP = '#3c41a3';
const STAMP = '#d6233c';
const GREY = '#8a8ca3';

export const hue = {
  blue: CARBON,
  cyan: CARBON,
  indigo: CARBON,
  violet: CARBON_DEEP,
  grape: CARBON_DEEP,
  yellow: CARBON,
  teal: CARBON,
  green: CARBON,
  gray: GREY,
  dark: GREY,
  red: STAMP,
  orange: STAMP,
} as const;

export const statusHue: Record<IncidentStatus, string> = {
  NEW: CARBON,
  CLASSIFIED: CARBON,
  ASSIGNED: CARBON,
  ACKNOWLEDGED: CARBON_DEEP,
  IN_PROGRESS: CARBON_DEEP,
  ON_HOLD: CARBON,
  RESOLVED: CARBON,
  VERIFIED: GREY,
  CLOSED: GREY,
  CANCELLED: GREY,
};

export const priorityHue: Record<PriorityLevel, string> = { P1: STAMP, P2: CARBON_DEEP, P3: CARBON, P4: GREY };

export const slaHue: Record<SlaStatus, string> = {
  NOT_STARTED: GREY,
  ON_TRACK: CARBON,
  AT_RISK: STAMP,
  PAUSED: CARBON,
  BREACHED: STAMP,
  MET: GREY,
};

/** Typed figures: docket numbers, times, counts. Loaded in the root layout; falls back to the system monospace. */
export const FIGURE = 'CourierPrime_700Bold';
export const FIGURE_REGULAR = 'CourierPrime_400Regular';
