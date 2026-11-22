# I-ITSM mobile (React Native + Expo)

Android and iOS app with a different experience per role:

| Role | Tabs | Highlights |
|---|---|---|
| Employee | Home · My incidents · Alerts · Profile | Floating **Report** button, smart category/duplicate hints, photo of the error, confirm fixes with a rating |
| Technician | Home · Queue · Alerts · Profile | Queue by priority, **swipe right** to take / acknowledge / start, resolve, availability |
| Administrator | Overview · Incidents · Team · Alerts · Profile | SLA compliance and response KPIs, unassigned queue, ranked assignment, team workload, SLA violations, activity log |

Native features: the platform's own tab bar and headers (with search), Material 3 design, swipe actions, action
sheets, fingerprint/face unlock, notifications in the system shade, camera photos on incidents, haptics.
User, catalogue and SLA-policy configuration stays on the web app.

Stack: Expo SDK 58 · React Native 0.88 · Expo Router (file-based navigation) · TanStack Query · STOMP over WebSocket
for live updates · JWT in the encrypted keystore (expo-secure-store).

## Run it on your phone (development)

1. Install **Expo Go** from the Play Store / App Store. Phone and laptop on the **same Wi-Fi**.
2. Start the backend (`cd backend && ./mvnw spring-boot:run`) and the web dev server (`cd frontend && npm run dev`).
   The app reaches the backend **through the web dev server on port 5173**, which forwards `/api` and `/ws`.
3. Start the app:
   ```bash
   cd mobile
   npm install        # first time only
   npx expo start
   ```
4. Scan the QR code: Android with Expo Go, iPhone with the Camera app.

The login screen shows which server the app is using. To point it somewhere else (e.g. a deployed backend), create
`mobile/.env` with `EXPO_PUBLIC_API_URL=https://your-server` and restart `npx expo start`.

## Structure

```
src/app/                  screens (Expo Router: each file is a route)
  _layout.tsx             providers + signed-in / signed-out navigation
  login.tsx
  (tabs)/                 Home · Incidents · Report (employees) · Alerts · Profile
  incident/[id].tsx       incident detail and all workflow actions
src/api/                  REST client and DTO types (types.ts is copied from the web app)
src/auth/                 session (token in the device keystore)
src/realtime/             live updates
src/ui/                   theme and component kit
```

## Installable builds (APK / iOS)

`npx eas-cli@latest build --platform android --profile preview` builds an APK in the cloud (free Expo account).
iOS device builds need an Apple Developer account.
