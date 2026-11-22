import react from '@vitejs/plugin-react'
import { defineConfig, type ProxyOptions } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

/**
 * In development the browser only ever talks to this dev server, which forwards /api and /ws to Spring Boot: to the
 * browser it is one origin. The browser's Origin header is dropped on the way, so the backend does not apply its
 * CORS / WebSocket origin check to these proxied calls. Otherwise opening the app as http://127.0.0.1:5173 or from
 * another device on the network fails with "Invalid CORS request".
 */
const dropOrigin: ProxyOptions['configure'] = (proxy) => {
  proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
  proxy.on('proxyReqWs', (proxyReq) => proxyReq.removeHeader('origin'))
}

/**
 * Progressive Web App: installable from Chrome/Edge/Android ("Install app") and iOS Safari ("Add to Home Screen"),
 * opens in its own window, and starts instantly / offline because the app shell is precached by a Workbox service
 * worker. API data is never cached: it is per-user and permission-checked, so it always comes from the server.
 */
const pwa = VitePWA({
  registerType: 'prompt', // a new version is offered to the user ("Reload") instead of swapping code mid-task
  includeAssets: ['favicon.ico', 'favicon.svg', 'apple-touch-icon-180x180.png'],
  manifest: {
    id: '/',
    name: 'I-ITSM: IT Service Management',
    short_name: 'I-ITSM',
    description: 'Report, track and resolve IT incidents',
    lang: 'en',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    display_override: ['window-controls-overlay', 'standalone'],
    orientation: 'any',
    theme_color: '#4263eb',
    background_color: '#ffffff',
    categories: ['business', 'productivity', 'utilities'],
    icons: [
      { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
      { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
      { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
      { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
    // Long-press the installed icon to jump straight to these pages.
    shortcuts: [
      { name: 'Report an incident', short_name: 'Report', url: '/incidents/new', icons: [{ src: 'pwa-192x192.png', sizes: '192x192' }] },
      { name: 'Incidents', url: '/incidents', icons: [{ src: 'pwa-192x192.png', sizes: '192x192' }] },
      { name: 'Notifications', url: '/notifications', icons: [{ src: 'pwa-192x192.png', sizes: '192x192' }] },
    ],
  },
  workbox: {
    globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
    maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
    // Client-side routes (/incidents/42) load the cached app shell; backend paths must always reach the server.
    navigateFallback: '/index.html',
    navigateFallbackDenylist: [/^\/api\//, /^\/ws/, /^\/actuator/],
    cleanupOutdatedCaches: true,
    runtimeCaching: [
      {
        // IBM Plex from Google Fonts: cache so the installed app keeps its typeface offline.
        urlPattern: /^https:\/\/fonts\.(googleapis|gstatic)\.com\/.*/i,
        handler: 'CacheFirst',
        options: {
          cacheName: 'google-fonts',
          expiration: { maxEntries: 20, maxAgeSeconds: 60 * 60 * 24 * 365 },
          cacheableResponse: { statuses: [0, 200] },
        },
      },
    ],
  },
})

export default defineConfig({
  plugins: [react(), pwa],
  // Listen on the network too, so phones on the same Wi-Fi can open http://<laptop-ip>:5173 / :4173
  preview: { host: true },
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', configure: dropOrigin },
      // Real-time updates (STOMP over WebSocket)
      '/ws': { target: 'ws://localhost:8080', ws: true, configure: dropOrigin },
    },
  },
})
