import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server forwards /api calls to the Spring Boot backend, so the browser sees one origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
