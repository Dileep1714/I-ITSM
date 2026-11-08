import react from '@vitejs/plugin-react'
import { defineConfig, type ProxyOptions } from 'vite'

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

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', configure: dropOrigin },
      // Real-time updates (STOMP over WebSocket)
      '/ws': { target: 'ws://localhost:8080', ws: true, configure: dropOrigin },
    },
  },
})
